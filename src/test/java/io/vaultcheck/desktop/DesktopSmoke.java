package io.vaultcheck.desktop;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit desktop smoke harness; requires a Windows graphical session. Not part of headless tests. */
public final class DesktopSmoke {
    public static void main(String[] args) throws Exception {
        var app = new DesktopApplication();
        var done = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        app.init();
        try {
            Platform.startup(() -> Platform.runLater(() -> {
                Stage stage = new Stage();
                try {
                    app.start(stage);
                    if (stage.getIcons().size() != 1 || stage.getIcons().getFirst().isError()) throw new AssertionError("Application icon missing or invalid");
                    var root = stage.getScene().getRoot();
                    var table = (TableView<?>) root.lookup(".table-view");
                    if (((javafx.scene.control.SplitPane)root.lookup(".split-pane")).getItems().size() != 1) throw new AssertionError("Inspector should start hidden");
                    if (!table.getItems().isEmpty()) throw new AssertionError("Expected empty state");
                    root.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().startsWith("Mostrar resultados")).map(n -> (Button)n).findFirst().orElseThrow().fire();
                    if (table.getItems().size() != 5) throw new AssertionError("Expected demo rows");
                    var search = root.lookupAll(".text-field").stream().filter(n -> n instanceof TextField t && t.isEditable()).map(n -> (TextField)n).findFirst().orElseThrow();
                    search.setText("memoria");
                    if (table.getItems().size() != 1) throw new AssertionError("Search failed");
                    search.clear();
                    ((javafx.scene.control.ToggleButton)root.lookup("#filter-differences")).fire();
                    if (table.getItems().size() != 3) throw new AssertionError("Difference filter failed");
                    search.setText("memoria");
                    if (table.getItems().size() != 1) throw new AssertionError("Combined filter failed");
                    if (!((javafx.scene.control.ToggleButton)root.lookup("#filter-differences")).getText().endsWith("3")) throw new AssertionError("Search changed global count");
                    search.clear();
                    ((javafx.scene.control.ToggleButton)root.lookup("#filter-unavailable")).fire();
                    if (!table.getItems().isEmpty()) throw new AssertionError("Invented unavailable rows");
                    ((javafx.scene.control.ToggleButton)root.lookup("#filter-all")).fire();
                    if (table.getItems().size() != 5) throw new AssertionError("All filter failed");
                    table.getSelectionModel().select(1);
                    if (((javafx.scene.control.SplitPane)root.lookup(".split-pane")).getItems().size() != 2) throw new AssertionError("Inspector did not open");
                    double originalWidth = stage.getWidth(), originalHeight = stage.getHeight();
                    stage.setWidth(800); stage.setHeight(600);
                    root.applyCss(); root.layout();
                    var workspace = (javafx.scene.control.ScrollPane) root.lookup("#workspace-scroll");
                    workspace.resize(565, 560); workspace.layout();
                    if (((javafx.scene.control.SplitPane)root.lookup(".split-pane")).getOrientation() != javafx.geometry.Orientation.VERTICAL)
                        throw new AssertionError("Compact inspector must stack below results");
                    if (!workspace.isFitToWidth() || workspace.getContent().getBoundsInLocal().getHeight() <= workspace.getViewportBounds().getHeight())
                        throw new AssertionError("Small window must retain scrollable content");
                    stage.setWidth(originalWidth); stage.setHeight(originalHeight);
                    root.applyCss(); root.layout();
                    var image = stage.getScene().snapshot(null);
                    var png = new java.awt.image.BufferedImage((int)image.getWidth(), (int)image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var pixels = image.getPixelReader();
                    for (int y=0;y<png.getHeight();y++) for(int x=0;x<png.getWidth();x++) png.setRGB(x,y,pixels.getArgb(x,y));
                    javax.imageio.ImageIO.write(png, "png", new java.io.File("target/desktop-preview.png"));
                    root.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("Limpiar vista")).map(n -> (Button)n).findFirst().orElseThrow().fire();
                    if (!table.getItems().isEmpty()) throw new AssertionError("Clear failed");
                    System.out.println("DESKTOP SMOKE OK: empty, demo, search, selection, snapshot, clear");
                } catch (Throwable error) { failure.set(error); }
                finally { stage.close(); done.countDown(); }
            }));
            if (!done.await(45, TimeUnit.SECONDS)) throw new AssertionError("Desktop timeout");
            if (failure.get() != null) throw new AssertionError("Desktop smoke failed", failure.get());
        } finally { Platform.exit(); app.stop(); }
    }
}


