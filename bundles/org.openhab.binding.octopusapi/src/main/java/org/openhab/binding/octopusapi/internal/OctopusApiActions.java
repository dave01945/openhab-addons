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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.ActionOutput;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OctopusApiActions} provides actions for the Octopus API binding
 *
 * @author David Jones - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = OctopusApiActions.class)
@ThingActionsScope(name = "octopusapi")
@NonNullByDefault
public class OctopusApiActions implements ThingActions {

    private final Logger logger = LoggerFactory.getLogger(OctopusApiActions.class);
    private @Nullable OctopusApiHandler handler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        this.handler = (OctopusApiHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "Get Smart Meter Device ID", description = "Retrieves the smart meter device GUID for Octopus Home Mini and automatically configures it")
    public @ActionOutput(name = "deviceId", type = "java.lang.String", label = "Device ID", description = "The smart meter device GUID (format: AA-BB-CC-DD-EE-FF-GG-HH)") String getSmartMeterDeviceId(
            @ActionInput(name = "autoConfig", label = "Auto Configure", description = "Automatically update thing configuration with the device ID", defaultValue = "true") @Nullable Boolean autoConfig) {
        OctopusApiHandler localHandler = handler;
        if (localHandler == null) {
            logger.warn("OctopusApiHandler is null, cannot get device ID");
            return "ERROR: Handler not initialized";
        }

        try {
            String deviceId = localHandler.getSmartMeterDeviceId();

            // Automatically update configuration if requested (default true)
            if (autoConfig == null || autoConfig) {
                localHandler.updateDeviceIdConfiguration(deviceId);
                logger.info("Automatically configured device ID: {}", deviceId);
            }

            return deviceId;
        } catch (Exception e) {
            logger.error("Failed to get smart meter device ID: {}", e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    // Static method for rule DSL
    public static String getSmartMeterDeviceId(@Nullable ThingActions actions, @Nullable Boolean autoConfig) {
        if (actions instanceof OctopusApiActions octopusActions) {
            return octopusActions.getSmartMeterDeviceId(autoConfig);
        } else {
            throw new IllegalArgumentException("Instance is not an OctopusApiActions class.");
        }
    }

    // Overload for backwards compatibility
    public static String getSmartMeterDeviceId(@Nullable ThingActions actions) {
        return getSmartMeterDeviceId(actions, true);
    }
}
