package io.vaultcheck.desktop;

import io.vaultcheck.application.VerifyFolder;
import io.vaultcheck.domain.*;
import io.vaultcheck.infrastructure.manifest.*;
import javafx.application.Platform;
import javafx.animation.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real UI handlers, crypto and native reads; only dialog responses are injected. */
public final class DesktopReferenceSmoke {
    public static void main(String[] args) throws Exception {
        var temporary = Files.createTempDirectory("vaultcheck-reference-smoke-");
        var data = Files.createDirectory(temporary.resolve("data"));
        var file = data.resolve("example.txt"); Files.writeString(file, "abc");
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var ref = temporary.resolve("sample.vcm"); var pub = temporary.resolve("public.der");
        var manifest = new ReferenceManifest(1, 0, List.of(new ReferenceEntry(new ManifestPath("example.txt"),
                new FileFingerprint(3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))));
        var bytes = new SignedManifestCodec().sign(manifest, key.getPrivate());
        Files.write(ref, bytes); Files.write(pub, key.getPublic().getEncoded());
        var fingerprint = PinnedSigners.fingerprint(key.getPublic());
        var executor = Executors.newSingleThreadExecutor(); var done = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>(); var result = new AtomicReference<VerifyFolder.Result>();
        var panelRef = new AtomicReference<ReferencePanel>(); var busy = new AtomicBoolean();
        var confirmations = new AtomicInteger(); var phase = new AtomicInteger();
        try {
            Platform.startup(() -> Platform.runLater(() -> {
                var stage = new Stage(); var timer = new Timeline();
                try {
                    var panel = new ReferencePanel(() -> data,
                            task -> { busy.set(true); panelRef.get().setDisable(true); executor.execute(task); },
                            () -> { busy.set(false); panelRef.get().setDisable(false); }, result::set, text -> {},
                            (title, extension) -> extension.equals("*.vcm") ? ref : pub,
                            () -> Optional.of(confirmations.getAndIncrement() == 0 ? "0".repeat(64) : fingerprint));
                    panelRef.set(panel); var scene = new Scene(panel, 1100, 500);
                    scene.getStylesheets().add(DesktopReferenceSmoke.class.getResource("/desktop.css").toExternalForm());
                    stage.setScene(scene); stage.show(); panel.applyCss(); panel.layout();
                    button(panel, "Elegir referencia").fire(); button(panel, "Elegir clave pública").fire();
                    if (!button(panel, "Verificar referencia").isDisabled()) throw new AssertionError("Automatically trusted import");
                    button(panel, "Revisar firma").fire();
                    timer.getKeyFrames().add(new KeyFrame(Duration.millis(50), event -> {
                        if (busy.get()) return;
                        try {
                            if (phase.get() == 0) {
                                if (button(panel, "Aprobar identidad…").isDisabled()) throw new AssertionError("Review failed");
                                button(panel, "Aprobar identidad…").fire();
                                if (!button(panel, "Verificar referencia").isDisabled()) throw new AssertionError("Wrong fingerprint accepted");
                                panel.useLocalIdentity("0".repeat(64));
                                if (!button(panel, "Usar mi identidad de esta sesión").isDisabled()) throw new AssertionError("Unrelated local identity accepted");
                                panel.useLocalIdentity(fingerprint);
                                if (!button(panel, "Verificar referencia").isDisabled()) throw new AssertionError("Local identity silently approved");
                                button(panel, "Usar mi identidad de esta sesión").fire();
                                if (button(panel, "Verificar referencia").isDisabled()) throw new AssertionError("Matching local identity rejected");
                                button(panel, "Aprobar identidad…").fire();
                                if (button(panel, "Verificar referencia").isDisabled()) throw new AssertionError("Correct fingerprint rejected");
                                bytes[bytes.length - 1] ^= 1; Files.write(ref, bytes);
                                phase.set(1); button(panel, "Verificar referencia").fire();
                            } else if (phase.get() == 1) {
                                if (result.get() == null || result.get().differences().getFirst().status() != VerifyFolder.Status.MATCHED)
                                    throw new AssertionError("Reviewed bytes were not retained");
                                button(panel, "Elegir clave pública").fire();
                                if (!button(panel, "Verificar referencia").isDisabled()) throw new AssertionError("Key reselection retained trust");
                                if (!button(panel, "Usar mi identidad de esta sesión").isDisabled()) throw new AssertionError("Local approval enabled before signature review");
                                phase.set(2); button(panel, "Revisar firma").fire();
                            } else {
                                if (!button(panel, "Aprobar identidad…").isDisabled() || !button(panel, "Verificar referencia").isDisabled())
                                    throw new AssertionError("Tampered reference approved");
                                System.out.println("REFERENCE SMOKE OK: no automatic trust, wrong fingerprint rejected, explicit approval, frozen reference, selection invalidation, tampered signature rejected");
                                timer.stop(); stage.close(); done.countDown();
                            }
                        } catch(Throwable error) { failure.set(error); timer.stop(); stage.close(); done.countDown(); }
                    }));
                    timer.setCycleCount(Timeline.INDEFINITE); timer.play();
                } catch(Throwable error) { failure.set(error); stage.close(); done.countDown(); }
            }));
            if (!done.await(40, TimeUnit.SECONDS)) throw new AssertionError("Reference UI timeout");
            if (failure.get() != null) throw new AssertionError("Reference UI failed", failure.get());
        } finally {
            Platform.exit(); executor.shutdown(); executor.awaitTermination(10, TimeUnit.SECONDS);
            Files.delete(file); Files.delete(data); Files.delete(ref); Files.delete(pub); Files.delete(temporary);
        }
    }
    private static Button button(ReferencePanel panel, String text) {
        return panel.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals(text))
                .map(n -> (Button)n).findFirst().orElseThrow();
    }
}
