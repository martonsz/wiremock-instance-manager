package cloud.marton.wiremock_instance_manager;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class WiremockServerApplicationTest {

    @Test
    void main_invokesSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(any(Class.class), any(String[].class)))
                    .thenReturn(null);

            WiremockServerApplication.main(new String[]{});

            mocked.verify(() -> SpringApplication.run(WiremockServerApplication.class, new String[]{}));
        }
    }
}
