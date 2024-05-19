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
package org.openhab.binding.modbus.sunsynk.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.modbus.ModbusBindingConstants;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link ModbussunsynkBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author David Jones - Initial contribution
 */
@NonNullByDefault
public class ModbussunsynkBindingConstants {

    public static final ThingTypeUID THING_TYPE_SUNSYNK_INVERTER = new ThingTypeUID(ModbusBindingConstants.BINDING_ID,
            "sunsynk-inverter");
}
