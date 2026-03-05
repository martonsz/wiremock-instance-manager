package cloud.marton.wiremock_instance_manager;

import cloud.marton.wiremock_instance_manager.model.InstanceStatus;
import cloud.marton.wiremock_instance_manager.model.WireMockInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "wiremock-manager.config-file=./target/test-wiremock-instances.json"
})
class WiremockServerApplicationTests {

    @Autowired MockMvc mvc;

    ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    String createdId;

    @AfterEach
    void cleanup() throws Exception {
        if (createdId != null) {
            try {
                mvc.perform(post("/api/instances/" + createdId + "/stop"));
                mvc.perform(delete("/api/instances/" + createdId));
            } catch (Exception ignored) {}
            createdId = null;
        }
    }

    @Test
    void contextLoads() {
    }

    @Test
    void fullLifecycle_createStartStopDelete() throws Exception {
        // Create
        WireMockInstance inst = new WireMockInstance();
        inst.setName("integration-test");
        inst.setPort(19080);
        inst.setOptions(List.of());

        MvcResult createResult = mvc.perform(post("/api/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(inst)))
                .andExpect(status().isOk())
                .andReturn();

        WireMockInstance created = mapper.readValue(
                createResult.getResponse().getContentAsString(), WireMockInstance.class);
        createdId = created.getId();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(InstanceStatus.STOPPED);

        // Start
        mvc.perform(post("/api/instances/" + createdId + "/start"))
                .andExpect(status().isOk());

        // Verify status is RUNNING
        mvc.perform(get("/api/instances/" + createdId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // Stop
        mvc.perform(post("/api/instances/" + createdId + "/stop"))
                .andExpect(status().isOk());

        // Verify status is STOPPED
        mvc.perform(get("/api/instances/" + createdId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));

        // Delete
        mvc.perform(delete("/api/instances/" + createdId))
                .andExpect(status().isNoContent());

        // Verify gone
        mvc.perform(get("/api/instances/" + createdId))
                .andExpect(status().isNotFound());

        createdId = null;
    }

    @Test
    void listAll_returnsEmptyOrInstances() throws Exception {
        mvc.perform(get("/api/instances"))
                .andExpect(status().isOk());
    }

    @Test
    void create_rejectsDuplicatePort() throws Exception {
        WireMockInstance inst = new WireMockInstance();
        inst.setName("dup-test");
        inst.setPort(19081);

        MvcResult r = mvc.perform(post("/api/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(inst)))
                .andExpect(status().isOk())
                .andReturn();
        createdId = mapper.readValue(r.getResponse().getContentAsString(), WireMockInstance.class).getId();

        WireMockInstance dup = new WireMockInstance();
        dup.setName("dup-test-2");
        dup.setPort(19081);

        mvc.perform(post("/api/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dup)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restart_works() throws Exception {
        WireMockInstance inst = new WireMockInstance();
        inst.setName("restart-test");
        inst.setPort(19082);

        MvcResult r = mvc.perform(post("/api/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(inst)))
                .andExpect(status().isOk())
                .andReturn();
        createdId = mapper.readValue(r.getResponse().getContentAsString(), WireMockInstance.class).getId();

        mvc.perform(post("/api/instances/" + createdId + "/start"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/instances/" + createdId + "/restart"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/instances/" + createdId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void uploadMappings_toRunningInstance() throws Exception {
        WireMockInstance inst = new WireMockInstance();
        inst.setName("upload-test");
        inst.setPort(19083);

        MvcResult r = mvc.perform(post("/api/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(inst)))
                .andExpect(status().isOk())
                .andReturn();
        createdId = mapper.readValue(r.getResponse().getContentAsString(), WireMockInstance.class).getId();

        mvc.perform(post("/api/instances/" + createdId + "/start"))
                .andExpect(status().isOk());

        String mappingsJson = "{\"mappings\":[{\"request\":{\"method\":\"GET\",\"url\":\"/test\"},\"response\":{\"status\":200,\"body\":\"ok\"}}]}";

        mvc.perform(multipart("/api/instances/" + createdId + "/mappings")
                        .file("file", mappingsJson.getBytes())
                        .param("mode", "import"))
                .andExpect(status().isOk());

        // Replace mode also works
        mvc.perform(multipart("/api/instances/" + createdId + "/mappings")
                        .file("file", mappingsJson.getBytes())
                        .param("mode", "replace"))
                .andExpect(status().isOk());
    }

    @Test
    void uploadMappings_toStoppedInstance_returns400() throws Exception {
        WireMockInstance inst = new WireMockInstance();
        inst.setName("stopped-upload-test");
        inst.setPort(19084);

        MvcResult r = mvc.perform(post("/api/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(inst)))
                .andExpect(status().isOk())
                .andReturn();
        createdId = mapper.readValue(r.getResponse().getContentAsString(), WireMockInstance.class).getId();

        String mappingsJson = "{\"mappings\":[]}";
        mvc.perform(multipart("/api/instances/" + createdId + "/mappings")
                        .file("file", mappingsJson.getBytes())
                        .param("mode", "import"))
                .andExpect(status().isBadRequest());
    }
}
