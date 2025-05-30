package org.example.frontend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.extern.slf4j.Slf4j;
import org.example.frontend.manager.SceneManager;
import org.example.frontend.model.RegisterRequest;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

@Slf4j
public class RegisterController {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @FXML
  private TextField usernameField;

  @FXML
  private PasswordField passwordField;

  @FXML
  private PasswordField confirmPasswordField;

  @FXML
  private Label errorLabel;

  @FXML
  private Button registerButton;

  @FXML
  public void initialize() {
    limitTextLength(usernameField, 70);
    limitTextLength(passwordField, 70);
    limitTextLength(confirmPasswordField, 50);
  }

  private void limitTextLength(TextField field, int maxLength) {
    field.setTextFormatter(new TextFormatter<>(change ->
            change.getControlNewText().length() <= maxLength ? change : null));
  }

  @FXML
  private void onRegisterButtonClick(ActionEvent event) {
    String username = usernameField.getText().trim();
    String password = passwordField.getText();
    String confirmPassword = confirmPasswordField.getText();

    if (!validateInputs(username, password, confirmPassword)) {
      return;
    }

    registerButton.setDisable(true);
    hideError();

    sendToServer(username, password);

  }

  private void sendToServer(String username, String password) {
    RegisterRequest registerRequest = RegisterRequest.builder().username(username).password(password).build();

    try {
      String jsonBody = objectMapper.writeValueAsString(registerRequest);

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[]{new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
      }}, new SecureRandom());

      HttpClient client = HttpClient.newBuilder()
              .sslContext(sslContext)
              .connectTimeout(Duration.ofSeconds(10))
              .build();

      HttpRequest httpRequest = HttpRequest.newBuilder()
              .uri(URI.create("https://localhost:8080/auth/register"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

     client
              .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
              .thenAccept(response -> Platform.runLater(() -> {
                registerButton.setDisable(false);
                if (response.statusCode() == 200 || response.statusCode() == 201) {
                  log.info("Successfully registered");
                  try {
                    SceneManager.switchToLoginScene();
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                } else {
                  showError("Error with register: " + response.statusCode());
                }
              }))
              .exceptionally(ex -> {
                Platform.runLater(() -> {
                  registerButton.setDisable(false);
                  showError("Network error " + ex.getMessage());
                });
                return null;
              });

    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (Exception e) {
      showError("Json error " + e.getMessage());
    }

  }

  @FXML
  private void onLoginLinkClick(ActionEvent event) throws IOException {
    SceneManager.switchToLoginScene();
  }

  private boolean validateInputs(String username, String password, String confirmPassword) {
    if (username.isEmpty()) {
      showError("Enter username");
      return false;
    }

    if (username.length() < 3) {
      showError("Username must be at least 3 characters long");
      return false;
    }

    if (password.isEmpty()) {
      showError("Enter password");
      return false;
    }

    if (password.length() < 6) {
      showError("Password must be at least 6 characters long");
      return false;
    }

    if (!password.equals(confirmPassword)) {
      showError("The passwords do not match");
      return false;
    }

    return true;
  }

  private void showError(String message) {
    errorLabel.setText(message);
    errorLabel.setVisible(true);
  }

  private void hideError() {
    errorLabel.setVisible(false);
  }
}