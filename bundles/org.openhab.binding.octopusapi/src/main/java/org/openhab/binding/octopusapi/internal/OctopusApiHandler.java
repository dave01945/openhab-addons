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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.quantity.Energy;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.TimeSeries.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link OctopusApiHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author <David Jones> - Initial contribution
 */

@NonNullByDefault
public class OctopusApiHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OctopusApiHandler.class);

    private String apiKey = "";

    private String accountNumber = "";

    private String mpanImport = "";

    private String mpanExport = "";

    private String meterSerial = "";

    private String mprn = "";

    private String gasMeterSerial = "";

    private String deviceId = "";

    private @NonNullByDefault({}) OctopusApiConfiguration config;

    private @NonNullByDefault({}) OctopusApiConnection connection;

    private final HttpClient httpClient;

    private @NonNullByDefault({}) ScheduledFuture<?> scheduledFuture;

    private @NonNullByDefault({}) ScheduledFuture<?> livePollFuture;

    public OctopusApiHandler(Thing thing, HttpClient httpClient) {
        super(thing);
        this.httpClient = httpClient;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(OctopusApiActions.class);
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

        this.apiKey = config.apiKey;
        this.accountNumber = config.accountNumber;
        this.deviceId = config.deviceId;

        connection = new OctopusApiConnection(this, httpClient);

        // Query account details to get MPANs and meter serial from API
        try {
            queryAccountDetails();
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Failed to query account details: " + e.getMessage());
            return;
        }

        scheduledFuture = scheduler.scheduleWithFixedDelay(this::pollTask, 0, config.refreshInterval, TimeUnit.HOURS);

        // Start live polling if deviceId is configured (every liveRefreshInterval seconds)
        if (!deviceId.isEmpty()) {
            livePollFuture = scheduler.scheduleWithFixedDelay(this::pollLiveData, 0, config.liveRefreshInterval,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void dispose() {
        scheduledFuture.cancel(true);
        if (livePollFuture != null) {
            livePollFuture.cancel(true);
        }
    }

    private TimeSeries createConsumptionTimeSeries(String consumption) {

        JsonObject graphqlResponse = JsonParser.parseString(consumption).getAsJsonObject();
        TimeSeries consumptionSeries = new TimeSeries(Policy.ADD);

        // Navigate through GraphQL response structure
        JsonObject data = graphqlResponse.getAsJsonObject("data");
        if (data == null || !data.has("account")) {
            return consumptionSeries;
        }

        JsonObject account = data.getAsJsonObject("account");
        JsonArray properties = account.getAsJsonArray("properties");

        if (properties == null || properties.size() == 0) {
            return consumptionSeries;
        }

        // Iterate through properties to find electricity meter points
        for (JsonElement propElement : properties) {
            JsonObject property = propElement.getAsJsonObject();
            JsonArray meterPoints = property.getAsJsonArray("electricityMeterPoints");

            if (meterPoints == null) {
                continue;
            }

            for (JsonElement mpElement : meterPoints) {
                JsonObject meterPoint = mpElement.getAsJsonObject();
                JsonArray meters = meterPoint.getAsJsonArray("meters");

                if (meters == null) {
                    continue;
                }

                for (JsonElement meterElement : meters) {
                    JsonObject meter = meterElement.getAsJsonObject();
                    String serial = meter.get("serialNumber").getAsString();

                    // Match the meter serial number
                    if (!serial.equals(meterSerial)) {
                        continue;
                    }

                    JsonObject consumptionData = meter.getAsJsonObject("consumption");
                    if (consumptionData == null) {
                        continue;
                    }

                    JsonArray edges = consumptionData.getAsJsonArray("edges");
                    if (edges == null) {
                        continue;
                    }

                    QuantityType<Energy> latestValue = null;
                    Instant latestTime = null;

                    for (JsonElement edge : edges) {
                        JsonObject node = edge.getAsJsonObject().getAsJsonObject("node");
                        Instant timestamp = Instant.parse(node.get("startAt").getAsString());
                        QuantityType<Energy> value = QuantityType.valueOf(node.get("value").getAsDouble(),
                                Units.KILOWATT_HOUR);
                        consumptionSeries.add(timestamp, value);

                        // Track latest value for current state update
                        if (latestTime == null || timestamp.isAfter(latestTime)) {
                            latestTime = timestamp;
                            latestValue = value;
                        }
                    }

                    // Store latest value in series metadata for state update
                    if (latestValue != null) {
                        consumptionSeries.add(Instant.now(), latestValue);
                    }
                }
            }
        }

        return consumptionSeries;
    }

    private TimeSeries createGasConsumptionTimeSeries(String consumption) {
        JsonObject graphqlResponse = JsonParser.parseString(consumption).getAsJsonObject();
        TimeSeries consumptionSeries = new TimeSeries(Policy.ADD);

        JsonObject data = graphqlResponse.getAsJsonObject("data");
        if (data == null || !data.has("account")) {
            return consumptionSeries;
        }

        JsonObject account = data.getAsJsonObject("account");
        JsonArray properties = account.getAsJsonArray("properties");

        if (properties == null || properties.size() == 0) {
            return consumptionSeries;
        }

        for (JsonElement propElement : properties) {
            JsonObject property = propElement.getAsJsonObject();
            JsonArray meterPoints = property.getAsJsonArray("gasMeterPoints");

            if (meterPoints == null) {
                continue;
            }

            for (JsonElement mpElement : meterPoints) {
                JsonObject meterPoint = mpElement.getAsJsonObject();
                JsonArray meters = meterPoint.getAsJsonArray("meters");

                if (meters == null) {
                    continue;
                }

                for (JsonElement meterElement : meters) {
                    JsonObject meter = meterElement.getAsJsonObject();
                    String serial = meter.get("serialNumber").getAsString();

                    if (!serial.equals(gasMeterSerial)) {
                        continue;
                    }

                    JsonObject consumptionData = meter.getAsJsonObject("consumption");
                    if (consumptionData == null) {
                        continue;
                    }

                    JsonArray edges = consumptionData.getAsJsonArray("edges");
                    if (edges == null) {
                        continue;
                    }

                    QuantityType<Energy> latestValue = null;
                    Instant latestTime = null;

                    for (JsonElement edge : edges) {
                        JsonObject node = edge.getAsJsonObject().getAsJsonObject("node");
                        Instant timestamp = Instant.parse(node.get("startAt").getAsString());
                        QuantityType<Energy> value = QuantityType.valueOf(node.get("value").getAsDouble(),
                                Units.KILOWATT_HOUR);
                        consumptionSeries.add(timestamp, value);

                        if (latestTime == null || timestamp.isAfter(latestTime)) {
                            latestTime = timestamp;
                            latestValue = value;
                        }
                    }

                    if (latestValue != null) {
                        consumptionSeries.add(Instant.now(), latestValue);
                    }
                }
            }
        }

        return consumptionSeries;
    }

    private void updateConsumption(boolean export) {
        // Consumption data has a 24-48 hour delay, so query from 7 days ago to 2 days ago
        Instant now = Instant.now();
        Instant endDate = now.minus(2, ChronoUnit.DAYS);
        Instant startDate = endDate.minus(5, ChronoUnit.DAYS);
        String mpan = (export) ? mpanExport : mpanImport;
        String id = (export) ? "export" : "consumption";
        ChannelUID consumptionUID = new ChannelUID(thing.getUID(), id);

        try {
            String responseData = connection.getConsumptionData(apiKey, accountNumber, mpan, meterSerial, startDate,
                    endDate);
            TimeSeries series = createConsumptionTimeSeries(responseData);
            sendTimeSeries(consumptionUID, series);

            // Also update current state with the most recent value for immediate visibility
            if (series.size() > 0) {
                series.getStates().reduce((first, second) -> second)
                        .ifPresent(entry -> updateState(consumptionUID, entry.state()));
            }
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());

            return;
        }
        updateStatus(ThingStatus.ONLINE);
    }

    private TimeSeries[] createAgileTimeSeries(String agile) {

        JsonObject graphqlResponse = JsonParser.parseString(agile).getAsJsonObject();
        TimeSeries[] agileRates = new TimeSeries[2];

        agileRates[0] = new TimeSeries(Policy.REPLACE);
        agileRates[1] = new TimeSeries(Policy.REPLACE);

        JsonObject data = graphqlResponse.getAsJsonObject("data");
        if (data == null || !data.has("applicableRates")) {
            return agileRates;
        }

        JsonObject applicableRates = data.getAsJsonObject("applicableRates");
        JsonArray edges = applicableRates.getAsJsonArray("edges");

        if (edges == null) {
            return agileRates;
        }

        for (JsonElement edge : edges) {
            JsonObject node = edge.getAsJsonObject().getAsJsonObject("node");
            Instant timestamp = Instant.parse(node.get("validFrom").getAsString());

            // GraphQL API returns 'value' field (price including VAT in pence/kWh)
            BigDecimal valueIncVatPence = new BigDecimal(node.get("value").getAsString());

            // Convert from pence to pounds
            BigDecimal valueIncVatPounds = valueIncVatPence.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

            // Calculate ex-VAT value (domestic electricity VAT is 5%, so divide by 1.05)
            BigDecimal valueExcVatPounds = valueIncVatPounds.divide(new BigDecimal("1.05"), 4, RoundingMode.HALF_UP);

            logger.debug("Rate at {}: inc-VAT={}, exc-VAT={}", timestamp, valueIncVatPounds, valueExcVatPounds);

            // Store as dimensionless DecimalType (values are in GBP per kWh)
            DecimalType incVatDecimal = new DecimalType(valueIncVatPounds);
            DecimalType excVatDecimal = new DecimalType(valueExcVatPounds);

            logger.debug("DecimalType values: inc-VAT={}, exc-VAT={}", incVatDecimal, excVatDecimal);

            agileRates[0].add(timestamp, incVatDecimal);
            agileRates[1].add(timestamp, excVatDecimal);
        }

        return agileRates;
    }

    private void updateGasConsumption() {
        Instant now = Instant.now();
        Instant endDate = now.minus(2, ChronoUnit.DAYS);
        Instant startDate = endDate.minus(5, ChronoUnit.DAYS);
        ChannelUID gasConsumptionUID = new ChannelUID(thing.getUID(), "gasConsumption");

        try {
            String responseData = connection.getGasConsumptionData(apiKey, accountNumber, mprn, gasMeterSerial,
                    startDate, endDate);
            TimeSeries series = createGasConsumptionTimeSeries(responseData);
            sendTimeSeries(gasConsumptionUID, series);

            if (series.size() > 0) {
                series.getStates().reduce((first, second) -> second)
                        .ifPresent(entry -> updateState(gasConsumptionUID, entry.state()));
            }
        } catch (Exception e) {
            logger.debug("Failed to fetch gas consumption: {}", e.getMessage());
        }
    }

    private void updateGasTariffRates() {
        Instant now = Instant.now();
        Instant start = now;
        Instant end = now.plus(24, ChronoUnit.HOURS);
        ChannelUID gasRatesUID = new ChannelUID(thing.getUID(), "gasRates");
        ChannelUID gasExRatesUID = new ChannelUID(thing.getUID(), "gasExRates");

        TimeSeries[] gasRates = new TimeSeries[2];
        try {
            String responseData = connection.getAgileRates(apiKey, accountNumber, mprn, start, end);
            gasRates = createAgileTimeSeries(responseData);
        } catch (Exception e) {
            logger.debug("Failed to fetch gas rates: {}", e.getMessage());
            return;
        }

        sendTimeSeries(gasRatesUID, gasRates[0]);
        sendTimeSeries(gasExRatesUID, gasRates[1]);

        if (gasRates[0].size() > 0) {
            gasRates[0].getStates().reduce((first, second) -> second)
                    .ifPresent(entry -> updateState(gasRatesUID, entry.state()));
        }
        if (gasRates[1].size() > 0) {
            gasRates[1].getStates().reduce((first, second) -> second)
                    .ifPresent(entry -> updateState(gasExRatesUID, entry.state()));
        }
    }

    private void updateCurrentTariffRates() {
        Instant now = Instant.now();
        Instant start = now;
        Instant end = now.plus(24, ChronoUnit.HOURS);
        ChannelUID currentRatesUID = new ChannelUID(thing.getUID(), "currentRates");
        ChannelUID currentExRatesUID = new ChannelUID(thing.getUID(), "currentExRates");

        TimeSeries[] currentRates = new TimeSeries[2];
        try {
            // Use GraphQL applicableRates query which returns current account tariff rates
            String responseData = connection.getAgileRates(apiKey, accountNumber, mpanImport, start, end);
            currentRates = createAgileTimeSeries(responseData);
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
            return;
        }
        logger.debug("Sending inc-VAT rates to channel {} with {} entries", currentRatesUID, currentRates[0].size());
        logger.debug("Sending exc-VAT rates to channel {} with {} entries", currentExRatesUID, currentRates[1].size());
        sendTimeSeries(currentRatesUID, currentRates[0]);
        sendTimeSeries(currentExRatesUID, currentRates[1]);

        // Update current state with the most recent value from each TimeSeries
        if (currentRates[0].size() > 0) {
            currentRates[0].getStates().reduce((first, second) -> second)
                    .ifPresent(entry -> updateState(currentRatesUID, entry.state()));
        }
        if (currentRates[1].size() > 0) {
            currentRates[1].getStates().reduce((first, second) -> second)
                    .ifPresent(entry -> updateState(currentExRatesUID, entry.state()));
        }

        updateStatus(ThingStatus.ONLINE);
    }

    private void pollTask() {
        updateConsumption(false);
        if (!mpanExport.isEmpty()) {
            updateConsumption(true);
        }
        updateCurrentTariffRates();
        if (!mprn.isEmpty()) {
            updateGasConsumption();
            updateGasTariffRates();
        }
    }

    /**
     * Query account details from GraphQL API to get MPANs and meter serial.
     */
    private void queryAccountDetails() throws Exception {
        String responseData = connection.getAccountDetails(apiKey, accountNumber);
        JsonObject graphqlResponse = JsonParser.parseString(responseData).getAsJsonObject();
        JsonObject data = graphqlResponse.getAsJsonObject("data");

        if (data == null || !data.has("account")) {
            throw new Exception("No account data returned from API");
        }

        JsonObject account = data.getAsJsonObject("account");
        JsonArray properties = account.getAsJsonArray("properties");

        if (properties == null || properties.size() == 0) {
            throw new Exception("No properties found for account");
        }

        // Get first property
        JsonObject property = properties.get(0).getAsJsonObject();
        JsonArray meterPoints = property.getAsJsonArray("electricityMeterPoints");

        if (meterPoints == null || meterPoints.size() == 0) {
            throw new Exception("No electricity meter points found");
        }

        // Iterate through meter points to find import and export MPANs
        for (JsonElement mpElement : meterPoints) {
            JsonObject meterPoint = mpElement.getAsJsonObject();
            String mpan = meterPoint.get("mpan").getAsString();
            JsonArray agreements = meterPoint.getAsJsonArray("agreements");

            if (agreements == null || agreements.size() == 0) {
                continue;
            }

            // Check if this is export based on tariff name containing "export" or "outgoing"
            boolean isExport = false;
            for (JsonElement agElement : agreements) {
                JsonObject agreement = agElement.getAsJsonObject();
                JsonObject tariff = agreement.getAsJsonObject("tariff");

                if (tariff != null) {
                    String fullName = tariff.has("fullName") ? tariff.get("fullName").getAsString().toLowerCase() : "";
                    String displayName = tariff.has("displayName")
                            ? tariff.get("displayName").getAsString().toLowerCase()
                            : "";

                    if (fullName.contains("export") || fullName.contains("outgoing") || displayName.contains("export")
                            || displayName.contains("outgoing")) {
                        isExport = true;
                        break;
                    }
                }
            }

            if (isExport) {
                this.mpanExport = mpan;
            } else {
                this.mpanImport = mpan;
            }

            // Get meter serial from first meter
            JsonArray meters = meterPoint.getAsJsonArray("meters");
            if (meters != null && meters.size() > 0 && this.meterSerial.isEmpty()) {
                JsonObject meter = meters.get(0).getAsJsonObject();
                this.meterSerial = meter.get("serialNumber").getAsString();
            }
        }

        if (mpanImport.isEmpty()) {
            throw new Exception("No import MPAN found");
        }
        if (meterSerial.isEmpty()) {
            throw new Exception("No meter serial number found");
        }

        logger.info("Discovered MPANs - Import: {}, Export: {}, Meter Serial: {}", mpanImport,
                mpanExport.isEmpty() ? "none" : mpanExport, meterSerial);

        // Query gas meter points if available
        JsonArray gasMeterPoints = property.getAsJsonArray("gasMeterPoints");
        if (gasMeterPoints != null && gasMeterPoints.size() > 0) {
            JsonObject gasMeterPoint = gasMeterPoints.get(0).getAsJsonObject();
            this.mprn = gasMeterPoint.get("mprn").getAsString();

            JsonArray gasMeters = gasMeterPoint.getAsJsonArray("meters");
            if (gasMeters != null && gasMeters.size() > 0) {
                JsonObject gasMeter = gasMeters.get(0).getAsJsonObject();
                this.gasMeterSerial = gasMeter.get("serialNumber").getAsString();
            }
            logger.info("Discovered gas - MPRN: {}, Meter Serial: {}", mprn, gasMeterSerial);
        }
    }

    private void pollLiveData() {
        if (deviceId.isEmpty()) {
            return;
        }

        try {
            String responseData = connection.getSmartMeterTelemetry(apiKey, deviceId);
            updateLiveTelemetry(responseData);
        } catch (Exception e) {
            logger.debug("Failed to fetch live telemetry: {}", e.getMessage());
            // Don't set thing offline for live data failures - it's optional
        }
    }

    private void updateLiveTelemetry(String telemetry) {
        JsonObject graphqlResponse = JsonParser.parseString(telemetry).getAsJsonObject();
        JsonObject data = graphqlResponse.getAsJsonObject("data");

        if (data == null || !data.has("smartMeterTelemetry")) {
            return;
        }

        JsonArray telemetryArray = data.getAsJsonArray("smartMeterTelemetry");
        if (telemetryArray == null || telemetryArray.size() == 0) {
            return;
        }

        // Get the first (most recent) telemetry entry
        JsonObject telemetryData = telemetryArray.get(0).getAsJsonObject();

        // Update demand (instant power in Watts)
        if (telemetryData.has("demand") && !telemetryData.get("demand").isJsonNull()) {
            double demandWatts = telemetryData.get("demand").getAsDouble();
            QuantityType<javax.measure.quantity.Power> demand = QuantityType.valueOf(demandWatts, Units.WATT);
            updateState(new ChannelUID(thing.getUID(), "liveDemand"), demand);
        }

        // Update consumption (cumulative meter reading in Wh, convert to kWh)
        if (telemetryData.has("consumption") && !telemetryData.get("consumption").isJsonNull()) {
            double consumptionWh = telemetryData.get("consumption").getAsDouble();
            QuantityType<Energy> consumption = QuantityType.valueOf(consumptionWh / 1000.0, Units.KILOWATT_HOUR);
            updateState(new ChannelUID(thing.getUID(), "liveMeterReading"), consumption);
        }
    }

    /**
     * Get the smart meter device ID (GUID) for Octopus Home Mini.
     * This is a binding action that can be called from rules.
     */
    public String getSmartMeterDeviceId() throws Exception {
        String responseData = connection.getSmartDeviceId(apiKey, accountNumber);
        JsonObject graphqlResponse = JsonParser.parseString(responseData).getAsJsonObject();
        JsonObject data = graphqlResponse.getAsJsonObject("data");

        if (data == null || !data.has("account")) {
            throw new Exception("No account data returned from API");
        }

        JsonObject account = data.getAsJsonObject("account");
        JsonArray agreements = account.getAsJsonArray("electricityAgreements");

        if (agreements == null || agreements.size() == 0) {
            throw new Exception("No active electricity agreements found");
        }

        // Iterate through agreements to find smart devices
        for (JsonElement agreementElement : agreements) {
            JsonObject agreement = agreementElement.getAsJsonObject();
            JsonObject meterPoint = agreement.getAsJsonObject("meterPoint");

            if (meterPoint == null) {
                continue;
            }

            JsonArray meters = meterPoint.getAsJsonArray("meters");
            if (meters == null) {
                continue;
            }

            for (JsonElement meterElement : meters) {
                JsonObject meter = meterElement.getAsJsonObject();
                JsonArray smartDevices = meter.getAsJsonArray("smartDevices");

                if (smartDevices == null || smartDevices.size() == 0) {
                    continue;
                }

                // Return the first device ID found
                JsonObject device = smartDevices.get(0).getAsJsonObject();
                if (device.has("deviceId")) {
                    return device.get("deviceId").getAsString();
                }
            }
        }

        throw new Exception(
                "No smart meter device found. Ensure you have an Octopus Home Mini installed and configured.");
    }

    /**
     * Update the thing configuration with the device ID and reinitialize.
     * This is called by the action to automatically configure the binding.
     */
    public void updateDeviceIdConfiguration(String deviceId) {
        Configuration configuration = editConfiguration();
        configuration.put("deviceId", deviceId);
        updateConfiguration(configuration);

        // Reinitialize the thing to start live polling with the new device ID
        dispose();
        initialize();
    }
}
