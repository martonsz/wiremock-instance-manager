package cloud.marton.wiremock_instance_manager.repository;

import cloud.marton.wiremock_instance_manager.model.InstanceConfig;
import cloud.marton.wiremock_instance_manager.model.WireMockInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class InstanceRepository {

    private static final Logger log = LoggerFactory.getLogger(InstanceRepository.class);

    private final File configFile;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public InstanceRepository(
            @Value("${wiremock-manager.config-file:./wiremock-instances.json}") String configFilePath,
            ObjectMapper objectMapper) {
        this.configFile = new File(configFilePath);
        this.objectMapper = objectMapper;
    }

    public List<WireMockInstance> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(load().getInstances());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<WireMockInstance> findById(String id) {
        lock.readLock().lock();
        try {
            return load().getInstances().stream()
                    .filter(i -> i.getId().equals(id))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public WireMockInstance save(WireMockInstance instance) {
        lock.writeLock().lock();
        try {
            InstanceConfig config = load();
            List<WireMockInstance> instances = config.getInstances();
            instances.removeIf(i -> i.getId().equals(instance.getId()));
            instances.add(instance);
            persist(config);
            return instance;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteById(String id) {
        lock.writeLock().lock();
        try {
            InstanceConfig config = load();
            boolean removed = config.getInstances().removeIf(i -> i.getId().equals(id));
            if (removed) {
                persist(config);
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private InstanceConfig load() {
        if (!configFile.exists()) {
            return new InstanceConfig();
        }
        try {
            InstanceConfig config = objectMapper.readValue(configFile, InstanceConfig.class);
            migrate(config);
            return config;
        } catch (IOException e) {
            log.error("Failed to read config file {}: {}", configFile.getAbsolutePath(), e.getMessage());
            return new InstanceConfig();
        }
    }

    private void migrate(InstanceConfig config) {
        // Future: handle version < CURRENT_VERSION migrations here
        if (config.getVersion() < InstanceConfig.CURRENT_VERSION) {
            log.info("Migrating config from version {} to {}", config.getVersion(), InstanceConfig.CURRENT_VERSION);
            config.setVersion(InstanceConfig.CURRENT_VERSION);
        }
    }

    private void persist(InstanceConfig config) {
        try {
            if (!configFile.getParentFile().exists() && !configFile.getParentFile().mkdirs()) {
                throw new IOException("Failed to create config directory for file: " + configFile.getAbsolutePath());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
        } catch (IOException e) {
            log.error("Failed to write config file {}: {}", configFile.getAbsolutePath(), e.getMessage());
            throw new RuntimeException("Failed to persist configuration", e);
        }
    }
}
