package cloud.marton.wiremock_instance_manager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaticFilesController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping("/instance")
    public String instance() {
        return "forward:/instance.html";
    }
}
