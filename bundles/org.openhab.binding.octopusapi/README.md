# Octopus API Binding

This binding integrates with the [Octopus Energy](https://octopus.energy) GraphQL API for UK customers.
It provides access to electricity and gas consumption data, export readings (for solar/battery systems), and tariff rates including Agile pricing.
The binding also supports real-time smart meter telemetry for users with an Octopus Home Mini device.

All consumption, export, and tariff data is provided as TimeSeries, allowing OpenHAB to track historical data and display it in charts.

## Supported Things

This binding provides a single thing type:

- `octopus` - Octopus Energy account thing (ThingTypeUID: `octopusapi:octopus`)

## Discovery

This binding does not support automatic discovery.
You must manually configure the thing with your Octopus Energy account credentials.

## Prerequisites

Before using this binding, you need:

1. An active Octopus Energy account (UK)
2. Your API key (obtain from your [Octopus Energy dashboard](https://octopus.energy/dashboard/developer/))
3. Your account number (found in your Octopus Energy account dashboard)
4. (Optional) Your smart meter device ID if you have an Octopus Home Mini for real-time data

## Thing Configuration

The binding requires the following configuration parameters:

| Name                    | Type    | Description                                                                                  | Default | Required |
|-------------------------|---------|----------------------------------------------------------------------------------------------|---------|----------|
| apiKey                  | text    | Your Octopus Energy API key                                                                  | N/A     | yes      |
| accountNumber           | text    | Your Octopus Energy account number                                                           | N/A     | yes      |
| refreshInterval         | integer | Interval between data refreshes in hours (1-48)                                              | 12      | yes      |
| liveRefreshInterval     | integer | Interval between live telemetry updates in seconds (10-300). Minimum 30s recommended by Octopus | 30      | yes      |
| deviceId                | text    | Smart meter device GUID for Octopus Home Mini (format: AA-BB-CC-DD-EE-FF-GG-HH)            | N/A     | no       |

**Note**: The `deviceId` parameter is only needed if you have an Octopus Home Mini and want to receive real-time power demand and meter readings.
Leave this blank if you don't have a Home Mini device.

## Channels

The binding provides the following channels:

| Channel ID        | Type            | Description                                                                                    |
|-------------------|-----------------|------------------------------------------------------------------------------------------------|
| consumption       | Number:Energy   | Half-hourly electricity consumption readings (kWh)                                             |
| export            | Number:Energy   | Half-hourly electricity export readings for solar/battery systems (kWh)                        |
| currentRates      | Number          | Current electricity tariff rates including VAT (GBP per kWh) - stored as dimensionless values |
| currentExRates    | Number          | Current electricity tariff rates excluding VAT (GBP per kWh) - stored as dimensionless values |
| gasConsumption    | Number:Energy   | Half-hourly gas consumption readings (kWh)                                                     |
| gasRates          | Number          | Current gas tariff rates including VAT (GBP per kWh) - stored as dimensionless values         |
| gasExRates        | Number          | Current gas tariff rates excluding VAT (GBP per kWh) - stored as dimensionless values         |
| liveDemand        | Number:Power    | Real-time power demand (W) - requires Octopus Home Mini                                        |
| liveMeterReading  | Number:Energy   | Real-time cumulative meter reading (kWh) - requires Octopus Home Mini                          |

**Important Notes:**

- All consumption and rate data is provided as TimeSeries for historical tracking
- Energy prices are stored as plain numbers (dimensionless) because OpenHAB doesn't support GBP/kWh units natively
- The `consumption` and `export` channels provide data in 30-minute intervals
- Live channels (`liveDemand` and `liveMeterReading`) only work with an Octopus Home Mini device
- The binding automatically handles authentication token management and renewal

## Full Example

### Thing Configuration

```java
Thing octopusapi:octopus:myaccount "Octopus Energy" [
    apiKey="sk_live_xxxxxxxxxxxxxxxxxxxx",
    accountNumber="A-12345678",
    refreshInterval=12,
    liveRefreshInterval=30,
    deviceId="AA-BB-CC-DD-EE-FF-GG-HH"
]
```

For users without an Octopus Home Mini (no real-time data):

```java
Thing octopusapi:octopus:myaccount "Octopus Energy" [
    apiKey="sk_live_xxxxxxxxxxxxxxxxxxxx",
    accountNumber="A-12345678",
    refreshInterval=12
]
```

### Item Configuration

```java
// Electricity consumption and export
Number:Energy ElectricityConsumption "Consumption [%.2f kWh]" { channel="octopusapi:octopus:myaccount:consumption" }
Number:Energy ElectricityExport "Export [%.2f kWh]" { channel="octopusapi:octopus:myaccount:export" }

// Tariff rates (dimensionless - values in GBP per kWh)
Number ElectricityRate "Current Rate [%.4f £/kWh]" { channel="octopusapi:octopus:myaccount:currentRates" }
Number ElectricityRateExVAT "Rate Ex-VAT [%.4f £/kWh]" { channel="octopusapi:octopus:myaccount:currentExRates" }

// Gas consumption and rates
Number:Energy GasConsumption "Gas Consumption [%.2f kWh]" { channel="octopusapi:octopus:myaccount:gasConsumption" }
Number GasRate "Gas Rate [%.4f £/kWh]" { channel="octopusapi:octopus:myaccount:gasRates" }
Number GasRateExVAT "Gas Rate Ex-VAT [%.4f £/kWh]" { channel="octopusapi:octopus:myaccount:gasExRates" }

// Real-time data (requires Octopus Home Mini)
Number:Power LiveDemand "Current Demand [%.0f W]" { channel="octopusapi:octopus:myaccount:liveDemand" }
Number:Energy LiveMeterReading "Meter Reading [%.3f kWh]" { channel="octopusapi:octopus:myaccount:liveMeterReading" }
```

### Sitemap Configuration

```perl
sitemap octopus label="Octopus Energy" {
    Frame label="Electricity" {
        Text item=ElectricityConsumption
        Text item=ElectricityExport
        Text item=ElectricityRate
        Text item=ElectricityRateExVAT
        Chart item=ElectricityConsumption period=D refresh=3600
        Chart item=ElectricityRate period=D refresh=3600
    }
    
    Frame label="Gas" {
        Text item=GasConsumption
        Text item=GasRate
        Chart item=GasConsumption period=D refresh=3600
    }
    
    Frame label="Live (Home Mini)" {
        Text item=LiveDemand
        Text item=LiveMeterReading
        Chart item=LiveDemand period=h refresh=60
    }
}
```

## Agile Tariff Support

If you're on an Octopus Agile tariff, the `currentRates` channel will provide half-hourly pricing updates.
This is ideal for automation rules that take advantage of cheaper electricity during low-demand periods.

Example rule to turn on a device during cheap rate periods:

```java
rule "Charge battery during cheap rates"
when
    Item ElectricityRate changed
then
    if (ElectricityRate.state < 0.10) {  // Less than 10p per kWh
        // Turn on your device
        logInfo("octopus", "Cheap rate detected: " + ElectricityRate.state + " £/kWh")
    }
end
```

## Rate Limiting

The Octopus Energy API has rate limits.
This binding is designed to respect these limits by:

- Caching authentication tokens (60-minute lifetime, refreshed at 55 minutes)
- Using configurable refresh intervals (minimum 1 hour for historical data)
- Recommending minimum 30 seconds for live telemetry updates

If you encounter rate limiting errors (HTTP 429), consider increasing your refresh intervals.

## Troubleshooting

### Authentication Issues

If the binding shows as OFFLINE with authentication errors:

1. Verify your API key is correct (obtain a new one from your Octopus dashboard if needed)
2. Check your account number matches exactly (including the "A-" prefix)
3. Ensure your API key has not been revoked

### No Live Data

If live channels (`liveDemand` and `liveMeterReading`) show no data:

1. Verify you have an Octopus Home Mini device installed
2. Check the `deviceId` format is correct (AA-BB-CC-DD-EE-FF-GG-HH)
3. Ensure your Home Mini is online and reporting to Octopus

### Check Logs

Enable debug logging for detailed API communication:

```
log:set DEBUG org.openhab.binding.octopusapi
```

Then check the logs for GraphQL requests/responses and error messages.
