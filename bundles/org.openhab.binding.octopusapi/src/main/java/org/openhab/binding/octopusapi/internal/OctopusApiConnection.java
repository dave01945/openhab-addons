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

import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.http.HttpStatus.TOO_MANY_REQUESTS_429;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.openhab.core.i18n.CommunicationException;
import org.openhab.core.i18n.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link OctopusApiConnection} class contains API connection parameters.
 *
 * @author David Jones - Initial contribution
 */

@NonNullByDefault
public class OctopusApiConnection {

    private final Logger logger = Objects.requireNonNull(LoggerFactory.getLogger(OctopusApiConnection.class));

    private final HttpClient httpClient;
    private static final String GRAPHQL_ENDPOINT = "https://api.octopus.energy/v1/graphql/";

    private @Nullable String authToken;
    private @Nullable Instant tokenExpiry;

    public OctopusApiConnection(OctopusApiHandler handler, HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Obtain authentication token using API key.
     */
    @SuppressWarnings("null")
    protected void obtainToken(String apiKey) {
        String mutation = String.format(
                "{\"query\":\"mutation { obtainKrakenToken(input: {APIKey: \\\"%s\\\"}) { token refreshToken refreshExpiresIn } }\"}",
                apiKey);

        logger.trace("Obtaining Kraken token");
        String nullToken = null;
        JsonObject response = executeGraphQL(mutation, nullToken);

        JsonObject data = response.getAsJsonObject("data");
        if (data != null && data.has("obtainKrakenToken")) {
            JsonObject tokenData = data.getAsJsonObject("obtainKrakenToken");
            authToken = tokenData.get("token").getAsString();
            // Token is valid for 60 minutes
            tokenExpiry = Instant.now().plus(55, ChronoUnit.MINUTES);
            logger.debug("Successfully obtained authentication token");
        } else {
            throw new ConfigurationException("Failed to obtain authentication token");
        }
    }

    /**
     * Check if token is valid and refresh if needed.
     */
    private void ensureValidToken(String apiKey) {
        Instant now = Instant.now();
        Instant expiry = tokenExpiry;
        if (authToken == null || expiry == null || now.isAfter(expiry)) {
            obtainToken(apiKey);
        }
    }

    /**
     * Execute a GraphQL query or mutation.
     *
     * @param queryOrMutation The GraphQL query or mutation string
     * @param token Authentication token (can be null for initial token request)
     */
    private JsonObject executeGraphQL(String queryOrMutation, @Nullable String token) {
        return executeGraphQLInternal(queryOrMutation, token);
    }

    /**
     * Execute a GraphQL query with authentication token.
     */
    @SuppressWarnings("null")
    private JsonObject executeGraphQLAuthenticated(String queryOrMutation, String token) {
        return executeGraphQLInternal(queryOrMutation, token);
    }

    /**
     * Internal method to execute GraphQL requests.
     */
    @SuppressWarnings("null")
    private JsonObject executeGraphQLInternal(String queryOrMutation, @Nullable String token) {
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("GraphQL request: {}", queryOrMutation);
            }

            StringContentProvider contentProvider = new StringContentProvider("application/json", queryOrMutation,
                    StandardCharsets.UTF_8);
            var request = httpClient.newRequest(GRAPHQL_ENDPOINT).method(POST).content(contentProvider).timeout(10,
                    TimeUnit.SECONDS);

            if (token != null) {
                request.header("Authorization", token);
            }

            ContentResponse contentResponse = request.send();
            int httpStatus = contentResponse.getStatus();
            String content = contentResponse.getContentAsString();

            logger.trace("Octopus GraphQL response: status = {}, content = '{}'", httpStatus, content);

            if (httpStatus == OK_200) {
                JsonElement jsonResponse = JsonParser.parseString(content);
                if (jsonResponse.isJsonObject()) {
                    JsonObject jsonObject = jsonResponse.getAsJsonObject();

                    // Check for GraphQL errors
                    if (jsonObject.has("errors")) {
                        String errorMessage = extractGraphQLError(jsonObject);

                        // Check if this is a rate limit error
                        if (isRateLimitError(jsonObject)) {
                            logger.debug("Rate limit error: {}", errorMessage);
                            throw new CommunicationException("Rate limit exceeded: " + errorMessage);
                        }

                        logger.debug("GraphQL errors: {}", errorMessage);
                        throw new ConfigurationException(errorMessage);
                    }

                    return jsonObject;
                }
            } else if (httpStatus == TOO_MANY_REQUESTS_429) {
                throw new CommunicationException("Rate limit exceeded");
            }

            logger.warn("GraphQL request failed with HTTP {}: {}", httpStatus, content);
            throw new CommunicationException("Unexpected HTTP status: " + httpStatus);

        } catch (ExecutionException e) {
            String errorMessage = e.getMessage();
            logger.debug("ExecutionException occurred during execution: {}", errorMessage, e);
            throw new CommunicationException(errorMessage == null ? "@text/offline.communication-error" : errorMessage,
                    e.getCause());
        } catch (TimeoutException e) {
            String errorMessage = e.getMessage();
            logger.debug("TimeoutException occurred during execution: {}", errorMessage, e);
            throw new CommunicationException(errorMessage == null ? "@text/offline.communication-error" : errorMessage,
                    e.getCause());
        } catch (InterruptedException e) {
            String errorMessage = e.getMessage();
            logger.debug("InterruptedException occurred during execution: {}", errorMessage, e);
            Thread.currentThread().interrupt();
            throw new CommunicationException(errorMessage == null ? "@text/offline.communication-error" : errorMessage,
                    e.getCause());
        }
    }

    /**
     * Get electricity meter readings using GraphQL measurements query.
     */
    @SuppressWarnings("null")
    protected String getConsumptionData(String apiKey, String accountNumber, String deviceId, Instant from,
            Instant to) {
        ensureValidToken(apiKey);

        // GraphQL measurements query using deviceId and readingDirection filter
        // API max limit is 100 records per query (about 2 days of half-hourly data)
        String query = String.format(
                "{\"query\":\"query { properties(accountNumber: \\\"%s\\\") { measurements(first: 100, timezone: \\\"Europe/London\\\", utilityFilters: {electricityFilters: {deviceId: \\\"%s\\\", readingDirection: CONSUMPTION}}, startAt: \\\"%s\\\") { edges { node { readAt value unit } } } } }\"}",
                accountNumber, deviceId, from.toString());

        JsonObject response = executeGraphQLAuthenticated(query, Objects.requireNonNull(authToken));
        return Objects.requireNonNull(response.toString());
    }

    /**
     * Get electricity meter export readings using GraphQL measurements query.
     * Export data uses GENERATION readingDirection to get solar/generation export.
     */
    @SuppressWarnings("null")
    protected String getExportData(String apiKey, String accountNumber, String deviceId, Instant from, Instant to) {
        ensureValidToken(apiKey);

        // GraphQL measurements query using deviceId and GENERATION readingDirection filter
        // API max limit is 100 records per query (about 2 days of half-hourly data)
        String query = String.format(
                "{\"query\":\"query { properties(accountNumber: \\\"%s\\\") { measurements(first: 100, timezone: \\\"Europe/London\\\", utilityFilters: {electricityFilters: {deviceId: \\\"%s\\\", readingDirection: GENERATION}}, startAt: \\\"%s\\\") { edges { node { readAt value unit } } } } }\"}",
                accountNumber, deviceId, from.toString());

        JsonObject response = executeGraphQLAuthenticated(query, Objects.requireNonNull(authToken));
        return Objects.requireNonNull(response.toString());
    }

    /**
     * Get tariff rates using GraphQL.
     */
    @SuppressWarnings("null")
    protected String getTariffRates(String apiKey, String accountNumber, String mpan, Instant from, Instant to) {
        ensureValidToken(apiKey);

        // GraphQL agile rates query - first parameter max is 100
        String query = String.format(
                "{\"query\":\"query { applicableRates(accountNumber: \\\"%s\\\", mpxn: \\\"%s\\\", startAt: \\\"%s\\\", endAt: \\\"%s\\\", first: 100) { edges { node { validFrom validTo value } } } }\"}",
                accountNumber, mpan, from.toString(), to.toString());

        JsonObject response = executeGraphQLAuthenticated(query, Objects.requireNonNull(authToken));
        return Objects.requireNonNull(response.toString());
    }

    /**
     * Get real-time smart meter telemetry data (requires Octopus Home Mini).
     */
    @SuppressWarnings("null")
    protected String getSmartMeterTelemetry(String apiKey, String deviceId) {
        ensureValidToken(apiKey);

        // GraphQL smartMeterTelemetry query
        String query = String.format(
                "{\"query\":\"query { smartMeterTelemetry(deviceId: \\\"%s\\\") { readAt demand consumption export } }\"}",
                deviceId);

        JsonObject response = executeGraphQLAuthenticated(query, Objects.requireNonNull(authToken));
        return Objects.requireNonNull(response.toString());
    }

    /**
     * Get gas consumption data using GraphQL measurements query.
     */
    @SuppressWarnings("null")
    protected String getGasConsumptionData(String apiKey, String accountNumber, String gasDeviceId, Instant startDate,
            Instant endDate) {
        ensureValidToken(apiKey);

        // GraphQL measurements query using gasDeviceId filter
        // API max limit is 100 records per query (about 2 days of half-hourly data)
        String query = String.format(
                "{\"query\":\"query { properties(accountNumber: \\\"%s\\\") { measurements(first: 100, timezone: \\\"Europe/London\\\", utilityFilters: {gasFilters: {deviceId: \\\"%s\\\"}}, startAt: \\\"%s\\\") { edges { node { readAt value unit } } } } }\"}",
                accountNumber, gasDeviceId, startDate.toString());

        JsonObject response = executeGraphQLAuthenticated(query, Objects.requireNonNull(authToken));
        return Objects.requireNonNull(response.toString());
    }

    /**
     * Get comprehensive account details including balance, MPANs, MPRNs, meter serials, tariffs, and device IDs.
     */
    @SuppressWarnings("null")
    protected String getAccountDetails(String apiKey, String accountNumber) {
        ensureValidToken(apiKey);

        // Comprehensive GraphQL query to get all account information in one call
        // Includes balance, agreements with tariffs, standing charges, smart meter device IDs, and rate structures
        String query = String.format(
                "{\"query\":\"query { account(accountNumber: \\\"%s\\\") { balance electricityAgreements(active: true) { meterPoint { mpan meters(includeInactive: false) { serialNumber smartImportElectricityMeter { deviceId manufacturer model firmwareVersion } smartExportElectricityMeter { deviceId manufacturer model firmwareVersion } } agreements(includeInactive: false) { validTo validFrom tariff { ... on TariffType { productCode standingCharge isExport displayName description __typename } ... on StandardTariff { unitRate preVatUnitRate } ... on DayNightTariff { dayRate preVatDayRate nightRate preVatNightRate } ... on ThreeRateTariff { nightRate offPeakRate preVatDayRate preVatNightRate preVatOffPeakRate dayRate } ... on HalfHourlyTariff { unitRates { preVatValue value rateType validFrom validTo } } ... on PrepayTariff { preVatUnitRate unitRate } } } } } gasAgreements(active: true) { meterPoint { mprn meters(includeInactive: false) { serialNumber consumptionUnits smartGasMeter { deviceId manufacturer model firmwareVersion } } agreements(includeInactive: false) { validFrom validTo tariff { standingCharge productCode displayName description unitRate preVatUnitRate } } } } } }\"}",
                accountNumber);

        JsonObject response = executeGraphQLAuthenticated(query, Objects.requireNonNull(authToken));
        return Objects.requireNonNull(response.toString());
    }

    /**
     * Extract error message from GraphQL error response.
     */
    private String extractGraphQLError(JsonObject response) {
        if (response.has("errors") && response.get("errors").isJsonArray()) {
            var errors = response.getAsJsonArray("errors");
            if (errors.size() > 0) {
                JsonObject firstError = errors.get(0).getAsJsonObject();
                if (firstError.has("message")) {
                    return Objects.requireNonNull(firstError.get("message").getAsString());
                }
            }
        }
        return "Unknown GraphQL error";
    }

    /**
     * Check if GraphQL error is a rate limit error.
     */
    private boolean isRateLimitError(JsonObject response) {
        if (response.has("errors") && response.get("errors").isJsonArray()) {
            var errors = response.getAsJsonArray("errors");
            if (errors.size() > 0) {
                JsonObject firstError = errors.get(0).getAsJsonObject();
                // Check for rate limit error code KT-CT-1199
                if (firstError.has("extensions")) {
                    JsonObject extensions = firstError.getAsJsonObject("extensions");
                    if (extensions.has("errorCode")) {
                        String errorCode = extensions.get("errorCode").getAsString();
                        return "KT-CT-1199".equals(errorCode);
                    }
                }
                // Also check message for "Too many requests"
                if (firstError.has("message")) {
                    String message = firstError.get("message").getAsString();
                    return message.toLowerCase().contains("too many requests");
                }
            }
        }
        return false;
    }
}
