package cloud.marton.wiremock_instance_manager.controller;

import cloud.marton.wiremock_instance_manager.model.InstanceStatus;
import cloud.marton.wiremock_instance_manager.model.WireMockInstance;
import cloud.marton.wiremock_instance_manager.service.WireMockInstanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InstanceApiController.class)
class InstanceApiControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean WireMockInstanceService service;

    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void listAll_returns200() throws Exception {
        when(service.listAll()).thenReturn(List.of(instance("a", 9090)));

        mvc.perform(get("/api/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("a"))
                .andExpect(jsonPath("$[0].status").value("STOPPED"));
    }

    @Test
    void create_returns200() throws Exception {
        WireMockInstance inst = instance("new", 9090);
        when(service.create(any())).thenReturn(inst);

        mvc.perform(post("/api/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void create_returns400_onIllegalArgument() throws Exception {
        when(service.create(any())).thenThrow(new IllegalArgumentException("Port conflict"));

        mvc.perform(post("/api/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Port conflict"));
    }

    @Test
    void getById_returns200() throws Exception {
        WireMockInstance inst = instance("a", 9090);
        when(service.getById(inst.getId())).thenReturn(inst);

        mvc.perform(get("/api/instances/" + inst.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        when(service.getById("missing")).thenThrow(new NoSuchElementException());

        mvc.perform(get("/api/instances/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_returns200() throws Exception {
        WireMockInstance inst = instance("updated", 9090);
        when(service.update(eq(inst.getId()), any())).thenReturn(inst);

        mvc.perform(put("/api/instances/" + inst.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inst)))
                .andExpect(status().isOk());
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        when(service.update(eq("missing"), any())).thenThrow(new NoSuchElementException());

        mvc.perform(put("/api/instances/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_returns400_onPortConflict() throws Exception {
        when(service.update(eq("id"), any())).thenThrow(new IllegalArgumentException("Port in use"));

        mvc.perform(put("/api/instances/id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        doNothing().when(service).delete("id");

        mvc.perform(delete("/api/instances/id"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        doThrow(new NoSuchElementException()).when(service).delete("missing");

        mvc.perform(delete("/api/instances/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void start_returns200() throws Exception {
        WireMockInstance inst = instance("a", 9090);
        inst.setStatus(InstanceStatus.RUNNING);
        when(service.start("id")).thenReturn(inst);

        mvc.perform(post("/api/instances/id/start"))
                .andExpect(status().isOk());
    }

    @Test
    void start_returns404_whenNotFound() throws Exception {
        when(service.start("missing")).thenThrow(new NoSuchElementException());

        mvc.perform(post("/api/instances/missing/start"))
                .andExpect(status().isNotFound());
    }

    @Test
    void start_returns500_onException() throws Exception {
        when(service.start("id")).thenThrow(new RuntimeException("Port bind failed"));

        mvc.perform(post("/api/instances/id/start"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void stop_returns200() throws Exception {
        WireMockInstance inst = instance("a", 9090);
        when(service.stop("id")).thenReturn(inst);

        mvc.perform(post("/api/instances/id/stop"))
                .andExpect(status().isOk());
    }

    @Test
    void stop_returns404_whenNotFound() throws Exception {
        when(service.stop("missing")).thenThrow(new NoSuchElementException());

        mvc.perform(post("/api/instances/missing/stop"))
                .andExpect(status().isNotFound());
    }

    @Test
    void restart_returns200() throws Exception {
        WireMockInstance inst = instance("a", 9090);
        when(service.restart("id")).thenReturn(inst);

        mvc.perform(post("/api/instances/id/restart"))
                .andExpect(status().isOk());
    }

    @Test
    void restart_returns404_whenNotFound() throws Exception {
        when(service.restart("missing")).thenThrow(new NoSuchElementException());

        mvc.perform(post("/api/instances/missing/restart"))
                .andExpect(status().isNotFound());
    }

    @Test
    void restart_returns500_onException() throws Exception {
        when(service.restart("id")).thenThrow(new RuntimeException("fail"));

        mvc.perform(post("/api/instances/id/restart"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getStatus_returns200() throws Exception {
        when(service.getStatus("id")).thenReturn(InstanceStatus.RUNNING);

        mvc.perform(get("/api/instances/id/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void getStatus_returns404_whenNotFound() throws Exception {
        when(service.getStatus("missing")).thenThrow(new NoSuchElementException());

        mvc.perform(get("/api/instances/missing/status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadMappings_returns404_whenNotFound() throws Exception {
        doThrow(new NoSuchElementException()).when(service).uploadMappings(eq("missing"), any(), any());

        mvc.perform(multipart("/api/instances/missing/mappings")
                        .file("file", "{}".getBytes())
                        .param("mode", "import"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadMappings_returns400_whenNotRunning() throws Exception {
        doThrow(new IllegalStateException("not running")).when(service).uploadMappings(eq("id"), any(), any());

        mvc.perform(multipart("/api/instances/id/mappings")
                        .file("file", "{}".getBytes())
                        .param("mode", "import"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadMappings_returns200_onSuccess() throws Exception {
        doNothing().when(service).uploadMappings(eq("id"), any(), any());

        mvc.perform(multipart("/api/instances/id/mappings")
                        .file("file", "{\"mappings\":[]}".getBytes())
                        .param("mode", "import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mappings uploaded successfully"));
    }

    @Test
    void uploadMappings_returns500_onIOException() throws Exception {
        doThrow(new java.io.IOException("read error")).when(service).uploadMappings(eq("id"), any(), any());

        mvc.perform(multipart("/api/instances/id/mappings")
                        .file("file", "{}".getBytes())
                        .param("mode", "import"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getVersion_returns200WithVersionKey() throws Exception {
        mvc.perform(get("/api/instances/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists());
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
