package org.example.frontend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.extern.slf4j.Slf4j;
import org.example.frontend.manager.SceneManager;
import org.example.frontend.model.JwtStorage;
import org.example.frontend.model.LoginRequest;
import org.example.frontend.model.LoginResponse;

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
public class LoginController {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @FXML
  private TextField usernameField;

  @FXML
  private PasswordField passwordField;

  @FXML
  private Button loginButton;

  @FXML
  private Label errorLabel;

  @FXML
  public void initialize() {
    limitTextLength(usernameField, 70);
    limitTextLength(passwordField, 70);
  }

  private void limitTextLength(TextField field, int maxLength) {
    field.setTextFormatter(new TextFormatter<>(
            change -> change.getControlNewText().length() <= maxLength ? change : null));
  }

  @FXML
  private void onLoginButtonClick(ActionEvent event) {
    String username = usernameField.getText();
    String password = passwordField.getText();

    if (!validateInputs(username, password)) {
      return;
    }

    loginButton.setDisable(true);
    hideError();

    LoginRequest loginRequest = LoginRequest.builder().username(username).password(password).build();

    try {
      String jsonBody = objectMapper.writeValueAsString(loginRequest);

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

      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create("https://localhost:8080/auth/login"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();




      client
              .sendAsync(request, HttpResponse.BodyHandlers.ofString())
              .thenAccept(response -> Platform.runLater(() -> {
                loginButton.setDisable(false);
                log.info("STATUS CODE {}", response.statusCode());
                if (response.statusCode() == 200) {
                  try {
                    LoginResponse loginResponse = objectMapper.readValue(response.body(), LoginResponse.class);
                    String token = loginResponse.getToken();

                    if (token == null) {
                      throw new RuntimeException();
                    }

                    JwtStorage.setToken(token);
                    JwtStorage.setUsername(username);

                    SceneManager.switchToMainScene();

                  } catch (JsonProcessingException e) {
                    showError("Error processing token: " + response.statusCode());
                  } catch (IOException e) {
                      throw new RuntimeException(e);
                  } catch (RuntimeException e) {
                    showError("Need registration!");
                  }
                } else {
                  showError("Error with login: " + response.body());
                }
              }))
              .exceptionally(ex -> {
                Platform.runLater(() -> {
                  loginButton.setDisable(false);
                  showError("Network error or need reg: " + ex.getMessage());
                });
                return null;
              });
    } catch (Exception e) {
      loginButton.setDisable(false);
      showError("Json error " + e.getMessage());
    }
  }

  @FXML
  private void onRegisterLinkClick(ActionEvent event) throws IOException {
    SceneManager.switchToRegisterScene();
  }

  private boolean validateInputs(String username, String password) {
    if (username.isEmpty()) {
      showError("Enter username");
      return false;
    }

    if (password.isEmpty()) {
      showError("Enter password");
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
