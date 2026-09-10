package io.vaultcheck.desktop;

import io.vaultcheck.infrastructure.manifest.CreateSignedReference;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.*;

/** Existing encrypted identities only; no private key or password is persisted by the UI. */
final class CreateReferencePanel extends VBox {
    private CreateSignedReference.Prepared prepared;
    private Path preparedRoot;
    private Path sessionEncryptedKey, sessionPublicKey;
    void useIdentity(io.vaultcheck.infrastructure.files.CreateLocalIdentity.Created identity) {
        sessionEncryptedKey = identity.encryptedKey(); sessionPublicKey = identity.publicKey();
    }
    private final Label status = new Label("Selecciona una carpeta arriba y prepara su referencia. Solo se admiten recorridos completos.");
    private final Button export = new Button("Firmar y guardar…");
    CreateReferencePanel(Stage owner, Supplier<Path> folder, Consumer<CooperativeTask<?>> submit, Runnable finished) {
        this(owner, folder, submit, finished, (title, pattern, save) -> select(owner, title, pattern, save));
    }
    @FunctionalInterface interface FileSelection { Path select(String title, String pattern, boolean save); }
    CreateReferencePanel(Stage owner, Supplier<Path> folder, Consumer<CooperativeTask<?>> submit,
                         Runnable finished, FileSelection selection) {
        super(10); status.setWrapText(true);
        var prepare = new Button("Preparar referencia");
        export.setDisable(true);
        getChildren().addAll(new FlowPane(12, 8, prepare, export), status);
        prepare.setOnAction(e -> {
            var selected = folder.get();
            prepared = null; preparedRoot = null; export.setDisable(true);
            if (selected == null) { status.setText("Elige primero la carpeta de origen."); return; }
            var task = new CooperativeTask<CreateSignedReference.Prepared>() {
                @Override protected CreateSignedReference.Prepared call() throws Exception {
                    return new CreateSignedReference().prepare(selected, this::cancellationRequested);
                }
            };
            status.setText("Leyendo carpeta en segundo plano…");
            task.setOnSucceeded(event -> {
                if (task.cancellationRequested()) status.setText("Preparación cancelada. No se ha firmado ni guardado nada.");
                else {
                    prepared = task.getValue(); preparedRoot = selected; export.setDisable(false);
                    status.setText("Recorrido completo: " + prepared.entries() + " archivos. Revisa este recuento antes de firmar.\nLa referencia representará las lecturas realizadas; no es una instantánea.");
                }
                finished.run();
            });
            task.setOnFailed(event -> { status.setText("No se pudo preparar un recorrido completo. Revisa accesos y límites; no se firmará una referencia parcial."); finished.run(); });
            submit.accept(task);
        });
        export.setOnAction(e -> {
            if (prepared == null || !preparedRoot.equals(folder.get())) {
                prepared = null; export.setDisable(true); status.setText("La carpeta cambió. Prepara de nuevo la referencia."); return;
            }
            var encrypted = sessionEncryptedKey != null ? sessionEncryptedKey : selection.select("Elegir clave privada cifrada VaultCheck", "*.vckey", false);
            if (encrypted == null) return;
            var publicKey = sessionPublicKey != null ? sessionPublicKey : selection.select("Elegir clave pública Ed25519 DER", "*.der", false);
            if (publicKey == null) return;
            var destination = selection.select("Guardar referencia fuera de la carpeta de origen", "*.vcm", true);
            if (destination == null) return;
            var passwordField = new PasswordField(); passwordField.setAccessibleText("Contraseña de la clave cifrada");
            var dialog = new Dialog<ButtonType>(); dialog.initOwner(owner); dialog.setTitle("Desbloquear para firmar");
            dialog.setHeaderText("Identidad de firma: " + publicKey + "\nLa clave privada debe corresponder a esta clave pública.");
            dialog.getDialogPane().setContent(new VBox(10, new Label("Contraseña de la clave existente (12–1024 caracteres):"), passwordField));
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            var decision = dialog.showAndWait();
            char[] password = passwordField.getText().toCharArray(); passwordField.clear();
            if (decision.orElse(ButtonType.CANCEL) != ButtonType.OK) { Arrays.fill(password, '\0'); return; }
            if (password.length < 12 || password.length > 1024) { Arrays.fill(password, '\0'); status.setText("La contraseña debe tener entre 12 y 1024 caracteres."); return; }
            var snapshot = prepared;
            var task = new CooperativeTask<String>() {
                @Override protected String call() throws Exception {
                    return new CreateSignedReference().export(snapshot, destination, encrypted, publicKey, password, this::cancellationRequested);
                }
            };
            status.setText("Desbloqueando, firmando y publicando… No se reemplazan archivos existentes.");
            task.setOnSucceeded(event -> {
                // Saving can commit just before a cancellation request; report the file that really exists.
                status.setText("Referencia guardada: " + destination + "\nIdentidad SHA-256: " + task.getValue()
                        + "\nConserva la clave pública para verificar. La contraseña ya no se conserva en el formulario.");
                finished.run();
            });
            task.setOnFailed(event -> { status.setText("Exportación no completada o limpieza fallida. Comprueba contraseña, identidad y destino. Puede existir la referencia o un archivo temporal; no se sobrescribe ni se elimina el destino."); finished.run(); });
            try { submit.accept(task); } catch (RuntimeException error) { Arrays.fill(password, '\0'); throw error; }
        });
    }
    private static Path select(Stage owner, String title, String pattern, boolean save) {
        var chooser = new FileChooser(); chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(title, pattern));
        var file = save ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner);
        return file == null ? null : file.toPath();
    }
}


