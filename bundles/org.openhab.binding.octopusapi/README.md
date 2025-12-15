# Octopus API Binding

This binding integrates with the [Octopus Energy](https://octopus.energy) GraphQL API for UK customers.
It provides access to:

- **Electricity consumption** - Half-hourly import data (7 days)
- **Electricity export** - Half-hourly export data for solar/battery systems (7 days)
- **Gas consumption** - Half-hourly gas usage data (3 days)
- **Tariff rates** - Current and historical electricity/gas rates (including Agile pricing)
- **Account information** - Balance, tariff details, and standing charges
- **Real-time telemetry** - Live power demand and meter readings (requires Octopus Home Mini)

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
3. Your account number (found in your Octopus Energy account dashboard - format: A-12345678)
4. (Optional) An Octopus Home Mini device for real-time power monitoring (device ID auto-discovered)

## Thing Configuration

The binding requires the following configuration parameters:

| Name                    | Type    | Description                                                                                  | Default | Required |
|-------------------------|---------|----------------------------------------------------------------------------------------------|---------|----------|
| apiKey                  | text    | Your Octopus Energy API key                                                                  | N/A     | yes      |
| accountNumber           | text    | Your Octopus Energy account number                                                           | N/A     | yes      |
| refreshInterval         | integer | Interval between data refreshes in hours (1-48)                                              | 12      | yes      |
| liveRefreshInterval     | integer | Interval between live telemetry updates in seconds (60-300). Minimum 60s to avoid rate limiting | 60      | yes      |

**Note**: All account details (balance, tariff information, device IDs) are automatically discovered from your Octopus Energy account during initialization and refreshed on the refresh interval.

## Channels

The binding provides the following channels:

| Channel ID               | Type            | Description                                                                                    |
|--------------------------|-----------------|------------------------------------------------------------------------------------------------|
| accountBalance           | Number          | Current account balance (GBP) - stored as dimensionless value                                  |
| consumption              | Number:Energy   | Half-hourly electricity import consumption (7 days, kWh) - TimeSeries                          |
| export                   | Number:Energy   | Half-hourly electricity export readings for solar/battery (7 days, kWh) - TimeSeries           |
| electricityTariffName    | String          | Name of current electricity tariff                                                             |
| electricityTariffDescription | String      | Description of current electricity tariff                                                      |
| electricityStandingCharge | Number         | Daily electricity standing charge (GBP) - stored as dimensionless value                        |
| currentRates             | Number          | Current electricity tariff rates including VAT (GBP/kWh) - TimeSeries, dimensionless          |
| currentExRates           | Number          | Current electricity tariff rates excluding VAT (GBP/kWh) - TimeSeries, dimensionless          |
| gasConsumption           | Number:Energy   | Half-hourly gas consumption (3 days, kWh) - TimeSeries                                         |
| gasTariffName            | String          | Name of current gas tariff                                                                     |
| gasTariffDescription     | String          | Description of current gas tariff                                                              |
| gasStandingCharge        | Number          | Daily gas standing charge (GBP) - stored as dimensionless value                                |
| gasRates                 | Number          | Current gas tariff rates including VAT (GBP/kWh) - TimeSeries, dimensionless                  |
| gasExRates               | Number          | Current gas tariff rates excluding VAT (GBP/kWh) - TimeSeries, dimensionless                  |
| liveDemand               | Number:Power    | Real-time power demand (W) - requires Octopus Home Mini                                        |
| liveMeterReading         | Number:Energy   | Real-time cumulative import meter reading (kWh) - requires Octopus Home Mini                   |
| liveExportReading        | Number:Energy   | Real-time cumulative export meter reading (kWh) - requires Octopus Home Mini                   |

**Important Notes:**

- All consumption and rate data is provided as **TimeSeries** for historical tracking and charting
- Energy prices are stored as **plain numbers (dimensionless)** because OpenHAB doesn't support GBP/kWh units natively
- The `consumption` channel provides **7 days** of half-hourly import data
- The `export` channel provides **7 days** of half-hourly export data (only populated if export meter detected)
- The `gasConsumption` channel provides **3 days** of half-hourly data
- Live channels (`liveDemand`, `liveMeterReading`, `liveExportReading`) only work with an **Octopus Home Mini** device
- The binding **automatically discovers** your meters, tariffs, and device IDs from your account
- Account details (balance, tariff names, standing charges) are refreshed every `refreshInterval` hours
- The binding handles **authentication token management** and renewal automatically (60-minute tokens, refreshed at 55 minutes)
- Rate limit errors are treated as transient and automatically retried on the next scheduled poll

## Full Example

### Thing Configuration

```java
Thing octopusapi:octopus:myaccount "Octopus Energy" [
    apiKey="sk_live_xxxxxxxxxxxxxxxxxxxx",
    accountNumber="A-12345678",
    refreshInterval=12,
    liveRefreshInterval=60
]
```

**Note**: The binding automatically discovers your smart meter device IDs, so you don't need to configure them manually.

### Item Configuration

```java
// Account information
Number AccountBalance "Account Balance [£%.2f]" { channel="octopusapi:octopus:myaccount:accountBalance" }

// Electricity consumption and export
Number:Energy ElectricityConsumption "Consumption [%.2f kWh]" { channel="octopusapi:octopus:myaccount:consumption" }
Number:Energy ElectricityExport "Export [%.2f kWh]" { channel="octopusapi:octopus:myaccount:export" }

// Electricity tariff information
String ElectricityTariffName "Tariff Name" { channel="octopusapi:octopus:myaccount:electricityTariffName" }
String ElectricityTariffDescription "Tariff Description" { channel="octopusapi:octopus:myaccount:electricityTariffDescription" }
Number ElectricityStandingCharge "Standing Charge [£%.4f/day]" { channel="octopusapi:octopus:myaccount:electricityStandingCharge" }

// Tariff rates (dimensionless - values in GBP per kWh)
Number ElectricityRate "Current Rate [%.4f £/kWh]" { channel="octopusapi:octopus:myaccount:currentRates" }
Number ElectricityRateExVAT "Rate Ex-VAT [%.4f £/kWh]" { channel="octopusapi:octopus:myaccount:currentExRates" }

// Gas consumption and tariff information
Number:Energy GasConsumption "Gas Consumption [%.2f kWh]" { channel="octopusapi:octopus:myaccount:gasConsumption" }
String GasTariffName "Gas Tariff Name" { channel="octopusapi:octopus:myaccount:gasTariffName" }
String GasTariffDescription "Gas Tariff Description" { channel="octopusapi:octopus:myaccount:gasTariffDescription" }
Number GasStandingCharge "Gas Standing Charge [£%.4f/day]" { channel="octopusapi:octopus:myaccount:gasStandingCharge" }
Number GasRate "Gas Rate [%.4f £/kWh]" { channel="octopusapi:octopus:myaccount:gasRates" }
Number GasRateExVAT "Gas Rate Ex-VAT [%.4f £/kWh]" { channel="octopusapi:octopus:myaccount:gasExRates" }

// Real-time data (requires Octopus Home Mini)
Number:Power LiveDemand "Current Demand [%.0f W]" { channel="octopusapi:octopus:myaccount:liveDemand" }
Number:Energy LiveMeterReading "Meter Reading [%.3f kWh]" { channel="octopusapi:octopus:myaccount:liveMeterReading" }
Number:Energy LiveExportReading "Export Reading [%.3f kWh]" { channel="octopusapi:octopus:myaccount:liveExportReading" }
```

### Sitemap Configuration

```perl
sitemap octopus label="Octopus Energy" {
    Frame label="Account" {
        Text item=AccountBalance
        Text item=ElectricityTariffName
        Text item=ElectricityTariffDescription
        Text item=ElectricityStandingCharge
    }
    
    Frame label="Electricity" {
        Text item=ElectricityConsumption
        Text item=ElectricityExport
        Text item=ElectricityRate
        Text item=ElectricityRateExVAT
        Chart item=ElectricityConsumption period=W refresh=3600
        Chart item=ElectricityRate period=D refresh=3600
    }
    
    Frame label="Gas" {
        Text item=GasTariffName
        Text item=GasStandingCharge
        Text item=GasConsumption
        Text item=GasRate
        Chart item=GasConsumption period=3D refresh=3600
    }
    
    Frame label="Live (Home Mini)" {
        Text item=LiveDemand
        Text item=LiveMeterReading
        Text item=LiveExportReading
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
- Using configurable refresh intervals (minimum 1 hour for historical data, minimum 60 seconds for live data)
- Automatically detecting rate limit errors and treating them as transient (will retry on next interval)
- Caching tariff data from the initialization query to minimize API calls

If you encounter rate limiting errors on live telemetry (error code KT-CT-1199), increase the `liveRefreshInterval` to 120 or more seconds.

## Troubleshooting

### Authentication Issues

If the binding shows as OFFLINE with authentication errors:

1. Verify your API key is correct (obtain a new one from your Octopus dashboard if needed)
2. Check your account number matches exactly (including the "A-" prefix)
3. Ensure your API key has not been revoked

### No Live Data

If live channels (`liveDemand`, `liveMeterReading`, `liveExportReading`) show no data:

1. Verify you have an Octopus Home Mini device installed and online
2. The binding automatically discovers your device ID - no manual configuration needed
3. Check the logs for "Found ... smart meter device ID" messages during initialization
4. Ensure live polling is not disabled (binding will skip if no device ID found)
5. If you see rate limit errors (KT-CT-1199), increase `liveRefreshInterval` to 120+ seconds

### Check Logs

Enable debug logging for detailed API communication:

```
log:set DEBUG org.openhab.binding.octopusapi
```

Then check the logs for GraphQL requests/responses and error messages.

## Developer Information

### Building from Source

```bash
# Fix code style (required before compile)
mvn spotless:apply

# Offline build (fast, for local development)
mvn -o clean install -DskipChecks

# Copy JAR to OpenHAB addons folder
cp target/org.openhab.binding.octopusapi-*.jar $OPENHAB_USERDATA/addons/

# Full release build (runs all checks)
mvn clean install
```

### API Reference

This binding uses the Octopus Energy GraphQL API v1:
- **Base URL**: `https://api.octopus.energy/v1/graphql/`
- **Authentication**: Token-based (obtained via `ObtainKrakenToken` mutation)
- **Token lifetime**: 60 minutes (auto-refreshed at 55 minutes)
- **Rate limiting**: Respects API limits via configurable polling intervals

### Key GraphQL Queries

- **Account data**: Retrieves meter points, tariffs, and device information
- **Consumption/Export**: Half-hourly electricity usage data
- **Gas consumption**: Half-hourly gas usage data
- **Tariff rates**: Current and historical pricing
- **Smart meter telemetry**: Real-time power demand (Home Mini required)

For more technical details, see the [copilot-instructions.md](.github/copilot-instructions.md) file.
