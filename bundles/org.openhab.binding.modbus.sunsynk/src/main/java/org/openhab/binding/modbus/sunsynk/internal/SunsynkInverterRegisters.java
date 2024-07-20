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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.function.Function;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.library.types.DateTimeType;
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
    INVERTER_STATE(59, UINT16, BigDecimal.ONE, quantityFactory(Units.KILOWATT_HOUR), "overview"),
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
    DAILY_GRID_IMPORT_ENERGY(76, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information"),
    DAILY_GRID_EXPORT_ENERGY(77, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
            "grid-information"),
    TOTAL_GRID_IMPORT_ENERGY(78, 80, UINT32_SWAP, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.KILOWATT_HOUR),
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
    INVERTER_VOLTAGE(154, UINT16, ConversionConstants.DIV_BY_TEN, quantityFactory(Units.VOLT), "inverter-information"),
    GRID_CURRENT(160, UINT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.AMPERE), "grid-information"),
    INVERTER_CURRENT(164, UINT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.AMPERE),
            "inverter-information"),
    AUX_LOAD_POWER(166, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "load-information"),
    GRID_L1_POWER(167, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "grid-information"),
    GRID_L2_POWER(168, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "grid-information"),
    GRID_POWER(169, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "grid-information"),
    GRID_CT_POWER(172, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "grid-information"),
    INVERTER_POWER(175, INT16, BigDecimal.ONE, quantityFactory(Units.WATT), "inverter-information"),
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
    INVERTER_FREQUENCY(193, UINT16, ConversionConstants.DIV_BY_HUNDRED, quantityFactory(Units.HERTZ),
            "inverter-information"),
    GRID_STATE(194, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), "grid-information"),

    SETTING_BATT_LOW(219, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), "settings-solar"),
    SETTING_GRID_CHARGE(232, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), "settings-solar"),
    SETTING_GEN_INPUT(235, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), "settings-solar"),
    SETTING_LOAD_PRIORITY(243, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), "settings-solar"),
    SETTING_LOAD_LIMIT(244, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), "settings-solar"),
    SETTING_SOLAR_EXPORT(247, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), "settings-solar"),

    SETTING_USE_TIMER(248, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_01,
            "settings-timer"),
    SETTING_PROG1_TIME(250, UINT16, ConversionConstants.TIME, "settings-timer"),
    SETTING_PROG2_TIME(251, UINT16, ConversionConstants.TIME, "settings-timer"),
    SETTING_PROG3_TIME(252, UINT16, ConversionConstants.TIME, "settings-timer"),
    SETTING_PROG4_TIME(253, UINT16, ConversionConstants.TIME, "settings-timer"),
    SETTING_PROG5_TIME(254, UINT16, ConversionConstants.TIME, "settings-timer"),
    SETTING_PROG6_TIME(255, UINT16, ConversionConstants.TIME, "settings-timer"),

    SETTING_PROG1_POWER(256, UINT16, BigDecimal.ONE, quantityFactory(Units.WATT), "settings-timer"),
    SETTING_PROG2_POWER(257, UINT16, BigDecimal.ONE, quantityFactory(Units.WATT), "settings-timer"),
    SETTING_PROG3_POWER(258, UINT16, BigDecimal.ONE, quantityFactory(Units.WATT), "settings-timer"),
    SETTING_PROG4_POWER(259, UINT16, BigDecimal.ONE, quantityFactory(Units.WATT), "settings-timer"),
    SETTING_PROG5_POWER(260, UINT16, BigDecimal.ONE, quantityFactory(Units.WATT), "settings-timer"),
    SETTING_PROG6_POWER(261, UINT16, BigDecimal.ONE, quantityFactory(Units.WATT), "settings-timer"),

    SETTING_PROG1_CAP(268, UINT16, BigDecimal.ONE, quantityFactory(Units.PERCENT), "settings-timer"),
    SETTING_PROG2_CAP(269, UINT16, BigDecimal.ONE, quantityFactory(Units.PERCENT), "settings-timer"),
    SETTING_PROG3_CAP(270, UINT16, BigDecimal.ONE, quantityFactory(Units.PERCENT), "settings-timer"),
    SETTING_PROG4_CAP(271, UINT16, BigDecimal.ONE, quantityFactory(Units.PERCENT), "settings-timer"),
    SETTING_PROG5_CAP(272, UINT16, BigDecimal.ONE, quantityFactory(Units.PERCENT), "settings-timer"),
    SETTING_PROG6_CAP(273, UINT16, BigDecimal.ONE, quantityFactory(Units.PERCENT), "settings-timer"),

    SETTING_PROG1_CHARGE(274, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_03,
            "settings-timer"),
    SETTING_PROG1_MODE(274, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_1C,
            "settings-timer"),
    SETTING_PROG2_CHARGE(275, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_03,
            "settings-timer"),
    SETTING_PROG2_MODE(275, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_1C,
            "settings-timer"),
    SETTING_PROG3_CHARGE(276, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_03,
            "settings-timer"),
    SETTING_PROG3_MODE(276, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_1C,
            "settings-timer"),
    SETTING_PROG4_CHARGE(277, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_03,
            "settings-timer"),
    SETTING_PROG4_MODE(277, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_1C,
            "settings-timer"),
    SETTING_PROG5_CHARGE(278, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_03,
            "settings-timer"),
    SETTING_PROG5_MODE(278, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_1C,
            "settings-timer"),
    SETTING_PROG6_CHARGE(279, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_03,
            "settings-timer"),
    SETTING_PROG6_MODE(279, UINT16, BigDecimal.ONE, quantityFactory(Units.ONE), ConversionConstants.MASK_1C,
            "settings-timer"),

    BATTERY_CHARGE_LIMIT(314, INT16, BigDecimal.ONE, quantityFactory(Units.AMPERE), "battery-information"),
    BATTERY_DISCHARGE_LIMIT(315, INT16, BigDecimal.ONE, quantityFactory(Units.AMPERE), "battery-information");

    private final BigDecimal multiplier;
    private final int registerNumber;
    private final int registerNumber2;
    private final ValueType type;
    private final boolean hasReg2;

    private final Function<BigDecimal, BigDecimal> conversion;
    private final Function<BigDecimal, State> stateFactory;
    private final boolean dateType;
    private final String channelGroup;

    SunsynkInverterRegisters(int registerNumber, ValueType type, BigDecimal multiplier,
            Function<BigDecimal, State> stateFactory, Function<BigDecimal, BigDecimal> conversion,
            String channelGroup) {
        this.multiplier = multiplier;
        this.registerNumber = registerNumber;
        this.registerNumber2 = -1;
        this.type = type;
        this.conversion = conversion;
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
        this.hasReg2 = false;
        this.dateType = false;
    }

    SunsynkInverterRegisters(int registerNumber, ValueType type, BigDecimal multiplier,
            Function<BigDecimal, State> stateFactory, String channelGroup) {
        this.multiplier = multiplier;
        this.registerNumber = registerNumber;
        this.registerNumber2 = -1;
        this.type = type;
        this.conversion = Function.identity();
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
        this.hasReg2 = false;
        this.dateType = false;
    }

    SunsynkInverterRegisters(int registerNumber, int registerNumber2, ValueType type, BigDecimal multiplier,
            Function<BigDecimal, State> stateFactory, Function<BigDecimal, BigDecimal> conversion,
            String channelGroup) {
        this.multiplier = multiplier;
        this.registerNumber = registerNumber;
        this.registerNumber2 = registerNumber2;
        this.type = type;
        this.conversion = conversion;
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
        this.hasReg2 = true;
        this.dateType = false;
    }

    SunsynkInverterRegisters(int registerNumber, int registerNumber2, ValueType type, BigDecimal multiplier,
            Function<BigDecimal, State> stateFactory, String channelGroup) {
        this.multiplier = multiplier;
        this.registerNumber = registerNumber;
        this.registerNumber2 = registerNumber2;
        this.type = type;
        this.conversion = Function.identity();
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
        this.hasReg2 = true;
        this.dateType = false;
    }

    SunsynkInverterRegisters(int registerNumber, ValueType type, Function<BigDecimal, BigDecimal> conversion,
            String channelGroup) {
        this.multiplier = BigDecimal.ONE;
        this.registerNumber = registerNumber;
        this.registerNumber2 = -1;
        this.type = type;
        this.conversion = conversion;
        this.stateFactory = quantityFactory(Units.ONE);
        this.channelGroup = channelGroup;
        this.hasReg2 = false;
        this.dateType = true;
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
     * Returns the modbus register2 number.
     *
     * @return modbus register2 number.
     */
    public int getRegisterNumber2() {
        return registerNumber2;
    }

    /**
     * Returns true if the channel has register 2.
     *
     * @return modbus register 2 boolean.
     */
    public boolean hasRegisterNumber2() {
        return hasReg2;
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
        BigDecimal value = new BigDecimal(registerValue.intValue());
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(conversion.apply(value).longValue(), 0, ZoneOffset.UTC);
        ZonedDateTime zDate = ZonedDateTime.of(dateTime, ZoneId.systemDefault());

        if (this.dateType) {
            DateTimeType dateTimeType = new DateTimeType(zDate);
            return dateTimeType;
        } else {
            final BigDecimal scaledValue = registerValue.toBigDecimal().multiply(this.multiplier);
            final BigDecimal convertedValue = conversion.apply(scaledValue);
            return this.stateFactory.apply(convertedValue);
        }
    }
}
