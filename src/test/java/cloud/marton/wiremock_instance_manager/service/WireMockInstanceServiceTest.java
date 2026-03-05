package cloud.marton.wiremock_instance_manager.service;

import cloud.marton.wiremock_instance_manager.model.InstanceStatus;
import cloud.marton.wiremock_instance_manager.model.WireMockInstance;
import cloud.marton.wiremock_instance_manager.repository.InstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WireMockInstanceServiceTest {

    @Mock
    InstanceRepository repository;
    @Mock
    WireMockServerManager serverManager;

    WireMockInstanceService service;

    @BeforeEach
    void setUp() {
        service = new WireMockInstanceService(repository, serverManager, 8080);
    }

    @Test
    void listAll_enrichesStatus() {
        WireMockInstance inst = instance("a", 9090);
        when(repository.findAll()).thenReturn(List.of(inst));
        when(serverManager.isRunning(inst.getId())).thenReturn(true);

        List<WireMockInstance> result = service.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo(InstanceStatus.RUNNING);
    }

    @Test
    void create_assignsIdAndTimestamps() {
        WireMockInstance inst = new WireMockInstance();
        inst.setName("new");
        inst.setPort(9090);
        when(repository.findAll()).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WireMockInstance result = service.create(inst);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(InstanceStatus.STOPPED);
    }

    @Test
    void create_rejectsPortBelow1024() {
        WireMockInstance inst = new WireMockInstance();
        inst.setPort(80);
        assertThatThrownBy(() -> service.create(inst))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1024");
    }

    @Test
    void create_rejectsAdminPort() {
        WireMockInstance inst = new WireMockInstance();
        inst.setPort(8080);
        assertThatThrownBy(() -> service.create(inst))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("admin");
    }

    @Test
    void create_rejectsDuplicatePort() {
        WireMockInstance existing = instance("existing", 9090);
        when(repository.findAll()).thenReturn(List.of(existing));

        WireMockInstance inst = new WireMockInstance();
        inst.setPort(9090);
        assertThatThrownBy(() -> service.create(inst))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9090");
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getById_enrichesStatus() {
        WireMockInstance inst = instance("a", 9090);
        when(repository.findById(inst.getId())).thenReturn(Optional.of(inst));
        when(serverManager.isRunning(inst.getId())).thenReturn(false);

        WireMockInstance result = service.getById(inst.getId());
        assertThat(result.getStatus()).isEqualTo(InstanceStatus.STOPPED);
    }

    @Test
    void update_throwsWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("missing", new WireMockInstance()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void update_allowsSamePort() {
        WireMockInstance existing = instance("a", 9090);
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WireMockInstance updated = new WireMockInstance();
        updated.setName("a-updated");
        updated.setPort(9090);

        WireMockInstance result = service.update(existing.getId(), updated);
        assertThat(result.getName()).isEqualTo("a-updated");
    }

    @Test
    void delete_stopsAndDeletes() {
        WireMockInstance inst = instance("a", 9090);
        when(repository.findById(inst.getId())).thenReturn(Optional.of(inst));

        service.delete(inst.getId());

        verify(serverManager).stop(inst.getId());
        verify(repository).deleteById(inst.getId());
        verify(serverManager).deleteInstanceData(inst);
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void start_callsManager() {
        WireMockInstance inst = instance("a", 9090);
        when(repository.findById(inst.getId())).thenReturn(Optional.of(inst));

        WireMockInstance result = service.start(inst.getId());

        verify(serverManager).start(inst);
        assertThat(result.getStatus()).isEqualTo(InstanceStatus.RUNNING);
    }

    @Test
    void stop_callsManager() {
        WireMockInstance inst = instance("a", 9090);
        when(repository.findById(inst.getId())).thenReturn(Optional.of(inst));

        WireMockInstance result = service.stop(inst.getId());

        verify(serverManager).stop(inst.getId());
        assertThat(result.getStatus()).isEqualTo(InstanceStatus.STOPPED);
    }

    @Test
    void restart_stopsAndStarts() {
        WireMockInstance inst = instance("a", 9090);
        when(repository.findById(inst.getId())).thenReturn(Optional.of(inst));

        service.restart(inst.getId());

        verify(serverManager).stop(inst.getId());
        verify(serverManager).start(inst);
    }

    @Test
    void getStatus_returnsCorrectStatus() {
        WireMockInstance inst = instance("a", 9090);
        when(repository.findById(inst.getId())).thenReturn(Optional.of(inst));
        when(serverManager.isRunning(inst.getId())).thenReturn(true);

        InstanceStatus status = service.getStatus(inst.getId());
        assertThat(status).isEqualTo(InstanceStatus.RUNNING);
    }

    @Test
    void getStatus_throwsWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStatus("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void start_throwsWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.start("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void stop_throwsWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.stop("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void restart_throwsWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.restart("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void update_withPortChange_validates() {
        WireMockInstance existing = instance("a", 9090);
        WireMockInstance other = instance("b", 9091);
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.findAll()).thenReturn(List.of(existing, other));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WireMockInstance updated = new WireMockInstance();
        updated.setName("a-new");
        updated.setPort(9092); // new port, not conflicting

        WireMockInstance result = service.update(existing.getId(), updated);
        assertThat(result.getPort()).isEqualTo(9092);
    }

    @Test
    void update_withPortChange_rejectsConflict() {
        WireMockInstance existing = instance("a", 9090);
        WireMockInstance other = instance("b", 9091);
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.findAll()).thenReturn(List.of(existing, other));

        WireMockInstance updated = new WireMockInstance();
        updated.setName("a-new");
        updated.setPort(9091); // conflicts with "other"

        assertThatThrownBy(() -> service.update(existing.getId(), updated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadOnStartup_doesNotThrow() {
        when(repository.findAll()).thenReturn(List.of(instance("a", 9090)));
        service.loadOnStartup();
        verify(repository).findAll();
    }

    private WireMockInstance instance(String name, int port) {
        WireMockInstance inst = new WireMockInstance();
        inst.setId(UUID.randomUUID().toString());
        inst.setName(name);
        inst.setPort(port);
        inst.setCreatedAt(Instant.now());
        inst.setUpdatedAt(Instant.now());
        return inst;
    }
}
