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
import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.UINT64_SWAP;

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
 * @author David Jones - Intial contribution
 */
@NonNullByDefault
public enum SunsynkInverterRegisters {

    RATED_POWER(16, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.WATT), "overview"),
    DAILY_ACTIVE_ENERGY(60, INT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR), "overview"),
    DAILY_REACTIVE_ENERGY(61, INT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR), "overview"),
    DAILY_GRID_WORK_TIME(62, UINT16, BigDecimal.ONE, quantityFactory(Units.SECOND), "grid-information"),
    TOTAL_ACTIVE_ENERGY(63, INT32_SWAP, BigDecimal.ONE, quantityFactory(Units.KILOWATT_HOUR), "overview"),
    MONTHLY_PV_ENERGY(65, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "mppt-information"),
    MONTHLY_LOAD_ENERGY(66, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "load-information"),
    MONTHLY_GRID_ENERGY(67, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR), "overview"),
    YEAR_PV_ENERGY(68, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "mppt-information"),
    DAILY_BATTERY_CHARGE(70, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "battery-information"),
    DAILY_BATTERY_DISCHARGE_ENERGY(71, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "battery-information"),
    TOTAL_BATTERY_CHARGE(72, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "battery-information"),
    TOTAL_BATTERY_DISCHARGE_ENERGY(74, UINT32_SWAP, ConversionConstants.DIV_BY_TEN,
            quantityFactory(Units.KILOWATT_HOUR), "battery-information"),
    DAILY_IMPORT_ENERGY(76, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information"),
    DAILY_GRID_EXPORT_ENERGY(77, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information"),
    TOTAL_IMPORT_ENERGY(78, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information"),
    GRID_FREQUENCY(79, UINT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.HERTZ), "grid-information"),
    TOTAL_GRID_EXPORT_ENERGY(81, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information"),
    DAILY_LOAD_ENERGY_CONSUMPTION(84, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "load-information"),
    TOTAL_LOAD_ENERGY_CONSUMPTION(85, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "load-information"),
    YEAR_LOAD_ENERGY(87, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "load-information"),
    INTERNAL_DC_TEMPERATURE(90, UINT16, BigDecimal.ONE, quantityFactory(Units.KELVIN),
            ConversionConstants.DIV_BY_TEMP_TEN, "overview"),
    INTERNAL_AC_TEMPERATURE(91, UINT16, BigDecimal.ONE, quantityFactory(Units.KELVIN),
            ConversionConstants.DIV_BY_TEMP_TEN, "overview"),
    EXTERNAL_TEMPERATURE(95, UINT16, BigDecimal.ONE, quantityFactory(Units.KELVIN), ConversionConstants.DIV_BY_TEMP_TEN,
            "overview"),
    TOTAL_PV_GENERATION(96, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "mppt-information"),
    YEAR_GRID_EXPORT_ENERGY(98, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information"),
    WARNING_MESSAGE(101, UINT64_SWAP, BigDecimal.ONE, quantityFactory(Units.AMPERE), "overview"),
    FAULT_MESSAGE(103, UINT64_SWAP, BigDecimal.ONE, quantityFactory(Units.AMPERE), "overview"),
    BATTERY_CORRECTED_AH(107, UINT16, BigDecimal.ONE, quantityFactory(Units.AMPERE_HOUR), "battery-information"),
    DAILY_PV_GENERATION(108, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "mppt-information"),
    MPPT1_VOLTAGE(109, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.VOLT), "mppt-information"),
    MPPT1_CURRENT(110, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.AMPERE), "mppt-information"),
    MPPT2_VOLTAGE(111, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.VOLT), "mppt-information"),
    MPPT2_CURRENT(112, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.AMPERE), "mppt-information"),
    GRID_VOLTAGE(150, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.VOLT), "grid-information"),
    INVERTER_VOLTAGE(154, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.VOLT), "inverter"),
    GRID_CURRENT(160, INT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.AMPERE), "overview"),
    INVERTER_CURRENT(164, UINT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.AMPERE), "inverter"),
    GRID_POWER(169, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "grid-information"),
    GRID_CT_POWER(172, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "overview"),
    INVERTER_POWER(175, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "inverter"),
    LOAD_POWER_1(176, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "load-information"),
    LOAD_POWER_2(177, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "load-information"),
    LOAD_POWER(178, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "load-information"),
    BATTERY_TEMPERATURE(182, UINT16, BigDecimal.ONE, quantityFactory(Units.KELVIN), ConversionConstants.DIV_BY_TEMP_TEN,
            "battery-information"),
    BATTERY_VOLTAGE(183, UINT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.VOLT),
            "battery-information"),
    BATTERY_SOC(184, UINT16, BigDecimal.ONE, quantityFactory(Units.PERCENT), "battery-information"),
    MPPT1_POWER(186, UINT16, BigDecimal.ONE, quantityFactory(Units.WATT), "mppt-information"),
    MPPT2_POWER(187, UINT16, BigDecimal.ONE, quantityFactory(Units.WATT), "mppt-information"),
    BATTERY_POWER(190, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "battery-information"),
    BATTERY_CURRENT(191, INT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.AMPERE),
            "battery-information"),
    INVERTER_FREQUENCY(193, UINT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.HERTZ), "inverter"),
    GRID_STATE(194, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), "grid-information"),
    BATTERY_CHARGE_LIMIT(314, INT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.AMPERE),
            "battery-information"),
    BATTERY_DISCHARGE_LIMIT(315, INT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.AMPERE),
            "battery-information");

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
