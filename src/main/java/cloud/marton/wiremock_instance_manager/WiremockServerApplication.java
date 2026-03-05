package cloud.marton.wiremock_instance_manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WiremockServerApplication {

    static void main(String[] args) {
        SpringApplication.run(WiremockServerApplication.class, args);
    }

}
