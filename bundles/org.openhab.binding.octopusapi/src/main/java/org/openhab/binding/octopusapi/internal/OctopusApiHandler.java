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

import static org.openhab.binding.octopusapi.internal.OctopusApiBindingConstants.*;

import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OctopusApiHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author <David Jones> - Initial contribution
 */
@NonNullByDefault
public class OctopusApiHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OctopusApiHandler.class);

    private @Nullable OctopusApiConfiguration config;

    public OctopusApiHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_1.equals(channelUID.getId())) {
            if (command instanceof RefreshType) {
                // TODO: handle data refresh
            }
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(OctopusApiConfiguration.class);

        updateStatus(ThingStatus.UNKNOWN);

        if (config.hostname.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Parameter hostname must not be empty!");
            return;
        }

        // check protocol is set
        if (!config.hostname.startsWith("http://") && !config.hostname.startsWith("https://")) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Hostname is invalid: protocol not defined.");
            return;
        }

        scheduler.scheduleWithFixedDelay(this::pollTask, 0, config.refreshInterval, TimeUnit.SECONDS);
    }

    private void pollTask() {
        logger.debug("Test");
    }
}
