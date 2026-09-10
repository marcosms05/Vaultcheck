package io.vaultcheck.desktop;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Explicit graphical integration check, with bounded waiting outside the JavaFX thread. */
public final class DesktopWorkerSmoke {
    public static void main(String[] args) throws Exception {
        var app = new DesktopApplication(); app.init();
        var done = new CountDownLatch(1); var failure = new AtomicReference<Throwable>();
        Platform.startup(() -> Platform.runLater(() -> {
            var stage = new Stage();
            var timer = new Timeline();
            try {
                app.start(stage);
                var root = stage.getScene().getRoot();
                var run = root.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("Ejecutar prueba real")).map(n -> (Button)n).findFirst().orElseThrow();
                var cancel = root.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("Cancelar")).map(n -> (Button)n).findFirst().orElseThrow();
                var table = (TableView<?>)root.lookup(".table-view");
                run.fire();
                if (!run.isDisabled() || cancel.isDisabled()) throw new AssertionError("Busy controls incorrect");
                cancel.fire();
                if (!run.isDisabled()) throw new AssertionError("Cancelled before worker completion");
                var phase = new AtomicInteger();
                timer.getKeyFrames().add(new KeyFrame(Duration.millis(50), event -> {
                    try {
                        if (run.isDisabled()) return;
                        if (phase.getAndIncrement() == 0) {
                            boolean cancelled = root.lookupAll(".label").stream().anyMatch(n -> n instanceof Label l && l.getText().startsWith("Cancelada"));
                            if (!cancelled || !table.getItems().isEmpty()) throw new AssertionError("Cancellation not acknowledged");
                            run.fire();
                        } else {
                            if (table.getItems().size() != 5) throw new AssertionError("Real result missing");
                            boolean authenticated = root.lookupAll(".label").stream().anyMatch(n -> n instanceof Label l && l.getText().startsWith("Firma válida"));
                            if (!authenticated) throw new AssertionError("Authentication state missing");
                            System.out.println("WORKER SMOKE OK: responsive event loop, busy controls, cooperative cancellation, restart, authenticated results");
                            timer.stop(); stage.close(); done.countDown();
                        }
                    } catch(Throwable error) { failure.set(error); timer.stop(); stage.close(); done.countDown(); }
                }));
                timer.setCycleCount(Timeline.INDEFINITE); timer.play();
            } catch(Throwable error) { failure.set(error); stage.close(); done.countDown(); }
        }));
        try {
            if (!done.await(40, TimeUnit.SECONDS)) throw new AssertionError("Worker timeout");
            if (failure.get() != null) throw new AssertionError("Worker smoke failed", failure.get());
        } finally { Platform.exit(); app.stop(); }
    }
}
