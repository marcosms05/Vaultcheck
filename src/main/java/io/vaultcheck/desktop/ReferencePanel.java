package io.vaultcheck.desktop;

import io.vaultcheck.application.VerifyFolder;
import io.vaultcheck.infrastructure.manifest.ReferenceReview;
import io.vaultcheck.infrastructure.files.WindowsFolderScanner;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;
import java.util.function.*;

/** Session-only review: selected bytes are frozen before approval, never automatically trusted. */
final class ReferencePanel extends VBox {
    private Path referencePath, keyPath;
    private final Consumer<String> message;
    private ReferenceReview reviewed;
    private ReferenceReview.Approved approved;
    private final Label status = new Label("Sin referencia. Importar una clave no implica confiar en ella.");
    private final TextField reference = field("Selecciona una referencia .vcm");
    private final TextField key = field("Clave pública Ed25519 · DER / SubjectPublicKeyInfo");
    private final Button review = new Button("Revisar firma");
    private final Button approve = new Button("Aprobar identidad…");
    private final Button verify = new Button("Verificar referencia");

    ReferencePanel(Stage owner, Supplier<Path> folder, Consumer<CooperativeTask<?>> submit,
                   Runnable finished, Consumer<VerifyFolder.Result> results, Consumer<String> message) {
        this(folder, submit, finished, results, message,
                (title, extension) -> choose(owner, title, extension), () -> confirm(owner));
    }
    ReferencePanel(Supplier<Path> folder, Consumer<CooperativeTask<?>> submit,
                   Runnable finished, Consumer<VerifyFolder.Result> results, Consumer<String> message,
                   BiFunction<String, String, Path> chooseFile, Supplier<java.util.Optional<String>> confirmIdentity) {
        super(8); this.message = message;
        status.setWrapText(true); verify.getStyleClass().add("primary");
        var chooseReference = new Button("Elegir referencia");
        var chooseKey = new Button("Elegir clave pública");
        getChildren().addAll(line(reference, chooseReference), line(key, chooseKey),
                new FlowPane(10, 8, review, approve, verify), status);
        review.setDisable(true); approve.setDisable(true); verify.setDisable(true);
        chooseReference.setOnAction(e -> {
            var selected = chooseFile.apply("Elegir referencia firmada", "*.vcm");
            if (selected != null) { referencePath = selected; reference.setText(selected.toString()); reset(); }
        });
        chooseKey.setOnAction(e -> {
            var selected = chooseFile.apply("Elegir clave pública Ed25519 DER", "*.der");
            if (selected != null) { keyPath = selected; key.setText(selected.toString()); reset(); }
        });
        review.setOnAction(e -> {
            reviewed = null; approved = null; approve.setDisable(true); verify.setDisable(true);
            var selectedReference = referencePath; var selectedKey = keyPath;
            status.setText("Revisando firma… La identidad todavía no está aprobada.");
            var task = new CooperativeTask<ReferenceReview>() {
                @Override protected ReferenceReview call() throws Exception {
                    if (cancellationRequested()) throw new java.util.concurrent.CancellationException();
                    try (var ref = open(selectedReference); var pub = open(selectedKey)) {
                        return ReferenceReview.read(ref, pub);
                    }
                }
            };
            task.setOnSucceeded(event -> {
                if (task.cancellationRequested()) status.setText("Revisión cancelada. Identidad no aprobada.");
                else {
                    reviewed = task.getValue(); approve.setDisable(false);
                    status.setText("Firma válida con la clave importada · IDENTIDAD NO APROBADA\nSHA-256: "
                            + reviewed.fingerprint() + "\nEntradas: " + reviewed.entries() + " · Omisiones: " + reviewed.omissions());
                }
                message.accept(status.getText()); finished.run();
            });
            task.setOnFailed(event -> { status.setText("Revisión no completada: formato, firma o lectura no válidos, o cancelación. Identidad no aprobada."); message.accept(status.getText()); finished.run(); });
            submit.accept(task);
        });
        approve.setOnAction(e -> {
            if (reviewed == null) return;
            confirmIdentity.get().ifPresent(confirmed -> {
                try {
                    approved = reviewed.approve(confirmed); verify.setDisable(false);
                    status.setText("Identidad aprobada solo para esta selección · SHA-256: " + reviewed.fingerprint()
                            + "\nLa carpeta todavía no se ha comparado.");
                } catch (java.security.GeneralSecurityException error) {
                    approved = null; verify.setDisable(true); status.setText("La huella no coincide. Identidad no aprobada.");
                }
            });
        });
        verify.setOnAction(e -> {
            var selectedFolder = folder.get();
            if (selectedFolder == null) { message.accept("Elige una carpeta antes de verificar la referencia."); return; }
            if (approved == null) return;
            var snapshot = approved;
            var task = new CooperativeTask<VerifyFolder.Result>() {
                @Override protected VerifyFolder.Result call() throws Exception {
                    return snapshot.verify(selectedFolder, new WindowsFolderScanner(), this::cancellationRequested);
                }
            };
            message.accept("Verificando la referencia aprobada en segundo plano…");
            task.setOnSucceeded(event -> {
                if (task.cancellationRequested()) message.accept("Verificación cancelada. Motor detenido; resultados descartados.");
                else results.accept(task.getValue());
                finished.run();
            });
            task.setOnFailed(event -> { message.accept("No se pudo completar la verificación; no se declara integridad."); finished.run(); });
            submit.accept(task);
        });
    }
    private static java.util.Optional<String> confirm(Stage owner) {
        var dialog = new TextInputDialog(); dialog.initOwner(owner);
        dialog.setTitle("Aprobar identidad para esta selección");
        dialog.setHeaderText("Comprueba la huella por un canal independiente");
        dialog.setContentText("Introduce los 64 caracteres SHA-256 confirmados con el propietario.\nNo basta copiar la huella del archivo importado:");
        return dialog.showAndWait();
    }
    private void reset() {
        reviewed = null; approved = null; approve.setDisable(true); verify.setDisable(true);
        review.setDisable(referencePath == null || keyPath == null);
        status.setText("Selección cambiada. Firma pendiente de revisión; identidad no aprobada."); message.accept("Selección cambiada: resultados anteriores descartados. Revisa la firma y la identidad.");
    }
    private static java.io.InputStream open(Path path) throws java.io.IOException {
        var attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new java.io.IOException("Ordinary file required");
        return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
    }
    private static Path choose(Stage owner, String title, String extension) {
        var dialog = new FileChooser(); dialog.setTitle(title);
        dialog.getExtensionFilters().add(new FileChooser.ExtensionFilter(title, extension));
        var chosen = dialog.showOpenDialog(owner); return chosen == null ? null : chosen.toPath();
    }
    private static TextField field(String prompt) { var f = new TextField(); f.setEditable(false); f.setPromptText(prompt); f.setAccessibleText(prompt); return f; }
    private static HBox line(TextField value, Button action) { HBox.setHgrow(value, Priority.ALWAYS); return new HBox(12, value, action); }
}




