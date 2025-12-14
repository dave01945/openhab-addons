# OpenHAB Octopus API Binding - AI Agent Instructions

## Project Overview
This is an **OpenHAB binding** for the Octopus Energy **GraphQL API** (UK energy provider). It fetches electricity consumption, export data, and Agile tariff rates as TimeSeries data for home automation.

## Architecture

### Core Components (OSGi Service Pattern)
- **OctopusApiHandlerFactory**: OSGi component that creates thing handlers. Uses `@Component` annotation with service registration
- **OctopusApiHandler**: Main thing handler extending `BaseThingHandler`. Manages scheduled polling, processes GraphQL responses into TimeSeries
- **OctopusApiConnection**: GraphQL client wrapper handling token authentication, query execution, and error mapping
- **OctopusApiConfiguration**: POJO mapping XML config parameters to Java fields

### Data Flow
1. Handler initializes → validates config → creates connection with `HttpClient` (injected via OSGi)
2. Connection obtains authentication token using `ObtainKrakenToken` mutation with API key
3. Scheduled task polls GraphQL API every N hours (configurable 1-48h)
4. GraphQL responses parsed into `TimeSeries` objects with `Instant` timestamps
5. TimeSeries sent to channels (`consumption`, `export`, `agileRates`, `agileExRates`)

## Critical Development Workflows

### Build & Deploy
```bash
# Fix code style (REQUIRED before compile)
mvn spotless:apply

# Offline build (fast, for local dev)
mvn -o clean install -DskipChecks

# Copy JAR to openHAB addons folder
cp target/org.openhab.binding.octopusapi-*.jar $openhab_addons

# Full release build (runs all checks)
mvn clean install
```

**VS Code Tasks**: Use "Build" task which chains: Spotless → Compile (Offline) → Copy Distribution

### Debugging
```bash
# Start openHAB in debug mode
sudo $openhab_home/start_debug.sh

# Tail logs in separate terminals
tail -F $openhab_logs/openhab.log    # General logs
tail -F $openhab_logs/events.log     # Thing/channel events
```

**Remote Debug**: openHAB typically exposes port 5005 for JDWP when started with `start_debug.sh`

### I18n Property Generation
```bash
# Generate default translations from XML files
mvn i18n:generate-default-translations
```
Updates [src/main/resources/OH-INF/i18n/octopusapi.properties](src/main/resources/OH-INF/i18n/octopusapi.properties) from thing/config XML definitions.

### Testing GraphQL API (VS Code Tasks)
VS Code tasks available for direct API testing:
- "Test GraphQL: Obtain Token" - Get auth token from API key
- "Test GraphQL: Query Consumption" - Fetch consumption data (requires token)
- "Test GraphQL: Query Agile Rates" - Fetch tariff rates (requires token)
- "Grep binding logs" - Search openHAB logs for octopus-related entries

These tasks prompt for credentials and use `curl` + `jq` to test the GraphQL API directly.

## OpenHAB-Specific Patterns

### Thing Handler Lifecycle
```java
initialize() → 
  updateStatus(ThingStatus.UNKNOWN) →
  validate config (check hostname protocol) →
  create OctopusApiConnection →
  schedule polling task (scheduleWithFixedDelay) →
  updateStatus(ThingStatus.ONLINE/OFFLINE)

dispose() → 
  cancel scheduled tasks (scheduledFuture.cancel(true))
```
**Polling mechanism**: `pollTask()` called on init + every `refreshInterval` hours. Fetches consumption, export (if configured), and agile rates.

### Channel Updates
- Use `sendTimeSeries(ChannelUID, TimeSeries)` for time-series data
- TimeSeries uses `Policy.REPLACE` to replace existing forecasts
- Channels defined in [src/main/resources/OH-INF/thing/thing-types.xml](src/main/resources/OH-INF/thing/thing-types.xml)
- **Energy prices stored as dimensionless values**: OpenHAB doesn't have built-in currency/energy units (GBP/kWh not recognized). Store price values as plain `QuantityType` without units. Values are in pounds per kWh.

### Error Handling Convention
```java
// Configuration errors (user fixable)
throw new ConfigurationException("Invalid hostname");
// → Sets thing OFFLINE with CONFIGURATION_ERROR

// Communication errors (transient)
throw new CommunicationException("API timeout");
// → Allows automatic retry
```

### NonNull Annotations
- Use `@NonNullByDefault` at class level
- Mark nullable fields with `@NonNullByDefault({})` (empty set excludes field)
- Example: `private @NonNullByDefault({}) ScheduledFuture<?> scheduledFuture;`

## Configuration System

### XML → Java Mapping
[src/main/resources/OH-INF/config/config.xml](src/main/resources/OH-INF/config/config.xml) parameters map directly to `OctopusApiConfiguration` fields:
```xml
<parameter name="apiKey" type="text" required="true">
```
↓
```java
public String apiKey = "";
```

**Configuration Options**:
- `hostname`: API base URL (default: `https://api.octopus.energy`)
- `refreshInterval`: Polling interval in hours (1-48h, default: 12)
- `apiKey`, `accountNumber`, `mpanImport`, `meterSerial`: Required credentials
- `mpanExport`: Optional for solar export tracking
- `agileRegion`: UK region code (A-P) for tariff rates

### Thing Type UIDs
Defined in `OctopusApiBindingConstants`:
```java
BINDING_ID = "octopusapi"
THING_TYPE_OCTOPUSAPI = new ThingTypeUID(BINDING_ID, "octopus")
```
Must match XML: `<thing-type id="octopus">`

## Dependencies & Integration

### HTTP Client (OSGi Service)
```java
@Activate
public OctopusApiHandlerFactory(@Reference HttpClientFactory httpClientFactory) {
    this.httpClient = httpClientFactory.getCommonHttpClient();
}
```
**Never create your own HttpClient** - use the OSGi-provided shared instance.

### External API Integration
- Base URL: `https://api.octopus.energy/v1/graphql/` (GraphQL endpoint hardcoded in `OctopusApiConnection`)
- Auth: Token-based via `ObtainKrakenToken` mutation using API key
- Token lifetime: 60 minutes (cached, auto-refreshed at 55 minutes)
- Rate limiting: Handle `429 TOO_MANY_REQUESTS` gracefully
- GraphQL queries return nested JSON: `{"data": {...}, "errors": [...]}`
  - **Consumption/Export**: `account(accountNumber) → properties → electricityMeterPoints(mpan) → meters(serialNumber) → consumption/exportReadings`
  - **Agile Rates**: `applicableRates(accountNumber, mpxn, startAt, endAt)` returns `edges → node → {validFrom, validTo, value}`
  - Response fields: `startAt` (consumption timestamp), `value` (kWh or pence/kWh), `validFrom/validTo` (rate validity)
- See [OctopusApiConnection.java](src/main/java/org/openhab/binding/octopusapi/internal/OctopusApiConnection.java) for GraphQL query implementations

### JSON Parsing (Gson)
- Uses `com.google.gson` library (bundled in JAR via `bnd-maven-plugin`)
- GraphQL response structure: `{"data": {...}, "errors": [...]}`
- Always check for `"errors"` array in response - throw `ConfigurationException` if present
- TimeSeries parsing pattern for agile rates:
  ```java
  JsonObject data = response.getAsJsonObject("data");
  JsonArray edges = data.getAsJsonObject("applicableRates").getAsJsonArray("edges");
  for (JsonElement edge : edges) {
      JsonObject node = edge.getAsJsonObject().getAsJsonObject("node");
      Instant timestamp = Instant.parse(node.get("validFrom").getAsString());
      double valueInPence = node.get("value").getAsDouble();
      QuantityType<EnergyPrice> value = new QuantityType<>(valueInPence / 100.0 + " GBP/kWh");
      timeSeries.add(timestamp, value);
  }
  ```
- Consumption uses `startAt` for timestamps, `value` for kWh readings

## Code Style Requirements
- **Spotless**: Run `mvn spotless:apply` before every commit
- **Copyright headers**: All Java files must have EPL-2.0 header (see existing files)
- **Imports**: Use static imports for constants (see `OctopusApiHandlerFactory`)
- **Logging**: Use SLF4J, trace level for request/response details

## Key Files Reference
- Thing handlers: [src/main/java/org/openhab/binding/octopusapi/internal/](src/main/java/org/openhab/binding/octopusapi/internal/)
- Thing definitions: [src/main/resources/OH-INF/thing/thing-types.xml](src/main/resources/OH-INF/thing/thing-types.xml)
- Configuration schema: [src/main/resources/OH-INF/config/config.xml](src/main/resources/OH-INF/config/config.xml)
- Build: [pom.xml](pom.xml) (parent: openhab-addons reactor)
- VS Code tasks: [.vscode/tasks.json](.vscode/tasks.json)

## Common Pitfalls
- **Don't forget Spotless**: Build will fail if code style is wrong
- **Channel IDs must match XML**: String channel IDs in Java must match `thing-types.xml` definitions
- **Scheduler cleanup**: Always cancel `ScheduledFuture` in `dispose()` to prevent memory leaks
- **OSGI-INF generation**: Handler factory XML is auto-generated from `@Component` annotations
