package io.vaultcheck;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class VaultCheckApplication {
    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains("--vaultcheck.ui=true")) {
            javafx.application.Application.launch(io.vaultcheck.desktop.DesktopApplication.class, args);
            return;
        }
        try (var context = new SpringApplicationBuilder(VaultCheckApplication.class)
                .web(WebApplicationType.NONE).run(args)) {
            // Bootstrap milestone: desktop lifecycle will replace this smoke entry point.
        }
    }
}

