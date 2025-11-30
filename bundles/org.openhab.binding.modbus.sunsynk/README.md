# Sunsynk Modbus Binding

This binding integrates Sunsynk inverters with openHAB through the Modbus protocol, providing comprehensive monitoring and control capabilities for solar power systems.

**Tested Hardware**: Sunsynk Ecco 5kW inverter

## Supported Things

This binding requires a Modbus bridge (either `modbus:serial` or `modbus:tcp`) to communicate with Sunsynk inverters.

- `sunsynk-inverter`: Sunsynk solar inverter with Modbus support (ThingTypeUID: `modbus:sunsynk-inverter`)

The binding has been tested with the Sunsynk Ecco 5kW model but should work with other Sunsynk inverters that support Modbus communication.

## Discovery

Auto-discovery is not supported. You must manually configure the Modbus bridge and inverter thing.

## Prerequisites

Before configuring this binding, you need:

1. A working Modbus connection to your Sunsynk inverter (RS485 serial or TCP/IP)
2. The Modbus binding installed
3. A configured Modbus bridge (serial or TCP)

## Thing Configuration

### Modbus Bridge Setup

First, configure a Modbus bridge. Example for serial connection:

```
Bridge modbus:serial:sunsynkBridge [ port="/dev/ttyUSB0", baud=9600, dataBits=8, parity="none", stopBits="1.0", encoding="rtu" ] {
    Thing sunsynk-inverter inverter [ slaveAddress=1, pollInterval=5000 ]
}
```

Example for TCP connection:

```
Bridge modbus:tcp:sunsynkBridge [ host="192.168.1.100", port=502, id=1 ] {
    Thing sunsynk-inverter inverter [ slaveAddress=1, pollInterval=5000 ]
}
```

### Inverter Thing Configuration

| Name          | Type    | Description                              | Default | Required |
|---------------|---------|------------------------------------------|---------|----------|
| slaveAddress  | integer | Modbus slave address of the inverter    | 1       | no       |
| pollInterval  | integer | Polling interval in milliseconds         | 5000    | no       |

## Channels

The binding provides over 100 channels organized into functional groups:

### Overview Group (`ss-overview`)
System state, energy totals, temperatures, system time, SD card status

### MPPT Information (`ss-mppt-information`)
Solar PV data for up to 4 MPPT trackers (voltage, current, power)

### Battery Information (`ss-battery-information`)
Battery state of charge, power, voltage, current, charge limits, temperature

### Grid Information (`ss-grid-information`)
Grid voltage, current, frequency, power, import/export totals

### Load Information (`ss-load-information`)
Load consumption, essential and non-essential load power, AUX output

### Inverter Information (`ss-inverter-information`)
Inverter output voltage, current, frequency, power

### Settings - Solar (`ss-settings-solar`)
Writable settings for solar export, load priority, grid charge, generator input

### Settings - Battery (`ss-settings-battery`)
Writable battery voltage thresholds (equalization, absorption, float, shutdown, restart, low capacity), charge/discharge current limits, grid charge current

### Settings - Timer (`ss-settings-timer`)
6 programmable timer slots with configurable time, power, voltage, capacity, SOC, and charge mode

### Settings - Advanced (`ss-settings-advanced`)
Inverter control, max solar/sell power, peak shaving, control mode, BMS protocol selection

### System Time (`ss-system-time`)
Read and write inverter system time (writable)

## Full Example

### Thing Configuration

```
Bridge modbus:serial:sunsynkBridge [ port="/dev/ttyUSB0", baud=9600, dataBits=8, parity="none", stopBits="1.0", encoding="rtu" ] {
    Thing sunsynk-inverter inverter [ slaveAddress=1, pollInterval=5000 ]
}
```

### Item Configuration

```
// Overview
Number:Energy      Sunsynk_Energy_Today         "Solar Energy Today"         { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-overview#ss-total-pv-energy-today" }
Number:Power       Sunsynk_Battery_Power        "Battery Power"              { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-overview#ss-battery-power" }
Number:Temperature Sunsynk_DC_Temperature       "DC Temperature"             { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-overview#ss-dc-temperature" }
DateTime           Sunsynk_System_Time          "Inverter Time"              { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-system-time#ss-system-time" }

// Battery
Number:Dimensionless Sunsynk_Battery_SOC        "Battery SOC"                { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-battery-information#ss-battery-soc" }
Number:ElectricPotential Sunsynk_Battery_Voltage "Battery Voltage"          { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-battery-information#ss-battery-voltage" }
Number:ElectricCurrent Sunsynk_Battery_Current   "Battery Current"          { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-battery-information#ss-battery-current" }

// Solar
Number:Power       Sunsynk_MPPT1_Power          "MPPT1 Power"                { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-mppt-information#ss-mppt1-power" }
Number:ElectricPotential Sunsynk_MPPT1_Voltage  "MPPT1 Voltage"              { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-mppt-information#ss-mppt1-voltage" }

// Grid
Number:Power       Sunsynk_Grid_Power           "Grid Power"                 { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-grid-information#ss-grid-power" }
Number:Frequency   Sunsynk_Grid_Frequency       "Grid Frequency"             { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-grid-information#ss-grid-frequency" }

// Load
Number:Power       Sunsynk_Load_Power           "Load Power"                 { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-load-information#ss-load-power" }

// Writable Settings
Switch             Sunsynk_Grid_Charge          "Grid Charge Enable"         { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-settings-solar#ss-grid-charge" }
Number             Sunsynk_Load_Limit           "Load Limit"                 { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-settings-solar#ss-load-limit" }
Number:ElectricPotential Sunsynk_Battery_Low_Cap "Battery Low Capacity V"  { channel="modbus:sunsynk-inverter:sunsynkBridge:inverter:ss-settings-battery#ss-battery-low-capacity-voltage" }
```

### Sitemap Example

```
sitemap sunsynk label="Solar System" {
    Frame label="Overview" {
        Text item=Sunsynk_Energy_Today
        Text item=Sunsynk_Battery_Power
        Text item=Sunsynk_Battery_SOC
    }
    Frame label="Solar" {
        Text item=Sunsynk_MPPT1_Power
        Text item=Sunsynk_MPPT1_Voltage
    }
    Frame label="Grid" {
        Text item=Sunsynk_Grid_Power
        Text item=Sunsynk_Grid_Frequency
    }
    Frame label="Settings" {
        Switch item=Sunsynk_Grid_Charge
        Slider item=Sunsynk_Load_Limit minValue=0 maxValue=100 step=1
    }
}
```

## Register Information

The binding reads Modbus registers in optimized batches, respecting the maximum register read count. Registers are pre-ordered numerically in the enum for optimal batching performance. Register gaps are handled automatically by splitting requests into multiple read operations.

Key register ranges:
- 16-196: Overview and sensor data (state, power, energy, temperatures, MPPT, battery, grid, load)
- 200-222: Battery settings (voltage thresholds, current limits)
- 230-293: Solar and advanced settings (grid charge, timers, peak shaving)
- 312-325: Battery charging and BMS protocol
- 603, 611: Extended battery information (BAT1 SOC/cycle)

For detailed register mapping, see `SunsynkInverterRegisters.java`.

## Notes

- Timer settings must be sequential (prog1 < prog2 < prog3 < prog4 < prog5 < prog6)
- System time writes use the system default timezone
- Some settings may require specific inverter firmware versions
- Always verify writable settings on a non-production system first

## Support

For issues or questions, please check:
- The openHAB community forum
- This binding's GitHub repository
