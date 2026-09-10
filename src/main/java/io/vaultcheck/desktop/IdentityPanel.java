package io.vaultcheck.desktop;

import io.vaultcheck.infrastructure.files.CreateLocalIdentity;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.util.Arrays;
import java.util.function.Consumer;

final class IdentityPanel extends VBox {
    IdentityPanel(Stage owner, Consumer<CooperativeTask<?>> submit, Runnable finished, Consumer<CreateLocalIdentity.Created> created) {
        this(owner, submit, finished, created, () -> {
            var chooser = new DirectoryChooser(); chooser.setTitle("Destino local para una nueva carpeta privada de VaultCheck");
            return chooser.showDialog(owner);
        });
    }
    IdentityPanel(Stage owner, Consumer<CooperativeTask<?>> submit, Runnable finished,
                  Consumer<CreateLocalIdentity.Created> created, java.util.function.Supplier<java.io.File> chooseParent) {
        super(10);
        var status = new Label("Crea una identidad Ed25519 local. La clave privada se guardará cifrada en una carpeta nueva con acceso limitado a tu usuario."); status.setWrapText(true);
        var generate = new Button("Crear identidad cifrada…");
        getChildren().addAll(generate, status);
        generate.setOnAction(event -> {
            var parent = chooseParent.get(); if (parent == null) return;
            var first = new PasswordField(); first.setAccessibleText("Nueva contraseña");
            var second = new PasswordField(); second.setAccessibleText("Repetir contraseña");
            var dialog = new Dialog<ButtonType>(); dialog.initOwner(owner); dialog.setTitle("Proteger nueva identidad");
            dialog.setHeaderText("Guarda esta contraseña: VaultCheck no puede recuperarla.");
            dialog.getDialogPane().setContent(new VBox(10, new Label("Contraseña (12–1024 caracteres)"), first, new Label("Repetir contraseña"), second));
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            var decision = dialog.showAndWait();
            char[] password = first.getText().toCharArray(), repeated = second.getText().toCharArray(); first.clear(); second.clear();
            boolean valid = password.length >= 12 && password.length <= 1024 && Arrays.equals(password, repeated);
            Arrays.fill(repeated, '\0');
            if (decision.orElse(ButtonType.CANCEL) != ButtonType.OK || !valid) {
                Arrays.fill(password, '\0'); status.setText("No se creó la identidad. Las contraseñas deben coincidir y tener entre 12 y 1024 caracteres."); return;
            }
            var task = new CooperativeTask<CreateLocalIdentity.Created>() {
                @Override protected CreateLocalIdentity.Created call() throws Exception {
                    return new CreateLocalIdentity().create(parent.toPath(), password, this::cancellationRequested);
                }
            };
            status.setText("Generando y cifrando… Si comienza el guardado, se completará antes de finalizar.");
            task.setOnSucceeded(e -> {
                var identity = task.getValue(); created.accept(identity);
                status.setText("Identidad guardada en: " + identity.directory() + "\nClave cifrada: " + identity.encryptedKey().getFileName()
                        + "\nClave pública: public.der\nHuella SHA-256: " + identity.fingerprint()
                        + "\nDisponible para firmar en esta sesión. Conserva carpeta y contraseña; comparte solo la clave pública y la huella.");
                finished.run();
            });
            task.setOnFailed(e -> {
                status.setText(task.getException() instanceof java.util.concurrent.CancellationException
                        ? "Creación cancelada antes del guardado."
                        : "No se completó la creación. Puede quedar una carpeta parcial vaultcheck-keys- en el destino. No se ha guardado una clave privada sin cifrar.");
                finished.run();
            });
            try { submit.accept(task); } catch (RuntimeException error) { Arrays.fill(password, '\0'); throw error; }
        });
    }
}

