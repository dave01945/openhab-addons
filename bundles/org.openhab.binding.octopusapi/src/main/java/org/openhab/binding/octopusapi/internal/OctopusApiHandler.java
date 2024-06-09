/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.octopusapi.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.measure.quantity.Energy;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.core.library.dimension.EnergyPrice;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.TimeSeries.Policy;

/**
 * The {@link OctopusApiHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author <David Jones> - Initial contribution
 */

@NonNullByDefault
public class OctopusApiHandler extends BaseThingHandler {

    private String baseUrl = "";

    private String apiKey = "";

    private String mpanImport = "";

    private String mpanExport = "";

    private String meterSerial = "";

    private String agileRegion = "";

    private @NonNullByDefault({}) OctopusApiConfiguration config;

    private @NonNullByDefault({}) OctopusApiConnection connection;

    private final HttpClient httpClient;

    private @NonNullByDefault({}) Future scheduledFuture;

    public OctopusApiHandler(Thing thing, HttpClient httpClient) {
        super(thing);
        this.httpClient = httpClient;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            pollTask();
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(OctopusApiConfiguration.class);

        updateStatus(ThingStatus.UNKNOWN);

        if (!config.hostname.startsWith("http://") && !config.hostname.startsWith("https://")) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Hostname is invalid: protocol not defined.");
            return;
        }

        this.baseUrl = config.hostname;
        this.apiKey = config.apiKey;
        this.mpanImport = config.mpanImport;
        this.mpanExport = config.mpanExport;
        this.meterSerial = config.meterSerial;
        this.agileRegion = config.agileRegion;

        connection = new OctopusApiConnection(this, httpClient);

        scheduledFuture = scheduler.scheduleWithFixedDelay(this::pollTask, 0, config.refreshInterval, TimeUnit.HOURS);
    }

    @Override
    public void dispose() {
        scheduledFuture.cancel(true);
    }

    private TimeSeries createConsumptionTimeSeries(String consumption) {

        JSONObject consumptionJson = new JSONObject(consumption);
        JSONArray consumptionArray = new JSONArray(consumptionJson.getJSONArray("results"));
        TimeSeries consumptionSeries = new TimeSeries(Policy.REPLACE);

        consumptionArray.forEach(item -> {
            JSONObject itemJson = (JSONObject) item;
            Instant timestamp = Instant.parse(itemJson.getString("interval_end"));
            QuantityType<Energy> value = QuantityType.valueOf(itemJson.getDouble("consumption"), Units.KILOWATT_HOUR);
            consumptionSeries.add(timestamp, value);
        });

        return consumptionSeries;
    }

    private void updateConsumption(boolean export) {
        Instant now = Instant.now();
        Instant back24 = now.minus(24, ChronoUnit.HOURS);
        String mpan = (export) ? mpanExport : mpanImport;
        String id = (export) ? "export" : "consumption";
        String urlConsumption24 = baseUrl + "/v1/electricity-meter-points/" + mpan + "/meters/" + meterSerial
                + "/consumption/?period_from=" + back24.toString() + "&period_to=" + now.toString();
        ChannelUID consumptionUID = new ChannelUID(thing.getUID(), id);

        try {
            sendTimeSeries(consumptionUID,
                    createConsumptionTimeSeries(connection.getResponse(urlConsumption24, apiKey + ":")));
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());

            return;
        }
        updateStatus(ThingStatus.ONLINE);
    }

    private TimeSeries[] createAgileTimeSeries(String agile) {

        JSONObject agileJson = new JSONObject(agile);
        JSONArray agileArray = new JSONArray(agileJson.getJSONArray("results"));
        TimeSeries[] agileRates = new TimeSeries[2];

        agileRates[0] = new TimeSeries(Policy.REPLACE);
        agileRates[1] = new TimeSeries(Policy.REPLACE);

        agileArray.forEach(item -> {
            JSONObject itemJson = (JSONObject) item;
            Instant timestamp = Instant.parse(itemJson.getString("valid_to"));

            QuantityType<EnergyPrice> value = new QuantityType<>(itemJson.getDouble("value_inc_vat") + " GBP/kWh");
            QuantityType<EnergyPrice> valueEx = new QuantityType<>(itemJson.getDouble("value_exc_vat") + " GBP/kWh");
            agileRates[0].add(timestamp, value);
            agileRates[1].add(timestamp, valueEx);
        });

        return agileRates;
    }

    private void updateAgile() {
        Instant now = Instant.now();
        Instant start = now.minus(24, ChronoUnit.HOURS);
        Instant end = now.plus(24, ChronoUnit.HOURS);
        String urlAgile = baseUrl + "/v1/products/AGILE-FLEX-22-11-25/electricity-tariffs/E-1R-AGILE-FLEX-22-11-25-"
                + agileRegion + "/standard-unit-rates/?period_from=" + start.toString() + "&period_to="
                + end.toString();
        ChannelUID agileUID = new ChannelUID(thing.getUID(), "agileRates");
        ChannelUID agileExUID = new ChannelUID(thing.getUID(), "agileExRates");

        TimeSeries[] agile = new TimeSeries[2];
        try {
            agile = createAgileTimeSeries(connection.getResponse(urlAgile, apiKey + ":"));
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
            return;
        }
        sendTimeSeries(agileUID, agile[0]);
        sendTimeSeries(agileExUID, agile[1]);
        updateStatus(ThingStatus.ONLINE);
    }

    private void pollTask() {
        updateConsumption(false);
        if (!mpanExport.isEmpty()) {
            updateConsumption(true);
        }
        updateAgile();
    }
}
