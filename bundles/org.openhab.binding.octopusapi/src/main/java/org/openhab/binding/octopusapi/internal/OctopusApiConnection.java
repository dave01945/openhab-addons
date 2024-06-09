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

import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.http.HttpStatus.TOO_MANY_REQUESTS_429;
import static org.eclipse.jetty.http.HttpStatus.UNAUTHORIZED_401;

import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.api.ContentResponse;
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

    private final OctopusApiHandler handler;
    private final HttpClient httpClient;

    public OctopusApiConnection(OctopusApiHandler handler, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.handler = handler;
    }

    protected String getResponse(String url, String auth) {
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("Octopus request: URL = '{}'", url);
            }
            String auth64 = Base64.getEncoder().encodeToString(auth.getBytes());
            String header = "Basic " + auth64;
            ContentResponse contentResponse = httpClient.newRequest(url).method(GET).header("Authorization", header)
                    .timeout(10, TimeUnit.SECONDS).send();
            int httpStatus = contentResponse.getStatus();
            String content = contentResponse.getContentAsString();
            String errorMessage = "";
            logger.trace("Ocopus API response: status = {}, content = '{}'", httpStatus, content);
            switch (httpStatus) {
                case OK_200:
                    return content;
                case BAD_REQUEST_400:
                case UNAUTHORIZED_401:
                case NOT_FOUND_404:
                    errorMessage = getErrorMessage(content);
                    logger.debug("Octopus API server responded with status code {}: {}", httpStatus, errorMessage);
                    throw new ConfigurationException(errorMessage);
                case TOO_MANY_REQUESTS_429:
                default:
                    errorMessage = getErrorMessage(content);
                    logger.debug("Octopus API server responded with status code {}: {}", httpStatus, errorMessage);
                    throw new CommunicationException(errorMessage);
            }
        } catch (ExecutionException e) {
            String errorMessage = e.getMessage();
            logger.debug("ExecutionException occurred during execution: {}", errorMessage, e);
            if (e.getCause() instanceof HttpResponseException) {
                logger.debug("Octopus API server responded with status code {}: Invalid API key.", UNAUTHORIZED_401);
                throw new ConfigurationException("@text/offline.conf-error-invalid-apikey", e.getCause());
            } else {
                throw new CommunicationException(
                        errorMessage == null ? "@text/offline.communication-error" : errorMessage, e.getCause());
            }
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

    private String getErrorMessage(String response) {
        JsonElement jsonResponse = JsonParser.parseString(response);
        if (jsonResponse.isJsonObject()) {
            JsonObject json = jsonResponse.getAsJsonObject();
            if (json.has("message")) {
                return json.get("message").getAsString();
            }
        }
        return response;
    }
}
