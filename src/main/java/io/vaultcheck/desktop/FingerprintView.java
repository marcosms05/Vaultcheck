package io.vaultcheck.desktop;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Public fingerprint only. Copying never approves an identity. */
final class FingerprintView extends HBox {
    private final TextField value = new TextField();
    FingerprintView(String accessibleName) {
        super(12);
        value.setEditable(false); value.setPromptText("Huella SHA-256 disponible tras completar la operación");
        value.setAccessibleText(accessibleName); value.setStyle("-fx-font-family: Consolas;");
        var copy = new Button("Copiar huella");
        copy.disableProperty().bind(value.textProperty().isEmpty());
        copy.setOnAction(event -> {
            var content = new ClipboardContent(); content.putString(value.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        HBox.setHgrow(value, Priority.ALWAYS); getChildren().addAll(value, copy);
    }
    void setFingerprint(String fingerprint) { value.setText(fingerprint); }
}
