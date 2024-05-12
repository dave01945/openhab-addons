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

import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.INT16;
import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.INT32_SWAP;
import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.UINT16;
import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.UINT32_SWAP;

import java.math.BigDecimal;
import java.util.function.Function;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;

/**
 * The {@link SunsynkInverterRegisters} is responsible for defining Modbus registers and their units.
 *
 * @author Sönke Küper - Initial contribution
 * @author David Jones - Base copied from sungrow
 */
@NonNullByDefault
public enum SunsynkInverterRegisters {

    // the following register numbers are 1-based. They need to be converted before sending them on the wire.

    INTERNAL_DC_TEMPERATURE(90, INT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KELVIN), "overview"),
    INTERNAL_AC_TEMPERATURE(91, INT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KELVIN), "overview"),
    EXTERNAL_TEMPERATURE(95, INT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KELVIN), "overview"),

    MPPT1_VOLTAGE(109, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.VOLT), "mppt-information"),
    MPPT1_CURRENT(110, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.AMPERE), "mppt-information"),
    MPPT1_POWER(187, UINT16, BigDecimal.ONE, quantityFactory(Units.WATT), "mppt-information"),
    MPPT2_VOLTAGE(111, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.VOLT), "mppt-information"),
    MPPT2_CURRENT(112, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.AMPERE), "mppt-information"),
    MPPT2_POWER(187, UINT16, BigDecimal.ONE, quantityFactory(Units.WATT), "mppt-information"),

    GRID_VOLTAGE(150, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.VOLT), "overview"),
    GRID_POWER(169, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "overview"),
    POWER_FACTOR(93, INT16, ConversionConstants.DIV_BY_THOU, quantityFactory(Units.VAR), "overview"),
    GRID_FREQUENCY(79, UINT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.HERTZ), "overview"),

    DAILY_PV_GENERATION(108, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR), "overview"),
    TOTAL_PV_GENERATION(96, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "overview"),
    DAILY_GRID_EXPORT_ENERGY(77, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information"),
    TOTAL_GRID_EXPORT_ENERGY(81, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information"),
    LOAD_POWER(178, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "load-information"),
    DAILY_BATTERY_CHARGE(70, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "battery-information"),
    TOTAL_BATTERY_CHARGE(72, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "battery-information"),

    DAILY_LOAD_ENERGY_CONSUMPTION(84, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "load-information"),
    TOTAL_LOAD_ENERGY_CONSUMPTION(85, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "load-information"),
    BATTERY_VOLTAGE(183, UINT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.VOLT),
            "battery-information"),
    BATTERY_CURRENT(191, INT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.AMPERE),
            "battery-information"),
    BATTERY_POWER(190, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "battery-information"),
    BATTERY_SOC(184, UINT16, BigDecimal.ONE, quantityFactory(Units.PERCENT), "battery-information"),
    BATTERY_TEMPERATURE(182, INT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KELVIN),
            ConversionConstants.CELSIUS_TO_KELVIN, "battery-information"),
    DAILY_BATTERY_DISCHARGE_ENERGY(71, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "battery-information"),
    TOTAL_BATTERY_DISCHARGE_ENERGY(74, UINT32_SWAP, ConversionConstants.DIV_BY_TEN,
            quantityFactory(Units.KILOWATT_HOUR), "battery-information"),

    GRID_STATE(194, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), "grid-information"),

    GRID_CURRENT(160, INT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.AMPERE), "overview"),
    TOTAL_ACTIVE_POWER(63, INT32_SWAP, BigDecimal.ONE, quantityFactory(Units.WATT), "overview"),
    DAILY_IMPORT_ENERGY(76, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information"),
    TOTAL_IMPORT_ENERGY(78, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information");

    private final BigDecimal multiplier;
    private final int registerNumber;
    private final ValueType type;

    private final Function<BigDecimal, BigDecimal> conversion;
    private final Function<BigDecimal, State> stateFactory;
    private final String channelGroup;

    SunsynkInverterRegisters(int registerNumber, ValueType type, BigDecimal multiplier,
            Function<BigDecimal, State> stateFactory, Function<BigDecimal, BigDecimal> conversion,
            String channelGroup) {
        this.multiplier = multiplier;
        this.registerNumber = registerNumber;
        this.type = type;
        this.conversion = conversion;
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
    }

    SunsynkInverterRegisters(int registerNumber, ValueType type, BigDecimal multiplier,
            Function<BigDecimal, State> stateFactory, String channelGroup) {
        this.multiplier = multiplier;
        this.registerNumber = registerNumber;
        this.type = type;
        this.conversion = Function.identity();
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
    }

    /**
     * Creates a Function that creates {@link QuantityType} states with the given {@link Unit}.
     *
     * @param unit {@link Unit} to be used for the value.
     * @return Function for value creation.
     */
    private static Function<BigDecimal, State> quantityFactory(Unit<?> unit) {
        return (BigDecimal value) -> new QuantityType<>(value, unit);
    }

    /**
     * Returns the modbus register number.
     *
     * @return modbus register number.
     */
    public int getRegisterNumber() {
        return registerNumber;
    }

    /**
     * Returns the {@link ValueType} for the channel.
     * 
     * @return {@link ValueType} for the channel.
     */
    public ValueType getType() {
        return type;
    }

    /**
     * Returns the count of registers read to return the value of this register.
     * 
     * @return register count.
     */
    public int getRegisterCount() {
        return this.type.getBits() / 16;
    }

    /**
     * Returns the channel group.
     * 
     * @return channel group id.
     */
    public String getChannelGroup() {
        return channelGroup;
    }

    /**
     * Returns the channel name.
     * 
     * @return the channel name.
     */
    public String getChannelName() {
        return this.name().toLowerCase().replace('_', '-');
    }

    /**
     * Creates the {@link State} for the given register value.
     *
     * @param registerValue the value for the channel.
     * @return {@link State] for the given value.
     */
    public State createState(DecimalType registerValue) {
        final BigDecimal scaledValue = registerValue.toBigDecimal().multiply(this.multiplier);

        final BigDecimal convertedValue = conversion.apply(scaledValue);
        return this.stateFactory.apply(convertedValue);
    }
}
