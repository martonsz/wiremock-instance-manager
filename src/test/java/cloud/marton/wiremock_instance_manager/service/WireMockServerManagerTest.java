package cloud.marton.wiremock_instance_manager.service;

import cloud.marton.wiremock_instance_manager.model.WireMockInstance;
import cloud.marton.wiremock_instance_manager.model.WireMockOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WireMockServerManagerTest {

    @TempDir
    File tempDataDir;

    WireMockServerManager manager;
    int testPort = 19900;

    @BeforeEach
    void setUp() {
        manager = new WireMockServerManager(tempDataDir.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        // clean up any running servers
        manager.stop("test-id");
    }

    @Test
    void isRunning_returnsFalse_whenNotStarted() {
        assertThat(manager.isRunning("unknown")).isFalse();
    }

    @Test
    void start_andIsRunning_returnsTrue() {
        WireMockInstance inst = instance(testPort);
        manager.start(inst);

        assertThat(manager.isRunning(inst.getId())).isTrue();
        manager.stop(inst.getId());
    }

    @Test
    void stop_makesIsRunningFalse() {
        WireMockInstance inst = instance(testPort + 1);
        manager.start(inst);
        manager.stop(inst.getId());

        assertThat(manager.isRunning(inst.getId())).isFalse();
    }

    @Test
    void getServer_returnsNull_whenNotRunning() {
        assertThat(manager.getServer("unknown")).isNull();
    }

    @Test
    void getServer_returnsServer_whenRunning() {
        WireMockInstance inst = instance(testPort + 2);
        manager.start(inst);

        assertThat(manager.getServer(inst.getId())).isNotNull();
        manager.stop(inst.getId());
    }

    @Test
    void start_twice_doesNotThrow() {
        WireMockInstance inst = instance(testPort + 3);
        manager.start(inst);
        manager.start(inst); // second call should be no-op

        assertThat(manager.isRunning(inst.getId())).isTrue();
        manager.stop(inst.getId());
    }

    @Test
    void stop_nonexistent_doesNotThrow() {
        manager.stop("nonexistent-id"); // should not throw
    }

    @Test
    void applyOption_verbose() {
        WireMockInstance inst = instance(testPort + 4);
        inst.setOptions(List.of(new WireMockOption("verbose", "true")));
        manager.start(inst);
        assertThat(manager.isRunning(inst.getId())).isTrue();
        manager.stop(inst.getId());
    }

    @Test
    void applyOption_noRequestJournal() {
        WireMockInstance inst = instance(testPort + 5);
        inst.setOptions(List.of(new WireMockOption("noRequestJournal", "true")));
        manager.start(inst);
        assertThat(manager.isRunning(inst.getId())).isTrue();
        manager.stop(inst.getId());
    }

    @Test
    void applyOption_unknownKey_doesNotThrow() {
        WireMockInstance inst = instance(testPort + 6);
        inst.setOptions(List.of(new WireMockOption("unknownOption", "value")));
        manager.start(inst);
        assertThat(manager.isRunning(inst.getId())).isTrue();
        manager.stop(inst.getId());
    }

    @Test
    void applyOptions_allSafeOptions(@TempDir File tempDir) {
        WireMockInstance inst = instance(testPort + 7);
        List<WireMockOption> opts = new ArrayList<>();
        opts.add(new WireMockOption("bindAddress", "127.0.0.1"));
        opts.add(new WireMockOption("globalResponseTemplating", "false"));
        opts.add(new WireMockOption("disableGzip", "false"));
        opts.add(new WireMockOption("maxRequestJournalEntries", "100"));
        opts.add(new WireMockOption("asyncResponseEnabled", "false"));
        opts.add(new WireMockOption("asyncResponseThreads", "2"));
        opts.add(new WireMockOption("jettyAcceptorThreads", "2"));
        opts.add(new WireMockOption("jettyAcceptQueueSize", "50"));
        opts.add(new WireMockOption("http2PlainDisabled", "true"));
        opts.add(new WireMockOption("http2TlsDisabled", "true"));
        opts.add(new WireMockOption("maxLoggedResponseSize", "1000"));
        opts.add(new WireMockOption("maxTemplateCacheEntries", "100"));
        opts.add(new WireMockOption("stubCorsEnabled", "true"));
        opts.add(new WireMockOption("enableBrowserProxying", "false"));
        opts.add(new WireMockOption("proxyTimeout", "5000"));
        opts.add(new WireMockOption("proxyHostHeader", "localhost"));
        opts.add(new WireMockOption("preserveHostHeader", "true"));
        opts.add(new WireMockOption("maxHttpClientConnections", "100"));
        opts.add(new WireMockOption("webhookThreadPoolSize", "5"));
        opts.add(new WireMockOption("rootDir", tempDir.getAbsolutePath()));
        inst.setOptions(opts);
        manager.start(inst);
        assertThat(manager.isRunning(inst.getId())).isTrue();
        manager.stop(inst.getId());
    }

    @Test
    void applyOptions_asyncResponseEnabled_true(@TempDir File tempDir) {
        WireMockInstance inst = instance(testPort + 8);
        inst.setOptions(List.of(
            new WireMockOption("asyncResponseEnabled", "true"),
            new WireMockOption("asyncResponseThreads", "4")
        ));
        manager.start(inst);
        assertThat(manager.isRunning(inst.getId())).isTrue();
        manager.stop(inst.getId());
    }

    @Test
    void applyOptions_disableGzip_true() {
        WireMockInstance inst = instance(testPort + 9);
        inst.setOptions(List.of(new WireMockOption("disableGzip", "true")));
        manager.start(inst);
        assertThat(manager.isRunning(inst.getId())).isTrue();
        manager.stop(inst.getId());
    }

    @Test
    void applyOptions_globalResponseTemplating_true() {
        WireMockInstance inst = instance(testPort + 10);
        inst.setOptions(List.of(new WireMockOption("globalResponseTemplating", "true")));
        manager.start(inst);
        assertThat(manager.isRunning(inst.getId())).isTrue();
        manager.stop(inst.getId());
    }

    @Test
    void deleteInstanceData_removesDirectory() throws IOException {
        WireMockInstance inst = instance(testPort + 11);
        Path instanceDir = tempDataDir.toPath().resolve(inst.getId());
        Files.createDirectories(instanceDir);
        Files.writeString(instanceDir.resolve("test.json"), "{}");

        manager.deleteInstanceData(inst);

        assertThat(instanceDir).doesNotExist();
    }

    @Test
    void deleteInstanceData_skipsWhenCustomRootDir() throws IOException {
        WireMockInstance inst = instance(testPort + 12);
        inst.setOptions(List.of(new WireMockOption("rootDir", "/custom/path")));
        Path instanceDir = tempDataDir.toPath().resolve(inst.getId());
        Files.createDirectories(instanceDir);

        manager.deleteInstanceData(inst);

        assertThat(instanceDir).exists(); // should NOT be deleted
    }

    @Test
    void deleteInstanceData_doesNotThrow_whenDirMissing() {
        WireMockInstance inst = instance(testPort + 13);
        manager.deleteInstanceData(inst); // no directory exists — should be a no-op
    }

    private WireMockInstance instance(int port) {
        WireMockInstance inst = new WireMockInstance();
        inst.setId(UUID.randomUUID().toString());
        inst.setName("test-" + port);
        inst.setPort(port);
        return inst;
    }
}
