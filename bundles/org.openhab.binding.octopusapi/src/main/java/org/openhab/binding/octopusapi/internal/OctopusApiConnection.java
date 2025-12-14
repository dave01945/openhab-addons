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

    private final Logger logger = LoggerFactory.getLogger(OctopusApiConnection.class);

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
    protected void obtainToken(String apiKey) {
        String mutation = String.format(
                "{\"query\":\"mutation { obtainKrakenToken(input: {APIKey: \\\"%s\\\"}) { token refreshToken refreshExpiresIn } }\"}",
                apiKey);

        logger.trace("Obtaining Kraken token");
        JsonObject response = executeGraphQL(mutation, null);

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
     */
    private JsonObject executeGraphQL(String queryOrMutation, @Nullable String token) {
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
     * Get electricity meter readings using GraphQL.
     */
    protected String getConsumptionData(String apiKey, String accountNumber, String mpan, String meterSerial,
            Instant from, Instant to) {
        ensureValidToken(apiKey);

        // GraphQL consumption query - must use edges/node structure with first parameter (max 100)
        String query = String.format(
                "{\"query\":\"query { account(accountNumber: \\\"%s\\\") { properties { electricityMeterPoints { mpan meters { serialNumber consumption(startAt: \\\"%s\\\", grouping: HALF_HOUR, timezone: \\\"Europe/London\\\", first: 100) { edges { node { startAt value } } } } } } } }\"}",
                accountNumber, from.toString());

        JsonObject response = executeGraphQL(query, authToken);
        return response.toString();
    }

    /**
     * Get agile tariff rates using GraphQL.
     */
    protected String getAgileRates(String apiKey, String accountNumber, String mpan, Instant from, Instant to) {
        ensureValidToken(apiKey);

        // GraphQL agile rates query - first parameter max is 100
        String query = String.format(
                "{\"query\":\"query { applicableRates(accountNumber: \\\"%s\\\", mpxn: \\\"%s\\\", startAt: \\\"%s\\\", endAt: \\\"%s\\\", first: 100) { edges { node { validFrom validTo value } } } }\"}",
                accountNumber, mpan, from.toString(), to.toString());

        JsonObject response = executeGraphQL(query, authToken);
        return response.toString();
    }

    /**
     * Get real-time smart meter telemetry data (requires Octopus Home Mini).
     */
    protected String getSmartMeterTelemetry(String apiKey, String deviceId) {
        ensureValidToken(apiKey);

        // GraphQL smartMeterTelemetry query
        String query = String.format(
                "{\"query\":\"query { smartMeterTelemetry(deviceId: \\\"%s\\\") { readAt demand consumption } }\"}",
                deviceId);

        JsonObject response = executeGraphQL(query, authToken);
        return response.toString();
    }

    /**
     * Get gas consumption data.
     */
    protected String getGasConsumptionData(String apiKey, String accountNumber, String mprn, String meterSerial,
            Instant startDate, Instant endDate) {
        ensureValidToken(apiKey);

        String query = String.format(
                "{\"query\":\"query { account(accountNumber: \\\"%s\\\") { properties { gasMeterPoints { mprn meters { serialNumber consumption(startAt: \\\"%s\\\", grouping: HALF_HOUR, timezone: \\\"Europe/London\\\", first: 100) { edges { node { startAt value } } } } } } } }\"}",
                accountNumber, startDate);

        JsonObject response = executeGraphQL(query, authToken);
        return response.toString();
    }

    /**
     * Get smart meter device ID (GUID) for Home Mini.
     */
    protected String getSmartDeviceId(String apiKey, String accountNumber) {
        ensureValidToken(apiKey);

        // GraphQL query to find smart device ID
        String query = String.format(
                "{\"query\":\"query { account(accountNumber: \\\"%s\\\") { electricityAgreements(active: true) { meterPoint { meters(includeInactive: false) { smartDevices { deviceId } } } } } }\"}",
                accountNumber);

        JsonObject response = executeGraphQL(query, authToken);
        return response.toString();
    }

    /**
     * Get account details including MPANs, MPRNs and meter serials.
     */
    protected String getAccountDetails(String apiKey, String accountNumber) {
        ensureValidToken(apiKey);

        // GraphQL query to get account properties with electricity and gas meter details
        // Use inline fragment to access TariffType fields
        String query = String.format(
                "{\"query\":\"query { account(accountNumber: \\\"%s\\\") { properties { electricityMeterPoints { mpan agreements { validFrom validTo tariff { ... on TariffType { fullName displayName } } } meters { serialNumber } } gasMeterPoints { mprn agreements { validFrom validTo tariff { ... on TariffType { fullName displayName } } } meters { serialNumber } } } } }\"}",
                accountNumber);

        JsonObject response = executeGraphQL(query, authToken);
        return response.toString();
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
                    return firstError.get("message").getAsString();
                }
            }
        }
        return "Unknown GraphQL error";
    }
}
