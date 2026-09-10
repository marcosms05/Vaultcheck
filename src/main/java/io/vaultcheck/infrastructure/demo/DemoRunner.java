package io.vaultcheck.infrastructure.demo;

import io.vaultcheck.application.CompareEntries;
import io.vaultcheck.domain.FileFingerprint;
import io.vaultcheck.domain.ManifestPath;
import io.vaultcheck.domain.ReferenceEntry;
import io.vaultcheck.infrastructure.files.WindowsHandleFingerprintReader;
import java.nio.file.Files;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Opt-in demonstration with synthetic data only. Never accepts a real reference or user root. */
@Component
@ConditionalOnProperty(name = "vaultcheck.demo", havingValue = "true")
public final class DemoRunner implements CommandLineRunner {
    @Override public void run(String... args) throws Exception {
        var directory = Files.createTempDirectory("vaultcheck-demo-");
        var matching = directory.resolve("coincidente.txt");
        var modified = directory.resolve("modificado.txt");
        try {
            Files.writeString(matching, "abc");
            Files.writeString(modified, "abd");
            var expected = new FileFingerprint(3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            var entries = List.of(
                    new ReferenceEntry(new ManifestPath("coincidente.txt"), expected),
                    new ReferenceEntry(new ManifestPath("modificado.txt"), expected),
                    new ReferenceEntry(new ManifestPath("no-disponible.txt"), expected));
            System.out.println("VaultCheck | DEMOSTRACION con datos sinteticos");
            System.out.println("Firma: NO COMPROBADA | Alcance: solo las tres entradas de prueba");
            var result = new CompareEntries(new WindowsHandleFingerprintReader())
                    .compare(directory, entries, () -> false, entry -> {
                        String status = switch (entry.status()) {
                            case MATCHED -> "COINCIDENTE";
                            case MODIFIED -> "MODIFICADO";
                            case NOT_VERIFIABLE -> "NO VERIFICABLE";
                        };
                        System.out.println(entry.path().value() + " -> " + status);
                    });
            System.out.printf("Resultado: %d coincidente(s), %d modificado(s), %d no verificable(s)%n",
                    result.matched(), result.modified(), result.notVerifiable());
            System.out.println("Cobertura: " + result.coverage() + " | No es una verificacion autenticada.");
            if (result.matched() != 1 || result.modified() != 1 || result.notVerifiable() != 1) {
                throw new IllegalStateException("The native demo did not produce the expected fixture results");
            }
        } finally {
            // Delete only the exact files created here; never recursively clean a user-selected tree.
            try { Files.deleteIfExists(matching); }
            finally {
                try { Files.deleteIfExists(modified); }
                finally { Files.deleteIfExists(directory); }
            }
        }
    }
}
