package io.vaultcheck;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapTest {
    @Test void contextLoadsWithoutWebServer() {
        try (var context = new SpringApplicationBuilder(VaultCheckApplication.class)
                .web(WebApplicationType.NONE).run()) {
            assertTrue(context.isActive());
        }
    }
}
