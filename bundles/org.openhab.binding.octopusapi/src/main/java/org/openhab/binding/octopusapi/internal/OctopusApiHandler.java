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
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.quantity.Energy;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.library.types.DecimalType;
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
 * @author David Jones - Initial contribution
 */

@NonNullByDefault
public class OctopusApiHandler extends BaseThingHandler {

    private final Logger logger = Objects.requireNonNull(LoggerFactory.getLogger(OctopusApiHandler.class));

    private String apiKey = "";

    private String accountNumber = "";

    private boolean hasExportMeter = false;

    private boolean accountConfigured = false;

    private String accountRegion = "";

    private String electricityDeviceId = "";

    private String gasDeviceId = "";

    private @NonNullByDefault({}) JsonObject electricityTariffData;

    private @NonNullByDefault({}) JsonObject gasTariffData;

    private @NonNullByDefault({}) OctopusApiConfiguration config;

    private @NonNullByDefault({}) OctopusApiConnection connection;

    private final HttpClient httpClient;

    private @NonNullByDefault({}) ScheduledFuture<?> scheduledFuture;

    private @NonNullByDefault({}) ScheduledFuture<?> agileRatesFuture;

    private @NonNullByDefault({}) ScheduledFuture<?> livePollFuture;

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

        this.apiKey = config.apiKey;
        this.accountNumber = config.accountNumber;
        this.accountConfigured = !apiKey.trim().isEmpty() && !accountNumber.trim().isEmpty();

        connection = new OctopusApiConnection(this, httpClient);

        if (accountConfigured) {
            // Query comprehensive account details to get account-scoped data and device IDs
            try {
                queryAccountDetails();
            } catch (Exception e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Failed to query account details: " + e.getMessage());
            }
        } else {
            logger.debug(
                    "API key/account number not configured: account-scoped channels disabled, using public Agile rates");
        }

        scheduledFuture = scheduler.scheduleWithFixedDelay(this::pollTask, 0, config.refreshInterval, TimeUnit.HOURS);

        // Schedule a daily Agile rates refresh at 16:10 UTC (shortly after Octopus publishes next-day prices)
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime next1610 = now.with(LocalTime.of(16, 10));
        if (!now.isBefore(next1610)) {
            next1610 = next1610.plusDays(1);
        }
        long initialDelaySeconds = now.until(next1610, ChronoUnit.SECONDS);
        agileRatesFuture = scheduler.scheduleWithFixedDelay(this::updateForecastRates, initialDelaySeconds,
                24 * 60 * 60, TimeUnit.SECONDS);
        logger.debug("Scheduled daily Agile rates refresh at 16:10 UTC, first run in {} seconds", initialDelaySeconds);

        // Start live polling if electricityDeviceId was retrieved (every liveRefreshInterval seconds)
        if (!electricityDeviceId.isEmpty()) {
            logger.debug("Starting live data polling with device ID: {}", electricityDeviceId);
            livePollFuture = scheduler.scheduleWithFixedDelay(this::pollLiveData, 0, config.liveRefreshInterval,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> localScheduledFuture = scheduledFuture;
        if (localScheduledFuture != null) {
            localScheduledFuture.cancel(true);
        }
        ScheduledFuture<?> localAgileRatesFuture = agileRatesFuture;
        if (localAgileRatesFuture != null) {
            localAgileRatesFuture.cancel(true);
        }
        ScheduledFuture<?> localLivePollFuture = livePollFuture;
        if (localLivePollFuture != null) {
            localLivePollFuture.cancel(true);
        }
    }

    private TimeSeries createConsumptionTimeSeries(String consumption) {
        TimeSeries consumptionSeries = new TimeSeries(Policy.ADD);

        try {
            JsonObject graphqlResponse = JsonParser.parseString(consumption).getAsJsonObject();

            // Navigate through GraphQL measurements response structure
            JsonObject data = graphqlResponse.getAsJsonObject("data");
            if (data == null || !data.has("properties")) {
                return consumptionSeries;
            }

            JsonElement propertiesElement = data.get("properties");
            if (propertiesElement == null || !propertiesElement.isJsonArray()) {
                logger.debug("Properties element is not a JsonArray");
                return consumptionSeries;
            }

            JsonArray propertiesArray = propertiesElement.getAsJsonArray();
            if (propertiesArray.isEmpty()) {
                return consumptionSeries;
            }

            JsonObject properties = propertiesArray.get(0).getAsJsonObject();
            if (!properties.has("measurements")) {
                return consumptionSeries;
            }

            JsonElement measurementsElement = properties.get("measurements");
            if (measurementsElement == null || !measurementsElement.isJsonObject()) {
                logger.debug("Measurements element is not a JsonObject");
                return consumptionSeries;
            }

            JsonObject measurements = measurementsElement.getAsJsonObject();
            JsonArray edges = measurements.getAsJsonArray("edges");

            if (edges == null) {
                return consumptionSeries;
            }

            QuantityType<Energy> latestValue = null;
            Instant latestTime = null;

            for (JsonElement edge : edges) {
                JsonObject node = edge.getAsJsonObject().getAsJsonObject("node");
                String readAtStr = Objects.requireNonNull(node.get("readAt").getAsString());
                Instant timestamp = Objects.requireNonNull(Instant.parse(readAtStr));
                QuantityType<Energy> value = QuantityType.valueOf(node.get("value").getAsDouble(), Units.KILOWATT_HOUR);
                consumptionSeries.add(timestamp, value);

                // Track latest value for current state update
                if (latestTime == null || timestamp.isAfter(latestTime)) {
                    latestTime = timestamp;
                    latestValue = value;
                }
            }

            // Store latest value in series metadata for state update
            if (latestValue != null) {
                consumptionSeries.add(Objects.requireNonNull(Instant.now()), latestValue);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse consumption data: {}", e.getMessage(), e);
        }

        return consumptionSeries;
    }

    private TimeSeries createGasConsumptionTimeSeries(String consumption) {
        TimeSeries consumptionSeries = new TimeSeries(Policy.ADD);

        try {
            JsonObject graphqlResponse = JsonParser.parseString(consumption).getAsJsonObject();

            // Navigate through GraphQL measurements response structure (same as electricity)
            JsonObject data = graphqlResponse.getAsJsonObject("data");
            if (data == null || !data.has("properties")) {
                return consumptionSeries;
            }

            JsonElement propertiesElement = data.get("properties");
            if (propertiesElement == null || !propertiesElement.isJsonArray()) {
                logger.debug("Properties element is not a JsonArray");
                return consumptionSeries;
            }

            JsonArray propertiesArray = propertiesElement.getAsJsonArray();
            if (propertiesArray.isEmpty()) {
                return consumptionSeries;
            }

            JsonObject properties = propertiesArray.get(0).getAsJsonObject();
            if (!properties.has("measurements")) {
                return consumptionSeries;
            }

            JsonElement measurementsElement = properties.get("measurements");
            if (measurementsElement == null || !measurementsElement.isJsonObject()) {
                logger.debug("Measurements element is not a JsonObject");
                return consumptionSeries;
            }

            JsonObject measurements = measurementsElement.getAsJsonObject();
            JsonArray edges = measurements.getAsJsonArray("edges");

            if (edges == null) {
                return consumptionSeries;
            }

            QuantityType<Energy> latestValue = null;
            Instant latestTime = null;

            for (JsonElement edge : edges) {
                JsonObject node = edge.getAsJsonObject().getAsJsonObject("node");
                String readAtStr = Objects.requireNonNull(node.get("readAt").getAsString());
                Instant timestamp = Objects.requireNonNull(Instant.parse(readAtStr));
                QuantityType<Energy> value = QuantityType.valueOf(node.get("value").getAsDouble(), Units.KILOWATT_HOUR);
                consumptionSeries.add(timestamp, value);

                // Track latest value for current state update
                if (latestTime == null || timestamp.isAfter(latestTime)) {
                    latestTime = timestamp;
                    latestValue = value;
                }
            }

            // Store latest value in series metadata for state update
            if (latestValue != null) {
                consumptionSeries.add(Objects.requireNonNull(Instant.now()), latestValue);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse gas consumption data: {}", e.getMessage(), e);
        }

        return consumptionSeries;
    }

    private void updateConsumption(boolean export) {
        // Query 7 days of data using multiple API calls (API limit is 100 records per query)
        // 7 days of half-hourly data = 336 records, so we need multiple queries
        Instant now = Instant.now();
        String id = (export) ? "export" : "consumption";
        ChannelUID consumptionUID = new ChannelUID(thing.getUID(), id);

        try {
            TimeSeries combinedSeries = new TimeSeries(Policy.ADD);

            // Query in 2-day chunks (96 records each, safely under 100 limit)
            // Start from 7 days ago and work forward
            for (int daysBack = 7; daysBack > 0; daysBack -= 2) {
                Instant chunkEnd = now.minus(daysBack - 2, ChronoUnit.DAYS);
                Instant chunkStart = now.minus(daysBack, ChronoUnit.DAYS);

                // Don't query beyond current time
                if (chunkEnd.isAfter(now)) {
                    chunkEnd = now;
                }

                String responseData;
                if (export) {
                    responseData = connection.getExportData(apiKey, accountNumber, electricityDeviceId,
                            Objects.requireNonNull(chunkStart), Objects.requireNonNull(chunkEnd));
                } else {
                    responseData = connection.getConsumptionData(apiKey, accountNumber, electricityDeviceId,
                            Objects.requireNonNull(chunkStart), Objects.requireNonNull(chunkEnd));
                }

                TimeSeries chunkSeries = createConsumptionTimeSeries(responseData);

                // Add all data points from this chunk to the combined series
                chunkSeries.getStates().forEach(entry -> combinedSeries.add(entry.timestamp(), entry.state()));
            }

            sendTimeSeries(consumptionUID, combinedSeries);

            // Also update current state with the most recent value for immediate visibility
            if (combinedSeries.size() > 0) {
                combinedSeries.getStates().reduce((first, second) -> second)
                        .ifPresent(entry -> updateState(consumptionUID, entry.state()));
            }
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());

            return;
        }
        updateStatus(ThingStatus.ONLINE);
    }

    /**
     * Create TimeSeries from tariff data stored during initialization.
     * Handles different tariff types: HalfHourly, Standard, DayNight, ThreeRate, Prepay.
     */
    private TimeSeries[] createTimeSeriesFromTariffData(@Nullable JsonObject tariffData) {
        TimeSeries[] rates = new TimeSeries[2];
        rates[0] = new TimeSeries(Policy.REPLACE);
        rates[1] = new TimeSeries(Policy.REPLACE);

        if (tariffData == null) {
            return rates;
        }

        try {
            // Check if this is a HalfHourlyTariff (e.g., Agile) with unitRates array
            if (tariffData.has("unitRates") && !tariffData.get("unitRates").isJsonNull()
                    && tariffData.get("unitRates").isJsonArray()) {
                JsonArray unitRates = tariffData.getAsJsonArray("unitRates");
                for (JsonElement rateElement : unitRates) {
                    if (!rateElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject rate = rateElement.getAsJsonObject();
                    if (rate.has("validFrom") && !rate.get("validFrom").isJsonNull()) {
                        Instant timestamp = Instant.parse(rate.get("validFrom").getAsString());

                        // Get value (inc VAT) in pence, convert to pounds
                        double valueIncVatPence = rate.get("value").getAsDouble();
                        BigDecimal valueIncVatPounds = new BigDecimal(valueIncVatPence).divide(new BigDecimal("100"), 4,
                                RoundingMode.HALF_UP);

                        // Get preVatValue in pence, convert to pounds
                        double valueExcVatPence = rate.get("preVatValue").getAsDouble();
                        BigDecimal valueExcVatPounds = new BigDecimal(valueExcVatPence).divide(new BigDecimal("100"), 4,
                                RoundingMode.HALF_UP);

                        rates[0].add(timestamp, new DecimalType(valueIncVatPounds));
                        rates[1].add(timestamp, new DecimalType(valueExcVatPounds));
                    }
                }
            } else {
                // For non-time-varying tariffs (Standard, Prepay), create a single rate entry
                // For time-varying tariffs (DayNight, ThreeRate), we'd need to calculate the applicable rate
                // based on time of day, but that requires knowing the tariff structure times
                Instant now = Instant.now();

                if (tariffData.has("unitRate") && !tariffData.get("unitRate").isJsonNull()) {
                    // StandardTariff or PrepayTariff
                    double unitRatePence = tariffData.get("unitRate").getAsDouble();
                    double preVatUnitRatePence = tariffData.get("preVatUnitRate").getAsDouble();

                    BigDecimal unitRatePounds = new BigDecimal(unitRatePence).divide(new BigDecimal("100"), 4,
                            RoundingMode.HALF_UP);
                    BigDecimal preVatUnitRatePounds = new BigDecimal(preVatUnitRatePence).divide(new BigDecimal("100"),
                            4, RoundingMode.HALF_UP);

                    // Add a single point for current time
                    rates[0].add(now, new DecimalType(unitRatePounds));
                    rates[1].add(now, new DecimalType(preVatUnitRatePounds));
                } else if (tariffData.has("dayRate") && !tariffData.get("dayRate").isJsonNull()) {
                    // DayNightTariff or ThreeRateTariff - for now, just use day rate
                    // A full implementation would need to determine current time period
                    double dayRatePence = tariffData.get("dayRate").getAsDouble();
                    double preVatDayRatePence = tariffData.get("preVatDayRate").getAsDouble();

                    BigDecimal dayRatePounds = new BigDecimal(dayRatePence).divide(new BigDecimal("100"), 4,
                            RoundingMode.HALF_UP);
                    BigDecimal preVatDayRatePounds = new BigDecimal(preVatDayRatePence).divide(new BigDecimal("100"), 4,
                            RoundingMode.HALF_UP);

                    rates[0].add(now, new DecimalType(dayRatePounds));
                    rates[1].add(now, new DecimalType(preVatDayRatePounds));

                    logger.debug("Using day rate for time-varying tariff. Full time-of-day logic not implemented.");
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to create tariff time series: {}", e.getMessage(), e);
        }

        return rates;
    }

    private void updateGasConsumption() {
        Instant now = Instant.now();
        Instant endDate = now;
        Instant startDate = now.minus(3, ChronoUnit.DAYS);
        ChannelUID gasConsumptionUID = new ChannelUID(thing.getUID(), "gasConsumption");

        try {
            String responseData = connection.getGasConsumptionData(apiKey, accountNumber, gasDeviceId,
                    Objects.requireNonNull(startDate), Objects.requireNonNull(endDate));
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
        ChannelUID gasRatesUID = new ChannelUID(thing.getUID(), "gasRates");
        ChannelUID gasExRatesUID = new ChannelUID(thing.getUID(), "gasExRates");

        if (gasTariffData == null) {
            logger.debug("No gas tariff data available");
            return;
        }

        TimeSeries[] gasRates = createTimeSeriesFromTariffData(gasTariffData);

        sendTimeSeries(gasRatesUID, Objects.requireNonNull(gasRates[0]));
        sendTimeSeries(gasExRatesUID, Objects.requireNonNull(gasRates[1]));

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
        ChannelUID currentRatesUID = new ChannelUID(thing.getUID(), "currentRates");
        ChannelUID currentExRatesUID = new ChannelUID(thing.getUID(), "currentExRates");

        if (electricityTariffData == null) {
            logger.debug("No electricity tariff data available");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "No tariff data available from initialization");
            return;
        }

        TimeSeries[] currentRates = createTimeSeriesFromTariffData(electricityTariffData);

        logger.debug("Sending inc-VAT rates to channel {} with {} entries", currentRatesUID, currentRates[0].size());
        logger.debug("Sending exc-VAT rates to channel {} with {} entries", currentExRatesUID, currentRates[1].size());
        sendTimeSeries(currentRatesUID, Objects.requireNonNull(currentRates[0]));
        sendTimeSeries(currentExRatesUID, Objects.requireNonNull(currentRates[1]));

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

    private TimeSeries[] createForecastRatesTimeSeries(String data) {
        TimeSeries[] rates = new TimeSeries[2];
        rates[0] = new TimeSeries(Policy.REPLACE);
        rates[1] = new TimeSeries(Policy.REPLACE);

        try {
            JsonObject restResponse = JsonParser.parseString(data).getAsJsonObject();
            JsonArray results = restResponse.getAsJsonArray("results");
            if (results == null) {
                return rates;
            }

            for (JsonElement resultElement : results) {
                JsonObject result = resultElement.getAsJsonObject();
                if (!result.has("valid_from") || result.get("valid_from").isJsonNull()) {
                    continue;
                }
                Instant timestamp = Instant.parse(result.get("valid_from").getAsString());

                double valueIncVatPence = result.get("value_inc_vat").getAsDouble();
                BigDecimal valueIncVatPounds = new BigDecimal(valueIncVatPence).divide(new BigDecimal("100"), 4,
                        RoundingMode.HALF_UP);
                rates[0].add(timestamp, new DecimalType(valueIncVatPounds));

                if (result.has("value_exc_vat") && !result.get("value_exc_vat").isJsonNull()) {
                    double valueExcVatPence = result.get("value_exc_vat").getAsDouble();
                    BigDecimal valueExcVatPounds = new BigDecimal(valueExcVatPence).divide(new BigDecimal("100"), 4,
                            RoundingMode.HALF_UP);
                    rates[1].add(timestamp, new DecimalType(valueExcVatPounds));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse forecast rates data: {}", e.getMessage(), e);
        }

        return rates;
    }

    private void updateForecastRates() {
        ChannelUID agileRatesUID = new ChannelUID(thing.getUID(), "agileRates");
        ChannelUID agileExRatesUID = new ChannelUID(thing.getUID(), "agileExRates");

        try {
            Instant now = Instant.now();
            Instant endAt = now.plus(24, ChronoUnit.HOURS);
            String effectiveRegion = config.agileRegion.isBlank() ? (accountRegion.isBlank() ? "A" : accountRegion)
                    : config.agileRegion;
            String responseData = connection.getPublicAgileRates(config.agileProductCode, effectiveRegion,
                    Objects.requireNonNull(now), Objects.requireNonNull(endAt));
            TimeSeries[] forecastRates = createForecastRatesTimeSeries(responseData);

            logger.debug("Sending agile inc-VAT forecast to channel {} with {} entries", agileRatesUID,
                    forecastRates[0].size());
            logger.debug("Sending agile exc-VAT forecast to channel {} with {} entries", agileExRatesUID,
                    forecastRates[1].size());
            sendTimeSeries(agileRatesUID, Objects.requireNonNull(forecastRates[0]));
            sendTimeSeries(agileExRatesUID, Objects.requireNonNull(forecastRates[1]));
        } catch (Exception e) {
            logger.debug("Failed to fetch agile forecast rates: {}", e.getMessage());
        }
    }

    private void pollTask() {
        if (thing.getStatus() == ThingStatus.REMOVING || thing.getStatus() == ThingStatus.REMOVED) {
            return;
        }

        if (accountConfigured) {
            try {
                // Refresh account details to update balance, tariff info, and cached tariff data
                queryAccountDetails();
            } catch (Exception e) {
                logger.debug("Failed to refresh account details: {}", e.getMessage());
                // Continue with other updates even if this fails
            }

            updateConsumption(false);
            if (hasExportMeter) {
                updateConsumption(true);
            }
            updateCurrentTariffRates();
            if (!gasDeviceId.isEmpty()) {
                updateGasConsumption();
                updateGasTariffRates();
            }
        }

        updateForecastRates();
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

        // Update account balance
        if (account.has("balance") && !account.get("balance").isJsonNull()) {
            // Balance is in pence, convert to pounds
            double balancePence = account.get("balance").getAsDouble();
            double balancePounds = balancePence / 100.0;
            updateState(new ChannelUID(thing.getUID(), "accountBalance"), new DecimalType(balancePounds));
            logger.debug("Account balance: \u00a3{}", String.format("%.2f", balancePounds));
        }

        // Process electricity agreements
        JsonArray electricityAgreements = account.getAsJsonArray("electricityAgreements");
        if (electricityAgreements != null && electricityAgreements.size() > 0) {
            processElectricityAgreements(electricityAgreements);
        } else {
            throw new Exception("No electricity agreements found");
        }

        // Process gas agreements (optional)
        JsonArray gasAgreements = account.getAsJsonArray("gasAgreements");
        if (gasAgreements != null && gasAgreements.size() > 0) {
            processGasAgreements(gasAgreements);
        }
    }

    private void processElectricityAgreements(JsonArray electricityAgreements) {
        for (JsonElement agreementElement : electricityAgreements) {
            JsonObject agreement = agreementElement.getAsJsonObject();
            JsonObject meterPoint = agreement.getAsJsonObject("meterPoint");

            if (meterPoint == null) {
                continue;
            }

            // Get tariff information from agreements
            JsonArray agreements = meterPoint.getAsJsonArray("agreements");
            String tariffName = "";
            String tariffDescription = "";
            double standingCharge = 0.0;
            JsonObject tariff = null;

            if (agreements != null && agreements.size() > 0) {
                JsonObject currentAgreement = agreements.get(0).getAsJsonObject();
                tariff = currentAgreement.getAsJsonObject("tariff");

                if (tariff != null) {
                    if (tariff.has("displayName") && !tariff.get("displayName").isJsonNull()) {
                        tariffName = tariff.get("displayName").getAsString();
                    }
                    if (tariff.has("description") && !tariff.get("description").isJsonNull()) {
                        tariffDescription = tariff.get("description").getAsString();
                    }
                    if (tariff.has("standingCharge") && !tariff.get("standingCharge").isJsonNull()) {
                        // Standing charge is in pence, convert to pounds
                        standingCharge = tariff.get("standingCharge").getAsDouble() / 100.0;
                    }
                }
            }

            // Determine if this is import or export based on meter point
            // Export meters will have smartExportElectricityMeter, import will have smartImportElectricityMeter
            boolean isExport = false;
            JsonArray meters = meterPoint.getAsJsonArray("meters");

            if (meters != null && meters.size() > 0) {
                for (JsonElement meterElement : meters) {
                    JsonObject meter = meterElement.getAsJsonObject();

                    // Check for export meter
                    if (meter.has("smartExportElectricityMeter")
                            && !meter.get("smartExportElectricityMeter").isJsonNull()) {
                        isExport = true;
                        JsonObject exportMeter = meter.getAsJsonObject("smartExportElectricityMeter");
                        if (exportMeter.has("deviceId") && !exportMeter.get("deviceId").isJsonNull()
                                && electricityDeviceId.isEmpty()) {
                            electricityDeviceId = Objects.requireNonNull(exportMeter.get("deviceId").getAsString());
                            logger.debug("Found export smart meter device ID: {}", electricityDeviceId);
                        }
                    }

                    // Check for import meter (and device ID)
                    if (meter.has("smartImportElectricityMeter")
                            && !meter.get("smartImportElectricityMeter").isJsonNull()) {
                        JsonObject importMeter = meter.getAsJsonObject("smartImportElectricityMeter");
                        if (importMeter.has("deviceId") && !importMeter.get("deviceId").isJsonNull()
                                && electricityDeviceId.isEmpty()) {
                            electricityDeviceId = Objects.requireNonNull(importMeter.get("deviceId").getAsString());
                            logger.debug("Found import smart meter device ID: {}", electricityDeviceId);
                        }
                    }
                }
            }

            if (isExport) {
                hasExportMeter = true;
            } else {
                // Store electricity tariff data for rate updates
                if (tariff != null) {
                    electricityTariffData = tariff;
                    if (tariff.has("tariffCode") && !tariff.get("tariffCode").isJsonNull()) {
                        accountRegion = extractRegionFromTariffCode(tariff.get("tariffCode").getAsString());
                        logger.debug("Detected account region {} from tariff code", accountRegion);
                    }
                }

                // Update electricity tariff channels for import meter
                if (!tariffName.isEmpty()) {
                    updateState(new ChannelUID(thing.getUID(), "electricityTariffName"),
                            new org.openhab.core.library.types.StringType(tariffName));
                    logger.debug("Electricity tariff name: {}", tariffName);
                }
                if (!tariffDescription.isEmpty()) {
                    updateState(new ChannelUID(thing.getUID(), "electricityTariffDescription"),
                            new org.openhab.core.library.types.StringType(tariffDescription));
                    logger.debug("Electricity tariff description: {}", tariffDescription);
                }
                if (standingCharge > 0) {
                    updateState(new ChannelUID(thing.getUID(), "electricityStandingCharge"),
                            new DecimalType(standingCharge));
                    logger.debug("Electricity standing charge: \u00a3{}", String.format("%.2f", standingCharge));
                }
            }
        }
    }

    /**
     * Extract the GSP region letter from an Octopus tariff code.
     * Tariff codes follow the format E-1R-PRODUCT-CODE-REGION (e.g. E-1R-AGILE-24-10-01-A).
     * The last hyphen-separated segment is the single-letter region code.
     */
    private static String extractRegionFromTariffCode(String tariffCode) {
        if (tariffCode == null || tariffCode.isBlank()) {
            return "";
        }
        int lastHyphen = tariffCode.lastIndexOf('-');
        if (lastHyphen >= 0 && lastHyphen < tariffCode.length() - 1) {
            String region = tariffCode.substring(lastHyphen + 1).trim().toUpperCase();
            if (region.length() == 1 && region.charAt(0) >= 'A' && region.charAt(0) <= 'P') {
                return region;
            }
        }
        return "";
    }

    private void processGasAgreements(JsonArray gasAgreements) {
        for (JsonElement agreementElement : gasAgreements) {
            JsonObject agreement = agreementElement.getAsJsonObject();
            JsonObject meterPoint = agreement.getAsJsonObject("meterPoint");

            if (meterPoint == null) {
                continue;
            }

            // Get tariff information from agreements
            JsonArray agreements = meterPoint.getAsJsonArray("agreements");
            String tariffName = "";
            String tariffDescription = "";
            double standingCharge = 0.0;
            JsonObject tariff = null;

            if (agreements != null && agreements.size() > 0) {
                JsonObject currentAgreement = agreements.get(0).getAsJsonObject();
                tariff = currentAgreement.getAsJsonObject("tariff");

                if (tariff != null) {
                    if (tariff.has("displayName") && !tariff.get("displayName").isJsonNull()) {
                        tariffName = tariff.get("displayName").getAsString();
                    }
                    if (tariff.has("description") && !tariff.get("description").isJsonNull()) {
                        tariffDescription = tariff.get("description").getAsString();
                    }
                    if (tariff.has("standingCharge") && !tariff.get("standingCharge").isJsonNull()) {
                        // Standing charge is in pence, convert to pounds
                        standingCharge = tariff.get("standingCharge").getAsDouble() / 100.0;
                    }

                    // Store gas tariff data for rate updates
                    gasTariffData = tariff;
                }
            }

            // Get gas device ID
            JsonArray meters = meterPoint.getAsJsonArray("meters");
            if (meters != null && meters.size() > 0) {
                JsonObject meter = meters.get(0).getAsJsonObject();

                // Check for smart gas meter device ID
                if (meter.has("smartGasMeter") && !meter.get("smartGasMeter").isJsonNull()) {
                    JsonObject gasMeter = meter.getAsJsonObject("smartGasMeter");
                    if (gasMeter.has("deviceId") && !gasMeter.get("deviceId").isJsonNull()) {
                        gasDeviceId = Objects.requireNonNull(gasMeter.get("deviceId").getAsString());
                        logger.debug("Found gas smart meter device ID: {}", gasDeviceId);
                    }
                }
            }

            // Update gas tariff channels
            if (!tariffName.isEmpty()) {
                updateState(new ChannelUID(thing.getUID(), "gasTariffName"),
                        new org.openhab.core.library.types.StringType(tariffName));
                logger.debug("Gas tariff name: {}", tariffName);
            }
            if (!tariffDescription.isEmpty()) {
                updateState(new ChannelUID(thing.getUID(), "gasTariffDescription"),
                        new org.openhab.core.library.types.StringType(tariffDescription));
                logger.debug("Gas tariff description: {}", tariffDescription);
            }
            if (standingCharge > 0) {
                updateState(new ChannelUID(thing.getUID(), "gasStandingCharge"), new DecimalType(standingCharge));
                logger.debug("Gas standing charge: \u00a3{}", String.format("%.2f", standingCharge));
            }

            break; // Only process first gas agreement
        }
    }

    private void pollLiveData() {
        // Don't poll if handler is being disposed
        if (thing.getStatus() == ThingStatus.REMOVING || thing.getStatus() == ThingStatus.REMOVED) {
            return;
        }

        if (electricityDeviceId.isEmpty()) {
            return;
        }

        try {
            String responseData = connection.getSmartMeterTelemetry(apiKey, electricityDeviceId);
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
            logger.debug("No smartMeterTelemetry data in response");
            return;
        }

        JsonArray telemetryArray = data.getAsJsonArray("smartMeterTelemetry");
        if (telemetryArray == null || telemetryArray.size() == 0) {
            logger.debug("Empty smartMeterTelemetry array");
            return;
        }

        // Get the first (most recent) telemetry entry
        JsonObject telemetryData = telemetryArray.get(0).getAsJsonObject();
        logger.debug("Telemetry data: {}", telemetryData);

        // Update demand (instant power in Watts)
        if (telemetryData.has("demand") && !telemetryData.get("demand").isJsonNull()) {
            double demandWatts = telemetryData.get("demand").getAsDouble();
            QuantityType<javax.measure.quantity.Power> demand = QuantityType.valueOf(demandWatts, Units.WATT);
            updateState(new ChannelUID(thing.getUID(), "liveDemand"), demand);
            logger.debug("Updated liveDemand: {} W", demandWatts);
        } else {
            logger.debug("No demand data in telemetry");
        }

        // Update consumption (cumulative meter reading in Wh, convert to kWh)
        if (telemetryData.has("consumption") && !telemetryData.get("consumption").isJsonNull()) {
            double consumptionWh = telemetryData.get("consumption").getAsDouble();
            QuantityType<Energy> consumption = QuantityType.valueOf(consumptionWh / 1000.0, Units.KILOWATT_HOUR);
            updateState(new ChannelUID(thing.getUID(), "liveMeterReading"), consumption);
            logger.debug("Updated liveMeterReading: {} kWh", consumptionWh / 1000.0);
        } else {
            logger.debug("No consumption data in telemetry");
        }

        // Update export (cumulative export meter reading in Wh, convert to kWh)
        if (telemetryData.has("export") && !telemetryData.get("export").isJsonNull()) {
            double exportWh = telemetryData.get("export").getAsDouble();
            QuantityType<Energy> export = QuantityType.valueOf(exportWh / 1000.0, Units.KILOWATT_HOUR);
            updateState(new ChannelUID(thing.getUID(), "liveExportReading"), export);
            logger.debug("Updated liveExportReading: {} kWh", exportWh / 1000.0);
        } else {
            logger.debug("No export data in telemetry");
        }
    }
}
