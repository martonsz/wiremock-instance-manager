package cloud.marton.wiremock_instance_manager.controller;

import cloud.marton.wiremock_instance_manager.model.InstanceStatus;
import cloud.marton.wiremock_instance_manager.model.WireMockInstance;
import cloud.marton.wiremock_instance_manager.service.WireMockInstanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/instances")
public class InstanceApiController {

    @Value("${app.version:unknown}")
    private String appVersion;

    private final WireMockInstanceService service;

    public InstanceApiController(WireMockInstanceService service) {
        this.service = service;
    }

    @GetMapping("/version")
    public Map<String, String> getVersion() {
        return Map.of("version", appVersion);
    }

    @GetMapping
    public List<WireMockInstance> listAll() {
        return service.listAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody WireMockInstance instance) {
        try {
            return ResponseEntity.ok(service.create(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/next-port")
    public Map<String, Integer> nextPort() {
        return Map.of("port", service.nextAvailablePort(9090));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.getById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody WireMockInstance instance) {
        try {
            return ResponseEntity.ok(service.update(id, instance));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<?> start(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.start(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stop(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.stop(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/restart")
    public ResponseEntity<?> restart(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.restart(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/mappings")
    public ResponseEntity<?> uploadMappings(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "import") String mode) {
        try {
            service.uploadMappings(id, file, mode);
            return ResponseEntity.ok(Map.of("message", "Mappings uploaded successfully"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to read uploaded file"));
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable String id) {
        try {
            InstanceStatus status = service.getStatus(id);
            return ResponseEntity.ok(Map.of("status", status));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
