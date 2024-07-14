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
}
