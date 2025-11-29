# openHAB Sunsynk Modbus Binding - AI Coding Agent Instructions

## Project Overview
This is an openHAB binding for Sunsynk inverters using Modbus protocol. It's part of the larger openHAB add-ons ecosystem and follows openHAB's OSGi bundle architecture patterns.

**Tested Hardware**: Sunsynk Ecco 5kW inverter

## Architecture & Key Components

### Core Structure
- **Handler Factory**: `ModbussunsynkHandlerFactory` - OSGi component that creates thing handlers
- **Thing Handler**: `sunsynkHandler` - Extends `BaseModbusThingHandler` to manage Modbus communication
- **Register Enum**: `SunsynkInverterRegisters` - Defines all Modbus registers with conversion logic
- **Constants**: `ModbussunsynkBindingConstants` - Thing type UIDs and channel identifiers
- **Conversions**: `ConversionConstants` - Unit conversion functions (temperature Kelvin, decimal scaling)

### Modbus Communication Pattern
The binding splits register reads into batches respecting `ModbusConstants.MAX_REGISTERS_READ_COUNT`:
- See `buildRequests()` in `sunsynkHandler` - creates optimized read requests
- Uses `submitOneTimePoll()` for async reads with callbacks
- Register gaps require multiple requests (e.g., registers 16-279 with non-contiguous ranges)

### Channel Groups & Registers
Organized in XML (`thing-types.xml`) and enum (`SunsynkInverterRegisters`):
- `ss-overview` - State, energy totals, temperatures, system time, SD card status
- `ss-mppt-information` - Solar PV voltage/current/power (supports MPPT1-4 for multi-tracker systems)
- `ss-battery-information` - Battery SOC, power, charge limits, charging voltage, Bat1 SOC/cycle
- `ss-grid-information` - Grid voltage, frequency, import/export
- `ss-load-information` - Load consumption, AUX voltage/frequency, load frequency
- `ss-inverter-information` - Inverter voltage/current/frequency
- `ss-settings-solar` - Solar export, load priority, grid charge settings
- `ss-settings-battery` - Battery voltage management (equalization/absorption/float), current limits, capacity/voltage thresholds
- `ss-settings-timer` - 6 programmable timer slots with time/power/voltage/capacity/mode/charge
- `ss-settings-advanced` - Inverter on/off, max solar/sell power, peak shaving, control mode, BMS protocol

## Development Workflow

### Building
Use VS Code tasks (defined in workspace):
```bash
# Clean build with code style fixes (primary workflow)
Task: "mvn Compile (Offline)" → depends on "mvn Spotless (Fix codestyle)"

# Or manually:
mvn spotless:apply  # Fix code style
mvn clean install -DskipChecks -o  # Offline build
```

### Deployment to openHAB
Follow task chain: `Build` task executes:
1. `mvn Spotless (Fix codestyle)`
2. `mvn Compile (Offline)` 
3. `Copy Distribution to Addons` → copies JAR to `$openhab_addons`
4. `Set permissions of addon` → `chown openhab:openhab`

**Environment Setup**: Required environment variables for deployment tasks:
- `$openhab_addons` - Path to openHAB addons directory (e.g., `/etc/openhab/addons`)
- `$openhab_home` - openHAB installation directory
- `$openhab_runtime` - openHAB runtime directory (for stop/start commands)
- `$openhab_logs` - openHAB logs directory (e.g., `/var/log/openhab`)

Set in your shell profile (`.bashrc`/`.zshrc`) or VS Code settings.

### Debugging
- Task: "Start openHAB (Debug)" - launches with remote debugging
- Task: "Tail openhab.log" or "Tail events.log" - monitor runtime logs
- Logger: `LoggerFactory.getLogger(sunsynkHandler.class)`

## Code Conventions

### Naming Patterns
- **Classes**: CamelCase but handler class is `sunsynkHandler` (lowercase 's' - existing pattern)
- **Channels**: Prefix `ss-` (sunsynk), group-based (e.g., `ss-battery-soc`)
- **Registers**: SCREAMING_SNAKE_CASE in enum (e.g., `BATTERY_SOC`)
- **OSGi annotations**: `@Component(configurationPid = "binding.modbus.sunsynk")`

### Eclipse Public License Requirements
Every `.java` file starts with EPL-2.0 header (see existing files for template)

### Null Safety
Use `@NonNullByDefault` at class level, `@Nullable` for specific fields/methods

### Register Definition Pattern
```java
BATTERY_SOC(184, UINT16, BigDecimal.ONE, quantityFactory(Units.PERCENT), "battery-information")
//         ^reg# ^type   ^multiplier      ^unit factory              ^channel-group
```

Second register number for split registers: `TOTAL_GRID_IMPORT_ENERGY(78, 80, UINT32_SWAP, ...)`

### Value Conversion
- Scale factors: `DIV_BY_TEN` (0.1), `DIV_BY_HUNDRED` (0.01), `DIV_BY_THOU` (0.001)
- Temperature: `DIV_BY_TEMP_TEN` handles Sunsynk format → Kelvin conversion
- Time registers: Custom `ConversionConstants.TIME` format (HHMM as integer)
- Bitmask operations: `ConversionConstants.MASK_01`, `MASK_03`, `MASK_1C`

### Timer Command Validation
Timer slots must be sequential: prog1 < prog2 < prog3 < prog4 < prog5 < prog6
See `fixTimerCommand()` for validation logic - enforces time ordering

## XML Configuration

### Thing Definition (`thing-types.xml`)
- Bridge types: `serial` or `tcp` Modbus bridges required
- Channel groups organize 100+ channels by function
- Each channel references a channel-type definition

### Config Parameters (`config.xml`)
Current: `pollInterval` (ms, default 5000, min 100)
Future settings would go here (slave ID, timeouts, etc.)

## Integration Points

### Dependencies
- **Modbus Binding**: `org.openhab.binding.modbus` (parent binding, scope: provided)
- **Core APIs**: `org.openhab.core.thing`, `org.openhab.core.io.transport.modbus`
- Uses openHAB's `QuantityType` for units (JSR-385)

### Parent POM
Inherits from `org.openhab.addons.reactor.bundles` version `5.1.0-SNAPSHOT`
Java 21 target: `<maven.compiler.release>21</maven.compiler.release>`

## Common Patterns

### Adding New Registers
1. Add enum entry to `SunsynkInverterRegisters` with register number, type, conversion
2. Add channel to appropriate group in `thing-types.xml`
3. Add channel-type definition with proper units
4. Update i18n properties in `src/main/resources/OH-INF/i18n/sunsynk.properties`:
   - Channel labels: `channel-type.modbus.ss-{channel-name}.label`
   - Channel descriptions: `channel-type.modbus.ss-{channel-name}.description`
   - Use task "mvn update properties (Default)" to auto-generate translation templates

### Writable Settings
Writable channels include:
- `ss-settings-solar` - Load priority, load limit, grid charge, solar export, generator input
- `ss-settings-battery` - Voltage thresholds (equalization/absorption/float/shutdown/restart/low), capacity limits, max charge/discharge current, grid charge battery current
- `ss-settings-timer` - 6 timer programs with time/power/voltage/capacity/charge mode
- `ss-settings-advanced` - Inverter enabled, max solar power, max sell power, peak shaving settings, BMS protocol
- `ss-system-time` - Write system time to inverter (special 3-register handling)
- Handler checks `channelUID.getGroupId()` for "ss-settings-solar", "ss-settings-battery", "ss-settings-timer", "ss-settings-advanced"
- Uses `submitWrite()` for standard writes, `submitSystemTimeWrite()` for datetime
- Timer values require special validation (see `fixTimerCommand()`)

### State Updates
Async pattern in `readSuccessful()`:
1. Parse `ModbusRegisterArray` from read result
2. Extract values using register's `ValueType` (INT16, UINT32_SWAP, etc.)
3. Apply conversion function
4. Update channel with `updateState(channelUID, state)`

**Special case - System Time (register 22)**: Spans 3 contiguous registers (22, 23, 24)
- Handled specially in `readSuccessful()` before standard register processing
- Format: [reg22]=year/month, [reg23]=day/hour, [reg24]=minute/second
- Converts to openHAB `DateTimeType` in system timezone

## Known Issues & Quirks

- Timer command format: DateTime strings parsed as `yyyy-MM-dd'T'HH:mm:ss.SSSZ`, converted to HHMM integer
- Minutes validation: Enforces < 60, rounds to next hour if invalid
- Register gaps: Some registers aren't contiguous (e.g., 78 vs 80 for split 32-bit values)
- Power calculations: Load power derived from grid + battery + PV (see handler state tracking)

## Testing & Validation

Currently no unit tests in repository. Manual testing against actual Sunsynk hardware via:
1. Deploy to local openHAB instance
2. Configure Modbus bridge (serial/TCP)
3. Add Sunsynk inverter thing
4. Monitor logs for register read/write success
5. Verify channel updates in UI or via REST API
