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

/**
 * The {@link OctopusApiConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author <David Jones> - Initial contribution
 */
@NonNullByDefault
public class OctopusApiConfiguration {

    public int refreshInterval = 12;
    public int liveRefreshInterval = 30;
    public String apiKey = "";
    public String accountNumber = "";
    public String agileProductCode = "";
    public String agileExportProductCode = "";
    public String agileRegion = "";
}
