# WireMock Server Manager — Claude Instructions

## Project Overview

Spring Boot 4.0.3 (Java 25) application that manages multiple embedded WireMock server instances via a REST API and HTML
frontend. Instances are persisted to `wiremock-instances.json`.

## Build & Run

```bash
# Run all tests + generate JaCoCo report
./gradlew test

# Verify ≥85% code coverage
./gradlew jacocoTestCoverageVerification

# Start the admin app on port 8080
./gradlew bootRun
```

Coverage report: `build/reports/jacoco/test/html/index.html`
Test report: `build/reports/tests/test/index.html`

## Project Structure

```
src/main/java/cloud/marton/wiremock_instance_manager/
├── WiremockServerApplication.java
├── config/AppConfig.java               # ObjectMapper bean, CORS
├── model/
│   ├── WireMockInstance.java           # id, name, port, status, options, timestamps
│   ├── InstanceStatus.java             # RUNNING | STOPPED
│   ├── WireMockOption.java             # key-value config option
│   └── InstanceConfig.java             # versioned JSON root {version, instances}
├── repository/InstanceRepository.java  # file-based JSON CRUD, ReentrantReadWriteLock
├── service/
│   ├── WireMockInstanceService.java    # CRUD + lifecycle orchestration
│   └── WireMockServerManager.java      # ConcurrentHashMap of live WireMockServer instances
└── controller/
    ├── InstanceApiController.java       # REST /api/instances/...
    └── PageController.java              # serves index.html, instance.html

src/main/resources/
├── application.yaml
├── logback.xml
└── static/
    ├── index.html                       # instance table, auto-refresh every 5s
    ├── instance.html                    # per-instance control panel
    └── js/app.js                        # shared fetchJson, showToast, statusBadge
```

## Wiremock documentation

- OpenAPI spec: doc/wiremock-admin-api.json

## Key Architecture Decisions

- WireMock instances run **in-process** (embedded), not as separate JVMs
- Status (`RUNNING`/`STOPPED`) is **runtime-only** — annotated `@JsonIgnore`, not persisted
- On app restart all instances reload from JSON as `STOPPED`
- Config file path is configurable: `wiremock-manager.config-file` in `application.yaml`

## REST API

| Method | Path                           | Description                                    |
|--------|--------------------------------|------------------------------------------------|
| GET    | `/api/instances`               | List all with live status                      |
| POST   | `/api/instances`               | Create instance                                |
| GET    | `/api/instances/{id}`          | Get one instance                               |
| PUT    | `/api/instances/{id}`          | Update name/port/options                       |
| DELETE | `/api/instances/{id}`          | Stop and delete                                |
| POST   | `/api/instances/{id}/start`    | Start                                          |
| POST   | `/api/instances/{id}/stop`     | Stop                                           |
| POST   | `/api/instances/{id}/restart`  | Restart                                        |
| POST   | `/api/instances/{id}/mappings` | Upload mappings JSON (`?mode=import\|replace`) |
| GET    | `/api/instances/{id}/status`   | Get live status                                |

## Supported WireMock Options (key → WireMockConfiguration method)

| Key                        | Method                                          | Value type |
|----------------------------|-------------------------------------------------|------------|
| `asyncResponseEnabled`     | `.asynchronousResponseEnabled(bool)`            | boolean    |
| `asyncResponseThreads`     | `.asynchronousResponseThreads(n)`               | int        |
| `bindAddress`              | `.bindAddress(addr)`                            | String     |
| `caKeystorePassword`       | `.caKeystorePassword(s)`                        | String     |
| `caKeystorePath`           | `.caKeystorePath(s)`                            | String     |
| `caKeystoreType`           | `.caKeystoreType(s)`                            | String     |
| `containerThreads`         | `.containerThreads(n)`                          | int        |
| `disableGzip`              | `.gzipDisabled(bool)`                           | boolean    |
| `enableBrowserProxying`    | `.enableBrowserProxying(bool)`                  | boolean    |
| `globalResponseTemplating` | `.globalTemplating(bool)`                       | boolean    |
| `http2PlainDisabled`       | `.http2PlainDisabled(bool)`                     | boolean    |
| `http2TlsDisabled`         | `.http2TlsDisabled(bool)`                       | boolean    |
| `httpsPort`                | `.httpsPort(n)`                                 | int        |
| `jettyAcceptorThreads`     | `.jettyAcceptors(n)`                            | int        |
| `jettyAcceptQueueSize`     | `.jettyAcceptQueueSize(n)`                      | int        |
| `keyManagerPassword`       | `.keyManagerPassword(s)`                        | String     |
| `keystorePassword`         | `.keystorePassword(s)`                          | String     |
| `keystorePath`             | `.keystorePath(s)`                              | String     |
| `keystoreType`             | `.keystoreType(s)`                              | String     |
| `maxHttpClientConnections` | `.maxHttpClientConnections(n)`                  | int        |
| `maxLoggedResponseSize`    | `.maxLoggedResponseSize(n)`                     | int        |
| `maxRequestJournalEntries` | `.maxRequestJournalEntries(n)`                  | int        |
| `maxTemplateCacheEntries`  | `.withMaxTemplateCacheEntries(n)`               | long       |
| `needClientAuth`           | `.needClientAuth(bool)`                         | boolean    |
| `noRequestJournal`         | `.disableRequestJournal()`                      | boolean    |
| `preserveHostHeader`       | `.preserveHostHeader(bool)`                     | boolean    |
| `proxyHostHeader`          | `.proxyHostHeader(s)`                           | String     |
| `proxyTimeout`             | `.proxyTimeout(n)`                              | int        |
| `rootDir`                  | `.withRootDirectory(path)`                      | String     |
| `stubCorsEnabled`          | `.stubCorsEnabled(bool)`                        | boolean    |
| `trustStorePassword`       | `.trustStorePassword(s)`                        | String     |
| `trustStorePath`           | `.trustStorePath(s)`                            | String     |
| `webhookThreadPoolSize`    | `.withWebhookThreadPoolSize(n)`                 | int        |
| `verbose`                  | logged as unsupported (removed in WireMock 3.x) | —          |

## Spring Boot 4.x / Java 25 Gotchas

- `@WebMvcTest` and `@AutoConfigureMockMvc` are in `org.springframework.boot.webmvc.test.autoconfigure` (moved from
  `org.springframework.boot.test.autoconfigure.web.servlet`)
- Test dependency required: `spring-boot-starter-webmvc-test`
- `@MockitoBean` is in `org.springframework.test.context.bean.override.mockito`
- JaCoCo **must be 0.8.13+** — 0.8.12 fails with "Unsupported class file major version 69" on Java 25

## WireMock 3.x API Notes

- Upload mappings: `Json.read(json, StubImport.class)` + `wireMock.importStubMappings(stubImport)`
- Reset mappings: `wireMock.resetMappings()`
- No `verbose(boolean)` on `WireMockConfiguration` in 3.x

## Port Validation Rules

- Range: 1024–65535
- Cannot conflict with admin port (8080)
- Cannot conflict with other existing instances

## Test Strategy

- `InstanceRepositoryTest` — uses `@TempDir`, tests CRUD + migration + error paths
- `WireMockServerManagerTest` — starts real embedded WireMock servers on ports 19900–19910+
- `WireMockInstanceServiceTest` — Mockito mocks for repository and manager
- `InstanceApiControllerTest` — `@WebMvcTest` with `@MockitoBean`
- `PageControllerTest` — `@WebMvcTest` for HTML serving
- `WiremockServerApplicationTests` — full `@SpringBootTest` lifecycle: create → start → upload → stop → delete

## MCP

- Always use Context7 MCP when I need library/API documentation, code generation, setup or configuration steps without
  me having to explicitly ask.

Integration tests use ports 19080–19084. Avoid these ports when running the app locally.
