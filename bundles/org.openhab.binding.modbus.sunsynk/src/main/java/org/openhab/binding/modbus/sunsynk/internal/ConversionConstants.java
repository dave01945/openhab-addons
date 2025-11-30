/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Constants for converting values.
 *
 * @author Sönke Küper - Initial contribution
 */
@NonNullByDefault
final class ConversionConstants {

    private ConversionConstants() {
    }

    /**
     * Multiplicand for 0.1.
     */
    static final BigDecimal DIV_BY_TEN = new BigDecimal(BigInteger.ONE, 1);
    static final BigDecimal DIV_BY_HUNDRED = new BigDecimal(BigInteger.ONE, 2);
    static final BigDecimal DIV_BY_THOU = new BigDecimal(BigInteger.ONE, 3);

    /**
     * Value conversion from Celsius to Kelvin.
     */
    static final Function<BigDecimal, BigDecimal> CELSIUS_TO_KELVIN = (BigDecimal celsius) -> celsius
            .add(new BigDecimal(273.15f));

    static final Function<BigDecimal, BigDecimal> DIV_BY_TEMP_TEN = (BigDecimal temp) -> temp
            .subtract(new BigDecimal(1000)).divide(new BigDecimal(10)).add(new BigDecimal(273.15f));

    static final Function<BigDecimal, BigDecimal> MASK_1C = (BigDecimal mask1c) -> {
        BigDecimal out = new BigDecimal(mask1c.toBigInteger().and(new BigInteger("1c", 16)));
        return out;
    };

    static final Function<BigDecimal, BigDecimal> MASK_03 = (BigDecimal mask03) -> {
        BigDecimal out = new BigDecimal(mask03.toBigInteger().and(new BigInteger("3", 16)));
        return out;
    };

    static final Function<BigDecimal, BigDecimal> MASK_01 = (BigDecimal mask01) -> {
        BigDecimal out = new BigDecimal(mask01.toBigInteger().and(new BigInteger("1", 16)));
        return out;
    };

    static final Function<BigDecimal, BigDecimal> TIME = (BigDecimal time) -> {
        String number = String.valueOf(time.intValue());
        char[] digits = number.toCharArray();
        int minutes = 0;
        int hours = 0;
        if (digits.length == 4) {
            hours = (digits[0] - 48) * 10;
            hours += digits[1] - 48;
            minutes = (digits[2] - 48) * 10;
            minutes += digits[3] - 48;
        } else if (digits.length == 3) {
            hours = digits[0] - 48;
            minutes = (digits[1] - 48) * 10;
            minutes += digits[2] - 48;
        } else if (digits.length == 2) {
            hours = 0;
            minutes = (digits[0] - 48) * 10;
            minutes += digits[1] - 48;
        } else if (digits.length == 1) {
            hours = 0;
            minutes = digits[0] - 48;
        } else {
            hours = 12;
            minutes = 34;
        }
        LocalDateTime dateTime = LocalDate.now().atTime(hours, minutes);
        if (dateTime.isBefore(LocalDateTime.now())) {
            dateTime = dateTime.plusDays(1);
        }

        return new BigDecimal(dateTime.toEpochSecond(ZoneOffset.UTC));
    };

    /**
     * System time conversion from 3 registers (22, 23, 24).
     * Register format: [0]=year/month, [1]=day/hour, [2]=minute/second
     * Year: (reg[0] >> 8) + 2000, Month: reg[0] & 0xFF
     * Day: (reg[1] >> 8), Hour: reg[1] & 0xFF
     * Minute: (reg[2] >> 8), Second: reg[2] & 0xFF
     */
    static final Function<BigDecimal[], LocalDateTime> SYSTEM_TIME = (BigDecimal[] regs) -> {
        if (regs.length != 3) {
            throw new IllegalArgumentException("SYSTEM_TIME requires exactly 3 registers");
        }
        int reg0 = regs[0].intValue();
        int reg1 = regs[1].intValue();
        int reg2 = regs[2].intValue();

        int year = ((reg0 >> 8) & 0xFF) + 2000;
        int month = reg0 & 0xFF;
        int day = (reg1 >> 8) & 0xFF;
        int hour = reg1 & 0xFF;
        int minute = (reg2 >> 8) & 0xFF;
        int second = reg2 & 0xFF;

        return LocalDateTime.of(year, month, day, hour, minute, second);
    };
}
