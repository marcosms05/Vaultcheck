package io.vaultcheck.desktop;

import javafx.application.Platform;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Injects only the native dialog selection, then exercises the real UI handlers and file reader. */
public final class DesktopFolderSmoke {
    public static void main(String[] args) throws Exception {
        var folder = Files.createTempDirectory("vaultcheck-selection-smoke-");
        var file = folder.resolve("fixture.txt"); Files.writeString(file, "abc");
        var app = new DesktopApplication(owner -> folder.toFile());
        var done = new CountDownLatch(1); var failure = new AtomicReference<Throwable>();
        try {
            app.init();
            Platform.startup(() -> Platform.runLater(() -> {
                var stage = new Stage(); var timer = new Timeline();
                try {
                    app.start(stage); var root = stage.getScene().getRoot();
                    var scan = (Button)root.lookup("#scan-folder");
                    if (!scan.isDisabled()) throw new AssertionError("Scan enabled without selection");
                    ((Button)root.lookup("#choose-folder")).fire();
                    if (scan.isDisabled()) throw new AssertionError("Selection did not enable scan");
                    scan.fire();
                    if (!scan.isDisabled()) throw new AssertionError("Concurrent scan possible");
                    timer.getKeyFrames().add(new KeyFrame(Duration.millis(50), event -> {
                        if (scan.isDisabled()) return;
                        try {
                            var table = (TableView<?>)root.lookup(".table-view");
                            if (table.getItems().size() != 1) throw new AssertionError("Missing inventory row");
                            table.getSelectionModel().select(0);
                            boolean evidence = root.lookupAll(".label").stream().anyMatch(n -> n instanceof Label l
                                    && l.getText().contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                                    && l.getText().contains("Sin referencia firmada"));
                            if (!evidence) throw new AssertionError("Missing hash or scope");
                            if (!Files.readString(file).equals("abc")) throw new AssertionError("Source changed");
                            System.out.println("FOLDER SMOKE OK: selection, background inventory, SHA-256, explicit scope, unchanged source");
                        } catch(Throwable error) { failure.set(error); }
                        finally { timer.stop(); stage.close(); done.countDown(); }
                    }));
                    timer.setCycleCount(Timeline.INDEFINITE); timer.play();
                } catch(Throwable error) { failure.set(error); stage.close(); done.countDown(); }
            }));
            if (!done.await(40, TimeUnit.SECONDS)) throw new AssertionError("Inventory timeout");
            if (failure.get() != null) throw new AssertionError("Folder smoke failed", failure.get());
        } finally { Platform.exit(); app.stop(); Files.deleteIfExists(file); Files.delete(folder); }
    }
}
