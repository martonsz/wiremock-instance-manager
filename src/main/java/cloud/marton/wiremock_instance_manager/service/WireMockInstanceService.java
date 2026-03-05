package cloud.marton.wiremock_instance_manager.service;

import cloud.marton.wiremock_instance_manager.model.InstanceStatus;
import cloud.marton.wiremock_instance_manager.model.WireMockInstance;
import cloud.marton.wiremock_instance_manager.repository.InstanceRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.stubbing.StubImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class WireMockInstanceService {

    private static final Logger log = LoggerFactory.getLogger(WireMockInstanceService.class);

    private final InstanceRepository repository;
    private final WireMockServerManager serverManager;
    private final int adminPort;

    public WireMockInstanceService(
            InstanceRepository repository,
            WireMockServerManager serverManager,
            @Value("${server.port:8080}") int adminPort) {
        this.repository = repository;
        this.serverManager = serverManager;
        this.adminPort = adminPort;
    }

    @PostConstruct
    public void loadOnStartup() {
        List<WireMockInstance> instances = repository.findAll();
        for (WireMockInstance instance : instances) {
            instance.setStatus(InstanceStatus.STOPPED);
        }
        log.info("Loaded {} instance(s) from config (all set to STOPPED)", instances.size());
    }

    public List<WireMockInstance> listAll() {
        List<WireMockInstance> instances = repository.findAll();
        for (WireMockInstance instance : instances) {
            instance.setStatus(serverManager.isRunning(instance.getId())
                    ? InstanceStatus.RUNNING : InstanceStatus.STOPPED);
        }
        return instances;
    }

    public WireMockInstance getById(String id) {
        WireMockInstance instance = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Instance not found: " + id));
        instance.setStatus(serverManager.isRunning(id) ? InstanceStatus.RUNNING : InstanceStatus.STOPPED);
        return instance;
    }

    public WireMockInstance create(WireMockInstance instance) {
        validatePort(instance.getPort(), null);
        instance.setId(UUID.randomUUID().toString());
        instance.setStatus(InstanceStatus.STOPPED);
        Instant now = Instant.now();
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        return repository.save(instance);
    }

    public WireMockInstance update(String id, WireMockInstance updated) {
        WireMockInstance existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Instance not found: " + id));

        if (existing.getPort() != updated.getPort()) {
            validatePort(updated.getPort(), id);
        }

        existing.setName(updated.getName());
        existing.setPort(updated.getPort());
        existing.setOptions(updated.getOptions());
        existing.setUpdatedAt(Instant.now());
        return repository.save(existing);
    }

    public void delete(String id) {
        WireMockInstance instance = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Instance not found: " + id));
        serverManager.stop(id);
        repository.deleteById(id);
        serverManager.deleteInstanceData(instance);
        log.info("Deleted instance {}", id);
    }

    public WireMockInstance start(String id) {
        WireMockInstance instance = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Instance not found: " + id));
        serverManager.start(instance);
        instance.setStatus(InstanceStatus.RUNNING);
        return instance;
    }

    public WireMockInstance stop(String id) {
        WireMockInstance instance = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Instance not found: " + id));
        serverManager.stop(id);
        instance.setStatus(InstanceStatus.STOPPED);
        return instance;
    }

    public WireMockInstance restart(String id) {
        stop(id);
        return start(id);
    }

    public void uploadMappings(String id, MultipartFile file, String mode) throws IOException {
        WireMockServer server = serverManager.getServer(id);
        if (server == null || !server.isRunning()) {
            throw new IllegalStateException("Instance " + id + " is not running");
        }

        String json = new String(file.getBytes(), StandardCharsets.UTF_8);

        WireMock wireMock = new WireMock("localhost", server.port());

        if ("replace".equalsIgnoreCase(mode)) {
            wireMock.resetMappings();
        }

        StubImport stubImport = Json.read(json, StubImport.class);
        wireMock.importStubMappings(stubImport);
        log.info("Uploaded mappings to instance {} (mode={})", id, mode);
    }

    public InstanceStatus getStatus(String id) {
        if (repository.findById(id).isEmpty()) {
            throw new NoSuchElementException("Instance not found: " + id);
        }
        return serverManager.isRunning(id) ? InstanceStatus.RUNNING : InstanceStatus.STOPPED;
    }

    public int nextAvailablePort(int startFrom) {
        List<WireMockInstance> existing = repository.findAll();
        java.util.Set<Integer> usedPorts = new java.util.HashSet<>();
        usedPorts.add(adminPort);
        for (WireMockInstance inst : existing) {
            usedPorts.add(inst.getPort());
        }
        int port = startFrom;
        while (usedPorts.contains(port)) {
            port++;
        }
        return port;
    }

    private void validatePort(int port, String excludeId) {
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1024 and 65535");
        }
        if (port == adminPort) {
            throw new IllegalArgumentException("Port " + port + " is used by the admin server");
        }
        List<WireMockInstance> existing = repository.findAll();
        for (WireMockInstance inst : existing) {
            if (inst.getPort() == port && !inst.getId().equals(excludeId)) {
                throw new IllegalArgumentException("Port " + port + " is already in use by instance: " + inst.getName());
            }
        }
    }
}
