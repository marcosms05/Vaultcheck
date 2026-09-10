package io.vaultcheck.desktop;

import io.vaultcheck.infrastructure.files.*;
import io.vaultcheck.infrastructure.manifest.ReferenceReview;
import io.vaultcheck.application.VerifyFolder;
import javafx.application.Platform;
import javafx.animation.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.*;
import javafx.util.Duration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Uses real JavaFX password dialogs; only OS file/directory chooser results are replaced. */
public final class DesktopCreationSmoke {
    public static void main(String[] args) throws Exception {
        var temp = Files.createTempDirectory("vaultcheck-creation-ui-");
        var source = Files.createDirectory(temp.resolve("source")); var file = source.resolve("file.txt"); Files.writeString(file, "abc");
        var destination = temp.resolve("reference.vcm");
        var identity = new AtomicReference<CreateLocalIdentity.Created>();
        var executor = Executors.newSingleThreadExecutor(); var done = new CountDownLatch(1);
        var error = new AtomicReference<Throwable>(); var busy = new AtomicBoolean(); var jobs = new AtomicInteger();
        var phase = new AtomicInteger(); var fields = new ArrayList<PasswordField>();
        try {
            Platform.startup(() -> Platform.runLater(() -> {
                var stage = new Stage(); var timer = new Timeline(); var root = new VBox(16);
                try {
                    java.util.function.Consumer<CooperativeTask<?>> submit = task -> { jobs.incrementAndGet(); busy.set(true); root.setDisable(true); executor.execute(task); };
                    Runnable finish = () -> { busy.set(false); root.setDisable(false); };
                    var creation = new CreateReferencePanel(stage, () -> source, submit, finish,
                            (title, pattern, save) -> destination);
                    var identities = new IdentityPanel(stage, submit, finish, value -> { identity.set(value); creation.useIdentity(value); }, temp::toFile);
                    root.getChildren().addAll(identities, creation); var scene = new Scene(root, 1100, 650);
                    scene.getStylesheets().add(DesktopCreationSmoke.class.getResource("/desktop.css").toExternalForm());
                    stage.setScene(scene); stage.show(); root.applyCss(); root.layout();
                    respond(ButtonType.CANCEL, fields); button(root, "Crear identidad cifrada…").fire();
                    assertCleared(fields); if (jobs.get() != 0) throw new AssertionError("Cancel started a job");
                    respond(ButtonType.OK, fields); button(root, "Crear identidad cifrada…").fire(); assertCleared(fields);
                    timer.getKeyFrames().add(new KeyFrame(Duration.millis(50), event -> {
                        if (busy.get()) return;
                        timer.pause(); Platform.runLater(() -> {
                        try {
                            switch (phase.getAndIncrement()) {
                                case 0 -> {
                                    if (identity.get() == null) throw new AssertionError("Identity not created");
                                    button(root, "Preparar referencia").fire();
                                }
                                case 1 -> {
                                    var export = button(root, "Firmar y guardar…");
                                    if (export.isDisabled()) throw new AssertionError("Preparation failed");
                                    int before = jobs.get(); respond(ButtonType.CANCEL, fields); export.fire(); assertCleared(fields);
                                    if (jobs.get() != before || Files.exists(destination)) throw new AssertionError("Cancel persisted a reference");
                                    respond(ButtonType.OK, fields); export.fire(); assertCleared(fields);
                                }
                                default -> {
                                    if (!Files.exists(destination)) throw new AssertionError("Reference missing");
                                    try (var ref = Files.newInputStream(destination); var pub = Files.newInputStream(identity.get().publicKey())) {
                                        var review = ReferenceReview.read(ref, pub);
                                        var result = review.approve(identity.get().fingerprint()).verify(source, new WindowsFolderScanner(), () -> false);
                                        if (result.differences().getFirst().status() != VerifyFolder.Status.MATCHED) throw new AssertionError("Export does not verify");
                                    }
                                    if (!Files.readString(file).equals("abc")) throw new AssertionError("Source changed");
                                    System.out.println("CREATION UI OK: real password dialogs, cancellation without jobs, cleared controls, identity, prepare, export, verified signature");
                                    timer.stop(); stage.close(); done.countDown();
                                }
                            }
                        } catch(Throwable failure) { error.set(failure); timer.stop(); stage.close(); done.countDown(); }
                        finally { if (done.getCount() > 0) timer.play(); }
                        });
                    })); timer.setCycleCount(Timeline.INDEFINITE); timer.play();
                } catch(Throwable failure) { error.set(failure); stage.close(); done.countDown(); }
            }));
            if (!done.await(45, TimeUnit.SECONDS)) throw new AssertionError("Creation UI timeout");
            if (error.get() != null) throw new AssertionError("Creation UI failed", error.get());
        } finally {
            Platform.exit(); executor.shutdown(); executor.awaitTermination(10, TimeUnit.SECONDS);
            Files.deleteIfExists(destination); Files.delete(file); Files.delete(source);
            if (identity.get() != null) {
                Files.delete(identity.get().encryptedKey()); Files.delete(identity.get().publicKey()); Files.delete(identity.get().directory());
            }
            Files.delete(temp);
        }
    }
    private static void respond(ButtonType decision, List<PasswordField> captured) {
        Platform.runLater(() -> {
            var pane = Window.getWindows().stream().filter(Window::isShowing).map(w -> w.getScene().getRoot())
                    .filter(n -> n instanceof DialogPane).map(n -> (DialogPane)n).findFirst().orElseThrow();
            for (var node : pane.lookupAll(".password-field")) {
                var field = (PasswordField)node; field.setText("synthetic-ui-password"); captured.add(field);
            }
            ((Button)pane.lookupButton(decision)).fire();
        });
    }
    private static void assertCleared(List<PasswordField> fields) {
        if (fields.isEmpty() || fields.stream().anyMatch(f -> !f.getText().isEmpty())) throw new AssertionError("Password controls retained data");
        fields.clear();
    }
    private static Button button(Parent parent, String label) {
        return parent.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals(label)).map(n -> (Button)n).findFirst().orElseThrow();
    }
}

