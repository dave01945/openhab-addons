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
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OctopusApiActions} provides rule actions for the Octopus Energy binding.
 *
 * @author David Jones - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = OctopusApiActions.class)
@ThingActionsScope(name = "octopusapi")
@NonNullByDefault
public class OctopusApiActions implements ThingActions {

    private final Logger logger = LoggerFactory.getLogger(OctopusApiActions.class);

    private @Nullable OctopusApiHandler handler;

    @RuleAction(label = "@text/action.refresh-agile-rates.label", description = "@text/action.refresh-agile-rates.description")
    public void refreshAgileRates() {
        OctopusApiHandler localHandler = handler;
        if (localHandler != null) {
            logger.debug("Rule action: refreshAgileRates triggered");
            localHandler.updateAllDailyRates();
        }
    }

    public static void refreshAgileRates(ThingActions actions) {
        ((OctopusApiActions) actions).refreshAgileRates();
    }

    @RuleAction(label = "@text/action.refresh-account-data.label", description = "@text/action.refresh-account-data.description")
    public void refreshAccountData() {
        OctopusApiHandler localHandler = handler;
        if (localHandler != null) {
            logger.debug("Rule action: refreshAccountData triggered");
            localHandler.refreshAccountAndConsumption();
        }
    }

    public static void refreshAccountData(ThingActions actions) {
        ((OctopusApiActions) actions).refreshAccountData();
    }

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof OctopusApiHandler octopusHandler) {
            this.handler = octopusHandler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }
}
