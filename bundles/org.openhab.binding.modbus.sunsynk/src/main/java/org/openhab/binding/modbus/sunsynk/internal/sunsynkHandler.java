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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.modbus.handler.BaseModbusThingHandler;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusConstants;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusReadRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.io.transport.modbus.ModbusWriteRegisterRequestBlueprint;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link sunsynkHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author David Jones - Initial contribution
 */
@NonNullByDefault
public class sunsynkHandler extends BaseModbusThingHandler {

    private static final class ModbusRequest {

        private final Deque<SunsynkInverterRegisters> registers;
        private final ModbusReadRequestBlueprint blueprint;

        public ModbusRequest(Deque<SunsynkInverterRegisters> registers, int slaveId) {
            this.registers = registers;
            this.blueprint = initReadRequest(registers, slaveId);
        }

        private ModbusReadRequestBlueprint initReadRequest(Deque<SunsynkInverterRegisters> registers, int slaveId) {
            int firstRegister = registers.getFirst().getRegisterNumber();
            int lastRegister = registers.getLast().getRegisterNumber();
            int length = lastRegister - firstRegister + registers.getLast().getRegisterCount();

            assert length <= ModbusConstants.MAX_REGISTERS_READ_COUNT;

            return new ModbusReadRequestBlueprint( //
                    slaveId, //
                    ModbusReadFunctionCode.READ_MULTIPLE_REGISTERS, //
                    firstRegister, //
                    length, //
                    TRIES //
            );
        }
    }

    private final Logger logger = LoggerFactory.getLogger(sunsynkHandler.class);

    private static final int TRIES = 3;
    private List<ModbusRequest> modbusRequests = new ArrayList<>();

    public sunsynkHandler(Thing thing) {
        super(thing);
    }

    /**
     * Splits the Sunsynk InverterRegisters into multiple ModbusRequest, to ensure the max request size.
     */
    private List<ModbusRequest> buildRequests() {
        final List<ModbusRequest> requests = new ArrayList<>();
        Deque<SunsynkInverterRegisters> currentRequest = new ArrayDeque<>();
        int currentRequestFirstRegister = 0;

        for (SunsynkInverterRegisters channel : SunsynkInverterRegisters.values()) {

            if (currentRequest.isEmpty()) {
                currentRequest.add(channel);
                currentRequestFirstRegister = channel.getRegisterNumber();
            } else {
                int sizeWithRegisterAdded = (channel.getRegisterNumber2() == -1) ? channel.getRegisterNumber()
                        : channel.getRegisterNumber2() - currentRequestFirstRegister + channel.getRegisterCount();
                if (sizeWithRegisterAdded > ModbusConstants.MAX_REGISTERS_READ_COUNT - 1) {
                    requests.add(new ModbusRequest(currentRequest, getSlaveId()));
                    currentRequest = new ArrayDeque<>();

                    currentRequest.add(channel);
                    currentRequestFirstRegister = channel.getRegisterNumber();
                } else {
                    currentRequest.add(channel);
                }
            }
        }
        if (!currentRequest.isEmpty()) {
            requests.add(new ModbusRequest(currentRequest, getSlaveId()));
        }
        logger.debug("Created {} modbus request templates.", requests.size());
        return requests;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType && !this.modbusRequests.isEmpty()) {
            for (ModbusRequest request : this.modbusRequests) {
                submitOneTimePoll( //
                        request.blueprint, //
                        (AsyncModbusReadResult result) -> this.readSuccessful(request, result), //
                        this::readError //
                );
            }
        } else if (channelUID.getGroupId().equals("ss-settings")) {
            for (SunsynkInverterRegisters channel : SunsynkInverterRegisters.values()) {
                if (channelUID.getIdWithoutGroup().equals("ss-" + channel.getChannelName())) {
                    ModbusRegisterArray regArray = new ModbusRegisterArray(
                            ModbusBitUtilities.commandToRegisters(command, channel.getType()).getBytes());
                    ModbusWriteRegisterRequestBlueprint request = new ModbusWriteRegisterRequestBlueprint(getSlaveId(),
                            channel.getRegisterNumber(), regArray, true, TRIES);
                    submitOneTimeWrite(request, result -> {
                        logger.debug("Modbus Write success - {}", result.getResponse().toString());
                    }, failure -> {
                        logger.debug("Modbus Write fail - {}", failure.getCause().toString());
                    });
                    break;
                }
            }
        }
    }

    @Override
    public void modbusInitialize() {
        final SunsynkInverterConfiguration config = getConfigAs(SunsynkInverterConfiguration.class);

        if (config.pollInterval <= 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid poll interval: " + config.pollInterval);
            return;
        }

        this.updateStatus(ThingStatus.UNKNOWN);
        this.modbusRequests = this.buildRequests();

        for (ModbusRequest request : modbusRequests) {
            registerRegularPoll( //
                    request.blueprint, //
                    config.pollInterval, //
                    0, //
                    (AsyncModbusReadResult result) -> this.readSuccessful(request, result), //
                    this::readError //
            );
        }
    }

    private void readSuccessful(ModbusRequest request, AsyncModbusReadResult result) {
        result.getRegisters().ifPresent(registers -> {
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
            int firstRegister = request.registers.getFirst().getRegisterNumber();

            for (SunsynkInverterRegisters channel : request.registers) {
                int index = channel.getRegisterNumber() - firstRegister;
                if (channel.getRegisterNumber2() != -1) {
                    int index2 = channel.getRegisterNumber2() - firstRegister;
                    int splitRegister1Data = registers.getRegister(index);
                    int splitRegister2Data = registers.getRegister(index2);
                    int combinedData = (splitRegister2Data << 16) + splitRegister1Data;
                    updateState(createChannelUid(channel), channel.createState(createDecimalType(combinedData)));
                } else {
                    ModbusBitUtilities.extractStateFromRegisters(registers, index, channel.getType())
                            .map(channel::createState).ifPresent(v -> updateState(createChannelUid(channel), v));
                }
            }
        });
    }

    private void readError(AsyncModbusFailure<ModbusReadRequestBlueprint> error) {
        this.logger.debug("Failed to get modbus data - {}", error.getCause().getMessage());
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                "Failed to retrieve data: " + error.getCause().getMessage());
    }

    private ChannelUID createChannelUid(SunsynkInverterRegisters register) {
        return new ChannelUID( //
                thing.getUID(), //
                "ss-" + register.getChannelGroup(), //
                "ss-" + register.getChannelName() //
        );
    }

    private DecimalType createDecimalType(int data) {
        return new DecimalType(data);
    }
}
