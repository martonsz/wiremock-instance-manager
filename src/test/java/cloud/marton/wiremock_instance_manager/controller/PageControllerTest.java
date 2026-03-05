package cloud.marton.wiremock_instance_manager.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaticFilesController.class)
class PageControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void root_redirectsToIndexHtml() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    void instance_redirectsToInstanceHtml() throws Exception {
        mvc.perform(get("/instance"))
                .andExpect(status().isOk());
    }
}
