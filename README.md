# NyitottAkták Search & Result Collector (Kotlin + Ktor + Compose Desktop)

A Kotlin application that parses comma-separated search terms, queries the [NyitottAkták API](https://nyitottaktak.hu/api/search), aggregates search results across all terms with pagination support, and provides both an interactive Jetpack Compose for Desktop UI and CLI execution with CSV/JSON exports.

## Features

- **Comma-separated Search Terms**: Accepts a comma-separated list of names (e.g., `farkasházi, petőfi, kovács`).
- **Compose Multiplatform UI**: Desktop GUI built with Jetpack Compose for Desktop and Material 3 design:
  - Input field for comma-separated names.
  - Sorting criteria (by name `nev`, birth date `szul_ido`) and direction (`asc` / `desc`).
  - Asynchronous querying with live progress indicator.
  - Interactive master-detail results browser showing full item properties, tags, and raw JSON fields.
  - One-click CSV and JSON exports via system file save dialogs.
- **Ktor Client (CIO Engine)**: Asynchronous HTTP client configured with Kotlinx Serialization, SSL handling, and automatic pagination.
- **CLI & Headless Mode**: Can be invoked directly from the command line with custom formatting and output options.
- **Console Logging**: Logback provides configurable logging, with Log4j 2 API calls routed through SLF4J to the same backend.
- **Comprehensive Test Suite**: Unit tests using Ktor MockEngine and Kotlinx Coroutines Test for parsing, API fetching, pagination, and file exports.

## Requirements

- **JDK**: Java 17+
- **Gradle**: Gradle 8+ (Gradle wrapper `./gradlew` is included)

## Running the Application

### 1. Launching the Compose Desktop GUI

Run without arguments to launch the graphical desktop interface:

```bash
./gradlew run
```

### 2. Running in CLI Mode

Pass search terms and optional arguments using `--args`:

```bash
# Basic summary search
./gradlew run --args="--input farkasházi --format summary"

# Multi-term search with CSV output
./gradlew run --args="--input \"farkasházi, petőfi\" --format csv --output results.csv"

# JSON output
./gradlew run --args="--input \"farkasházi, kovács\" --format json --output results.json"
```

### CLI Options

- `--input`, `-i`: Comma-separated query string.
- `--sort`, `-s`: Field to sort by (`nev` [default], `szul_ido`).
- `--dir`, `-d`: Sort direction (`asc` [default], `desc`).
- `--format`, `-f`: Output format (`summary` [default], `json`, `csv`).
- `--output`, `-o`: File path to save output to.
- `--first-page-only`: Restrict fetching to the first page (disables automatic pagination).

## Logging

The application uses Logback (`1.5.18`) as its logging backend. Log4j 2 API support (`2.24.3`) is provided through `log4j-to-slf4j`, so Log4j and SLF4J messages share the same configuration. A separate Log4j Core backend or `log4j2.xml` configuration is not needed.

Logging is configured in `src/main/resources/logback.xml`. By default, messages at `INFO` level and above are written to the console with a timestamp, thread name, level, logger name, and message.

To change verbosity, edit the root logger's `level` in that file (for example, to `DEBUG`). You can also add logger-specific levels inside `<configuration>`:

```xml
<logger name="hu.nyitottaktak" level="DEBUG" />
```

## Running Tests

Execute the automated test suite:

```bash
./gradlew test
```
