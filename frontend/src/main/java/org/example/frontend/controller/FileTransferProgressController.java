package org.example.frontend.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

public class FileTransferProgressController {
  @FXML
  private ProgressBar progressBar;
  @FXML
  private Label statusLabel;

  public void updateProgress(long chunk, long totalChunks) {
    double progress = (double) chunk / totalChunks;
    progressBar.setProgress(progress);
    statusLabel.setText(String.format("Sending: %.0f%%", progress * 100));
  }

  public void setComplete() {
    progressBar.setProgress(1.0);
    statusLabel.setText("File sent successfully!");
    statusLabel.setStyle("-fx-text-fill: green;");
  }

  public void setError(String message) {
    progressBar.setProgress(0);
    statusLabel.setText("Error: " + message);
    statusLabel.setStyle("-fx-text-fill: red;");
  }
}
