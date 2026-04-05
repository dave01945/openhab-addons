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

import static org.openhab.binding.octopusapi.internal.OctopusApiBindingConstants.CONFIG_AGILE_EXPORT_PRODUCT_CODE;
import static org.openhab.binding.octopusapi.internal.OctopusApiBindingConstants.CONFIG_AGILE_PRODUCT_CODE;
import static org.openhab.binding.octopusapi.internal.OctopusApiBindingConstants.CONFIG_AGILE_REGION;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.ConfigOptionProvider;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Dynamic options provider for Octopus API thing configuration parameters.
 *
 * @author David Jones - Initial contribution
 */
@Component(service = ConfigOptionProvider.class)
@NonNullByDefault
public class OctopusApiConfigOptionProvider implements ConfigOptionProvider {

    private static final Map<String, String> REGION_NAMES = Map.ofEntries(Map.entry("A", "Eastern England"),
            Map.entry("B", "East Midlands"), Map.entry("C", "London"), Map.entry("D", "Merseyside and North Wales"),
            Map.entry("E", "West Midlands"), Map.entry("F", "North East England"), Map.entry("G", "North West England"),
            Map.entry("H", "Southern England"), Map.entry("J", "South East England"), Map.entry("K", "South Wales"),
            Map.entry("L", "South West England"), Map.entry("M", "Yorkshire"), Map.entry("N", "Southern Scotland"),
            Map.entry("P", "Northern Scotland"));

    private final Logger logger = Objects.requireNonNull(LoggerFactory.getLogger(OctopusApiConfigOptionProvider.class));
    private final OctopusApiConnection connection;

    @Activate
    public OctopusApiConfigOptionProvider(@Reference HttpClientFactory httpClientFactory) {
        this.connection = new OctopusApiConnection(httpClientFactory.getCommonHttpClient());
    }

    @Override
    public @Nullable Collection<ParameterOption> getParameterOptions(URI uri, String param, @Nullable String context,
            @Nullable Locale locale) {
        if (!"thing-type".equals(uri.getScheme())) {
            return null;
        }

        String schemeSpecificPart = uri.getSchemeSpecificPart();
        if (schemeSpecificPart == null || !schemeSpecificPart.startsWith("octopusapi:")) {
            return null;
        }

        if (CONFIG_AGILE_PRODUCT_CODE.equals(param)) {
            try {
                Collection<String> productCodes = connection.getPublicAgileProductCodes();
                List<ParameterOption> options = new ArrayList<>();
                options.add(new ParameterOption("", "Auto (latest active Agile import product)"));
                productCodes.forEach(productCode -> options.add(new ParameterOption(productCode, productCode)));
                return options;
            } catch (Exception e) {
                logger.debug("Failed to load dynamic Agile product options: {}", e.getMessage());
                return List.of(new ParameterOption("", "Auto (latest active Agile import product)"));
            }
        }

        if (CONFIG_AGILE_EXPORT_PRODUCT_CODE.equals(param)) {
            try {
                Collection<String> productCodes = connection.getPublicAgileExportProductCodes();
                List<ParameterOption> options = new ArrayList<>();
                options.add(new ParameterOption("", "Auto (latest active Agile OUTGOING product)"));
                productCodes.forEach(productCode -> options.add(new ParameterOption(productCode, productCode)));
                return options;
            } catch (Exception e) {
                logger.debug("Failed to load dynamic Agile export product options: {}", e.getMessage());
                return List.of(new ParameterOption("", "Auto (latest active Agile OUTGOING product)"));
            }
        }

        if (!CONFIG_AGILE_REGION.equals(param)) {
            return null;
        }

        String productCode = getContextProductCode(context);

        try {
            Collection<String> regions = connection.getPublicAgileRegions(productCode);
            List<ParameterOption> options = new ArrayList<>();
            options.add(new ParameterOption("", "Auto (detect from account — requires API Key and Account Number)"));
            regions.forEach(region -> options.add(new ParameterOption(region, regionLabel(region))));
            return options;
        } catch (Exception e) {
            logger.debug("Failed to load dynamic Agile region options: {}", e.getMessage());
            return getFallbackOptions();
        }
    }

    private String getContextProductCode(@Nullable String context) {
        if (context == null || context.isBlank()) {
            return "";
        }

        try {
            JsonObject contextObject = JsonParser.parseString(context).getAsJsonObject();
            if (contextObject.has(CONFIG_AGILE_PRODUCT_CODE)
                    && !contextObject.get(CONFIG_AGILE_PRODUCT_CODE).isJsonNull()) {
                return contextObject.get(CONFIG_AGILE_PRODUCT_CODE).getAsString();
            }
        } catch (Exception e) {
            logger.trace("Could not parse parameter options context as JSON: {}", e.getMessage());
        }

        return "";
    }

    private static String regionLabel(String code) {
        String name = REGION_NAMES.get(code);
        return name != null ? code + " — " + name : code;
    }

    private Collection<ParameterOption> getFallbackOptions() {
        List<ParameterOption> options = new ArrayList<>();
        options.add(new ParameterOption("", "Auto (detect from account — requires API Key and Account Number)"));
        REGION_NAMES.keySet().stream().sorted()
                .forEach(code -> options.add(new ParameterOption(code, regionLabel(code))));
        return options;
    }
}
