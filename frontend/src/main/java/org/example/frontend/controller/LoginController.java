package org.example.frontend.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

  @FXML
  private TextField usernameField;

  @FXML
  private PasswordField passwordField;

  @FXML
  private void onLoginButtonClick(ActionEvent event) {
    String username = usernameField.getText();
    String password = passwordField.getText();

    // TODO: отправить login-запрос на Spring Boot backend

    System.out.println("Logging in with: " + username + " / " + password);
  }

  @FXML
  private void onRegisterLinkClick(ActionEvent event) {
    // TODO: перейти на register.fxml
    System.out.println("Go to register page");
  }
}
