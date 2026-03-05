package cloud.marton.wiremock_instance_manager.service;

import cloud.marton.wiremock_instance_manager.model.WireMockInstance;
import cloud.marton.wiremock_instance_manager.model.WireMockOption;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WireMockServerManager {

    private static final Logger log = LoggerFactory.getLogger(WireMockServerManager.class);

    private final ConcurrentHashMap<String, WireMockServer> runningServers = new ConcurrentHashMap<>();
    private final String dataDir;

    public WireMockServerManager(@Value("${wiremock-manager.data-dir:./wiremock-data}") String dataDir) {
        this.dataDir = dataDir;
    }

    public void start(WireMockInstance instance) {
        if (runningServers.containsKey(instance.getId())) {
            log.warn("WireMock server for instance {} is already running", instance.getId());
            return;
        }
        WireMockConfiguration config = buildConfiguration(instance);
        WireMockServer server = new WireMockServer(config);
        server.start();
        runningServers.put(instance.getId(), server);
        log.info("Started WireMock server for instance '{}' on port {}", instance.getName(), instance.getPort());
    }

    public void stop(String instanceId) {
        WireMockServer server = runningServers.remove(instanceId);
        if (server != null) {
            server.stop();
            log.info("Stopped WireMock server for instance {}", instanceId);
        }
    }

    public boolean isRunning(String instanceId) {
        WireMockServer server = runningServers.get(instanceId);
        return server != null && server.isRunning();
    }

    public WireMockServer getServer(String instanceId) {
        return runningServers.get(instanceId);
    }

    public void deleteInstanceData(WireMockInstance instance) {
        boolean hasCustomRootDir = instance.getOptions() != null &&
                instance.getOptions().stream().anyMatch(opt -> "rootDir".equals(opt.key()));
        if (hasCustomRootDir) {
            return;
        }
        Path instanceDir = Paths.get(dataDir, instance.getId());
        if (!Files.exists(instanceDir)) {
            return;
        }
        try (var paths = Files.walk(instanceDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to delete instance data dir {}: {}", instanceDir, e.getMessage());
        }
        log.info("Deleted data directory for instance {}", instance.getId());
    }

    private WireMockConfiguration buildConfiguration(WireMockInstance instance) {
        WireMockConfiguration config = WireMockConfiguration.options().port(instance.getPort());

        boolean rootDirSet = false;
        if (instance.getOptions() != null) {
            for (WireMockOption opt : instance.getOptions()) {
                applyOption(config, opt.key(), opt.value());
                if ("rootDir".equals(opt.key())) {
                    rootDirSet = true;
                }
            }
        }

        if (!rootDirSet) {
            config.withRootDirectory(dataDir + "/" + instance.getId());
        }

        return config;
    }

    private void applyOption(WireMockConfiguration config, String key, String value) {
        switch (key) {
            // Existing options
            case "httpsPort" -> config.httpsPort(Integer.parseInt(value));
            case "bindAddress" -> config.bindAddress(value);
            case "globalResponseTemplating" -> {
                if (Boolean.parseBoolean(value)) config.globalTemplating(true);
            }
            case "disableGzip" -> {
                if (Boolean.parseBoolean(value)) config.gzipDisabled(true);
            }
            case "noRequestJournal" -> {
                if (Boolean.parseBoolean(value)) config.disableRequestJournal();
            }
            case "maxRequestJournalEntries" -> config.maxRequestJournalEntries(Integer.parseInt(value));
            case "asyncResponseEnabled" -> config.asynchronousResponseEnabled(Boolean.parseBoolean(value));
            case "asyncResponseThreads" -> config.asynchronousResponseThreads(Integer.parseInt(value));
            case "jettyAcceptorThreads" -> config.jettyAcceptors(Integer.parseInt(value));
            case "jettyAcceptQueueSize" -> config.jettyAcceptQueueSize(Integer.parseInt(value));
            case "rootDir" -> config.withRootDirectory(value);
            // Jetty / threading
            case "containerThreads" -> config.containerThreads(Integer.parseInt(value));
            // HTTP/2
            case "http2PlainDisabled" -> config.http2PlainDisabled(Boolean.parseBoolean(value));
            case "http2TlsDisabled" -> config.http2TlsDisabled(Boolean.parseBoolean(value));
            // HTTPS / TLS (server cert)
            case "keystorePath" -> config.keystorePath(value);
            case "keystorePassword" -> config.keystorePassword(value);
            case "keyManagerPassword" -> config.keyManagerPassword(value);
            case "keystoreType" -> config.keystoreType(value);
            case "needClientAuth" -> config.needClientAuth(Boolean.parseBoolean(value));
            case "trustStorePath" -> config.trustStorePath(value);
            case "trustStorePassword" -> config.trustStorePassword(value);
            // CA / browser-proxy TLS
            case "caKeystorePath" -> config.caKeystorePath(value);
            case "caKeystorePassword" -> config.caKeystorePassword(value);
            case "caKeystoreType" -> config.caKeystoreType(value);
            // Response / journal
            case "maxLoggedResponseSize" -> config.maxLoggedResponseSize(Integer.parseInt(value));
            // Templating
            case "maxTemplateCacheEntries" -> config.withMaxTemplateCacheEntries(Long.parseLong(value));
            // CORS
            case "stubCorsEnabled" -> config.stubCorsEnabled(Boolean.parseBoolean(value));
            // Proxy / forwarding
            case "enableBrowserProxying" -> config.enableBrowserProxying(Boolean.parseBoolean(value));
            case "proxyTimeout" -> config.proxyTimeout(Integer.parseInt(value));
            case "proxyHostHeader" -> config.proxyHostHeader(value);
            case "preserveHostHeader" -> config.preserveHostHeader(Boolean.parseBoolean(value));
            case "maxHttpClientConnections" -> config.maxHttpClientConnections(Integer.parseInt(value));
            // Webhooks
            case "webhookThreadPoolSize" -> config.withWebhookThreadPoolSize(Integer.parseInt(value));
            default -> log.warn("Unknown WireMock option: {}", key);
        }
    }
}
