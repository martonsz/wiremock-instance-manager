package cloud.marton.wiremock_instance_manager.repository;

import cloud.marton.wiremock_instance_manager.model.WireMockInstance;
import cloud.marton.wiremock_instance_manager.model.WireMockOption;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceRepositoryTest {

    @TempDir
    File tempDir;

    InstanceRepository repository;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String configPath = tempDir.getAbsolutePath() + "/test-instances.json";
        repository = new InstanceRepository(configPath, mapper);
    }

    @Test
    void findAll_returnsEmpty_whenNoFile() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void save_andFindAll_returnsInstance() {
        WireMockInstance inst = newInstance("test", 9090);
        repository.save(inst);

        List<WireMockInstance> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getName()).isEqualTo("test");
    }

    @Test
    void findById_returnsCorrectInstance() {
        WireMockInstance inst = newInstance("find-me", 9091);
        repository.save(inst);

        Optional<WireMockInstance> found = repository.findById(inst.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("find-me");
    }

    @Test
    void findById_returnsEmpty_forUnknownId() {
        assertThat(repository.findById("nonexistent")).isEmpty();
    }

    @Test
    void save_updatesExistingInstance() {
        WireMockInstance inst = newInstance("original", 9090);
        repository.save(inst);

        inst.setName("updated");
        repository.save(inst);

        List<WireMockInstance> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getName()).isEqualTo("updated");
    }

    @Test
    void deleteById_removesInstance() {
        WireMockInstance inst = newInstance("to-delete", 9090);
        repository.save(inst);

        boolean deleted = repository.deleteById(inst.getId());

        assertThat(deleted).isTrue();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void deleteById_returnsFalse_whenNotFound() {
        boolean deleted = repository.deleteById("nonexistent");
        assertThat(deleted).isFalse();
    }

    @Test
    void save_multipleInstances() {
        repository.save(newInstance("a", 9090));
        repository.save(newInstance("b", 9091));
        repository.save(newInstance("c", 9092));

        assertThat(repository.findAll()).hasSize(3);
    }

    @Test
    void persistsOptions() {
        WireMockInstance inst = newInstance("optioned", 9090);
        inst.setOptions(List.of(new WireMockOption("verbose", "true")));
        repository.save(inst);

        WireMockInstance loaded = repository.findById(inst.getId()).orElseThrow();
        assertThat(loaded.getOptions()).hasSize(1);
    }

    @Test
    void migrate_upgradesOldVersion() throws Exception {
        // Write a config file with version 0 (old format)
        String oldJson = "{\"version\":0,\"instances\":[]}";
        File configFile = new File(tempDir, "test-instances.json");
        try (FileWriter w = new FileWriter(configFile)) {
            w.write(oldJson);
        }

        // Reload - should migrate to current version
        List<WireMockInstance> all = repository.findAll();
        assertThat(all).isEmpty(); // migration ran, no instances
    }

    @Test
    void load_returnsEmpty_onCorruptFile() throws Exception {
        File configFile = new File(tempDir, "test-instances.json");
        try (FileWriter w = new FileWriter(configFile)) {
            w.write("INVALID JSON{{{");
        }

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void persist_throwsOnUnwritablePath() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Use a path that cannot be written (file is a directory)
        String badPath = tempDir.getAbsolutePath() + "/subdir/instances.json";
        if (!new File(tempDir, "subdir").mkdirs()) {
            throw new RuntimeException("Failed to create subdir for test");
        }

        // make it a directory, not a file
        File conflictDir = new File(tempDir, "subdir/instances.json");
        if (!conflictDir.mkdirs()) {
            throw new RuntimeException("Failed to create subdir for test");
        }

        InstanceRepository badRepo = new InstanceRepository(badPath, mapper);
        WireMockInstance inst = newInstance("x", 9999);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> badRepo.save(inst))
                .isInstanceOf(RuntimeException.class);
    }

    private WireMockInstance newInstance(String name, int port) {
        WireMockInstance inst = new WireMockInstance();
        inst.setId(UUID.randomUUID().toString());
        inst.setName(name);
        inst.setPort(port);
        inst.setCreatedAt(Instant.now());
        inst.setUpdatedAt(Instant.now());
        return inst;
    }
}
