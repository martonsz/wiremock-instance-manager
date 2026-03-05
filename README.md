# WireMock Server Manager

![CI](https://github.com/martonsz/wiremock-instance-manager/actions/workflows/main.yml/badge.svg)

A Spring Boot admin application for managing multiple embedded [WireMock](https://wiremock.org/) server instances.
Create, configure, start, stop, and monitor WireMock instances through a web UI or REST API. Configuration persists to
disk across restarts.

## Requirements

- Java 25
- Gradle (wrapper included)

## Quick Start

```bash
# Start the admin app
./gradlew bootRun
```

Open [http://localhost:8080](http://localhost:8080) in your browser.

1. Click **Create New Instance**, enter a name and port (e.g. `9090`)
2. Click **Start** — WireMock is now live at `http://localhost:9090`
3. Visit `http://localhost:9090/__admin/mappings` to confirm

## Features

- **Multiple instances** — run as many WireMock servers as needed, each on its own port
- **Persistent config** — instances survive admin app restarts (loaded back as `STOPPED`)
- **Mappings upload** — upload a JSON mappings file via the UI or API (import or replace modes)
- **Live status** — the UI polls status every 5 seconds
- **Option configuration** — configure WireMock options (HTTPS port, response templating, Jetty settings, etc.) per
  instance
- **Port safety** — server-side validation prevents port conflicts and reserved port usage

## Configuration

Edit `src/main/resources/application.yaml`:

```yaml
server:
  port: 8080                               # admin UI port

wiremock-manager:
  data-dir: ${WIREMOCK_INSTANCE_MANAGER_DATA_DIR:./wiremock-data} # where WireMock instance data (mappings, files) is stored
  config-file: ${WIREMOCK_INSTANCE_MANAGER_CONFIG_FILE:./wiremock-data/wiremock-instances.json} # where instances are persisted
```

## REST API

All endpoints are under `/api/instances`.

| Method   | Path                           | Description                         |
|----------|--------------------------------|-------------------------------------|
| `GET`    | `/api/instances`               | List all instances with live status |
| `POST`   | `/api/instances`               | Create a new instance               |
| `GET`    | `/api/instances/{id}`          | Get a single instance               |
| `PUT`    | `/api/instances/{id}`          | Update name, port, or options       |
| `DELETE` | `/api/instances/{id}`          | Stop and delete an instance         |
| `POST`   | `/api/instances/{id}/start`    | Start an instance                   |
| `POST`   | `/api/instances/{id}/stop`     | Stop an instance                    |
| `POST`   | `/api/instances/{id}/restart`  | Restart an instance                 |
| `POST`   | `/api/instances/{id}/mappings` | Upload a mappings JSON file         |
| `GET`    | `/api/instances/{id}/status`   | Get live status                     |

### Example: Create and start an instance

```bash
# Create
curl -X POST http://localhost:8080/api/instances \
  -H 'Content-Type: application/json' \
  -d '{"name":"My API Mock","port":9090,"options":[]}'

# Start (use the id from the response above)
curl -X POST http://localhost:8080/api/instances/{id}/start
```

### Example: Upload mappings

```bash
curl -X POST http://localhost:8080/api/instances/{id}/mappings \
  -F file=@mappings.json \
  -F mode=import        # or 'replace' to clear existing mappings first
```

**Mappings file format** (standard WireMock import format):

```json
{
  "mappings": [
    {
      "request": {
        "method": "GET",
        "url": "/hello"
      },
      "response": {
        "status": 200,
        "body": "Hello World"
      }
    }
  ]
}
```

## Supported WireMock Options

Options can be set per instance via the UI or API using these keys:

| Key                        | Type   | Description                                    |
|----------------------------|--------|------------------------------------------------|
| `httpsPort`                | int    | Enable HTTPS on this port                      |
| `bindAddress`              | string | Bind to a specific address (default: all)      |
| `globalResponseTemplating` | bool   | Enable Handlebars templating for all responses |
| `disableGzip`              | bool   | Disable Gzip compression                       |
| `noRequestJournal`         | bool   | Disable the request journal                    |
| `maxRequestJournalEntries` | int    | Cap the request journal size                   |
| `asyncResponseEnabled`     | bool   | Enable asynchronous response dispatch          |
| `asyncResponseThreads`     | int    | Number of async response threads               |
| `jettyAcceptorThreads`     | int    | Number of Jetty acceptor threads               |
| `jettyAcceptQueueSize`     | int    | Jetty accept queue size                        |
| `rootDir`                  | string | Root directory for mappings and files          |

## Development

```bash
# Run tests
./gradlew test

# Run tests with coverage report
./gradlew test jacocoTestReport
# → build/reports/jacoco/test/html/index.html

# Verify ≥85% coverage threshold
./gradlew jacocoTestCoverageVerification
```

## Tech Stack

| Component   | Technology                     |
|-------------|--------------------------------|
| Framework   | Spring Boot 4.0.3              |
| Language    | Java 25                        |
| Mock server | WireMock 3.13.0 (embedded)     |
| Persistence | JSON file via Jackson          |
| Frontend    | Plain HTML + vanilla JS (AJAX) |
| Testing     | JUnit 5, Mockito, MockMvc      |
| Coverage    | JaCoCo 0.8.13                  |

## Project Layout

```
src/main/java/cloud/marton/wiremock_instance_manager/
├── config/          # Spring beans (ObjectMapper, CORS)
├── controller/      # REST API + page routing
├── model/           # Data model (WireMockInstance, options, config)
├── repository/      # JSON file persistence
└── service/         # Business logic + live WireMock lifecycle

src/main/resources/
├── application.yaml
├── logback.xml
└── static/          # index.html, instance.html, js/app.js
```

## Logs

Application logs are written to `logs/wiremock-manager.log` with daily rotation (30-day retention). Console output
mirrors the log file.
