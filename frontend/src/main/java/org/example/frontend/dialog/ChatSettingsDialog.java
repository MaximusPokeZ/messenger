package org.example.frontend.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.example.frontend.manager.SceneManager;
import org.example.frontend.model.main.ChatRoom;
import org.example.frontend.model.main.ChatSetting;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public class ChatSettingsDialog extends Dialog<ChatSetting> {
  private ComboBox<String> cipherBox;
  private ComboBox<String> keyBitLengthBox;
  private ComboBox<String> cipherModeCombo;
  private ComboBox<String> paddingModeCombo;
  private TextField ivField;
  private Button generateIvButton;

  public ChatSettingsDialog() {
    setTitle("Encryption settings");
    setHeaderText("Select encryption options");

    ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
    getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

    cipherBox = new ComboBox<>();
    cipherBox.getItems().addAll("RC6", "SERPENT", "MAGENTA");
    cipherBox.getSelectionModel().selectFirst();
    cipherBox.getStyleClass().add("input-field");

    keyBitLengthBox = new ComboBox<>();
    keyBitLengthBox.getItems().addAll("128", "192", "256");
    keyBitLengthBox.getSelectionModel().selectFirst();
    keyBitLengthBox.getStyleClass().add("input-field");

    cipherModeCombo = new ComboBox<>();
    cipherModeCombo.getItems().addAll("ECB", "CBC", "PCBC", "CFB", "OFB", "CTR", "RANDOM_DELTA");
    cipherModeCombo.getSelectionModel().selectFirst();
    cipherModeCombo.getStyleClass().add("input-field");

    paddingModeCombo = new ComboBox<>();
    paddingModeCombo.getItems().addAll("ANSI_X923", "ZEROS", "PKCS7", "ISO_10126");
    paddingModeCombo.getSelectionModel().selectFirst();
    paddingModeCombo.getStyleClass().add("input-field");

    ivField = new TextField();
    ivField.setPromptText("Base64 IV");
    ivField.getStyleClass().add("input-field");

    generateIvButton = new Button("Generate IV");
    generateIvButton.setOnAction(e -> generateIv());
    generateIvButton.getStyleClass().add("secondary-button");

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20, 150, 10, 10));

    grid.add(new Label("Cipher:"), 0, 0);
    grid.add(cipherBox, 1, 0);

    grid.add(new Label("KeyBitLength:"), 0, 1);
    grid.add(keyBitLengthBox, 1, 1);

    grid.add(new Label("Cipher mode:"), 0, 2);
    grid.add(cipherModeCombo, 1, 2);

    grid.add(new Label("Padding mode:"), 0, 3);
    grid.add(paddingModeCombo, 1, 3);

    grid.add(new Label("IV (Base64):"), 0, 4);
    grid.add(ivField, 1, 4);
    grid.add(generateIvButton, 2, 4);


    getDialogPane().setContent(grid);

    String stylesPath = Objects.requireNonNull(SceneManager.class.getResource("/css/styles_2.css")).toExternalForm();


    getDialogPane().getStylesheets().add(stylesPath);


    setResultConverter(dialogButton -> {
      if (dialogButton == createButtonType) {
        return new ChatSetting(
                cipherBox.getValue(),
                cipherModeCombo.getValue(),
                paddingModeCombo.getValue(),
                ivField.getText().isEmpty() ? generateRandomIvBase64() : ivField.getText(),
                keyBitLengthBox.getValue()
        );
      }
      return null;
    });

    generateIv();
  }

  private void generateIv() {
    ivField.setText(generateRandomIvBase64());
  }

  private String generateRandomIvBase64() {
    byte[] iv = new byte[16];
    new SecureRandom().nextBytes(iv);
    return Base64.getEncoder().encodeToString(iv);
  }

  public void setInitialSettings(ChatRoom room) {
    cipherBox.setValue(room.getCipher());
    cipherModeCombo.setValue(room.getCipherMode());
    paddingModeCombo.setValue(room.getPaddingMode());
    ivField.setText(room.getIv());
    keyBitLengthBox.setValue(room.getKeyBitLength());
  }
}
