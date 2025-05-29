package org.example.frontend;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

public class HelloController {
    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private Button connectButton;

    private Socket connection;
    private PrintWriter out;
    private BufferedReader in;
    private ServerSocket serverSocket;
    private boolean isServerRunning = false;

    @FXML private ProgressBar progressBar;

    private DataOutputStream dataOut;
    private DataInputStream dataIn;
    private FileOutputStream fileOut;
    private volatile boolean isReceivingFile = false;
    private String currentFileName;
    private long currentFileSize;



    // Обработчик кнопки подключения
    @FXML
    private void handleConnect() {
        new Thread(() -> {
            try {
                String ip = ipField.getText();
                int port = Integer.parseInt(portField.getText());

                connection = new Socket(ip, port);
                initializeStreams(connection); // Вынесли инициализацию потоков в отдельный метод

                startReceiverThread();
                updateUI("Connected to " + ip + ":" + port);
            } catch (Exception e) {
                updateUI("Connection error: " + e.getMessage());
            }
        }).start();
    }

    @FXML
    private void handleFileSelect() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            new Thread(() -> sendFile(file)).start();
        }
    }

    // Метод для отправки файла
    private void sendFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            // Отправляем заголовок
            dataOut.writeUTF("FILE_START");
            dataOut.writeUTF(file.getName());
            dataOut.writeLong(file.length());

            // Отправляем содержимое
            byte[] buffer = new byte[4096];
            int read;
            long totalSent = 0;
            while ((read = fis.read(buffer)) != -1) {
                dataOut.write(buffer, 0, read);
                totalSent += read;
                updateProgress((double) totalSent / file.length());
            }
            dataOut.writeUTF("FILE_END");
            updateUI("File sent: " + file.getName());
        } catch (IOException e) {
            updateUI("File send error: " + e.getMessage());
        }
    }

    private void updateProgress(double progress) {
        javafx.application.Platform.runLater(() ->
                progressBar.setProgress(progress)
        );
    }

    private void startReceivingFile() throws IOException {
        isReceivingFile = true;
        currentFileName = dataIn.readUTF();
        currentFileSize = dataIn.readLong();
        fileOut = new FileOutputStream("received_" + currentFileName);
        updateUI("Receiving file: " + currentFileName);
        updateProgress(0);
    }

    private void receiveFileData() {
        try {
            byte[] buffer = new byte[4096];
            long totalReceived = 0;
            while (totalReceived < currentFileSize) {
                int read = dataIn.read(buffer);
                if (read == -1) break;

                fileOut.write(buffer, 0, read);
                totalReceived += read;
                updateProgress((double) totalReceived / currentFileSize);
            }
            fileOut.close();
            isReceivingFile = false;
            updateUI("File received: " + currentFileName);
        } catch (IOException e) {
            updateUI("File receive error: " + e.getMessage());
        }
    }

    // Обработчик отправки сообщения
    @FXML
    private void handleSend() {
        String message = messageField.getText();
        if (dataOut != null && !message.isEmpty()) {
            try {
                dataOut.writeUTF(message);
                updateUI("You: " + message);
                messageField.clear();
            } catch (IOException e) {
                updateUI("Send error: " + e.getMessage());
            }
        }
    }

    // Запуск сервера для входящих соединений
    public void startServer(int port) {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isServerRunning = true;
                updateUI("Server started on port " + port);

                while (isServerRunning) {
                    Socket clientSocket = serverSocket.accept();
                    connection = clientSocket;
                    initializeStreams(clientSocket); // Используем ту же инициализацию потоков

                    updateUI("New connection from: " + clientSocket.getInetAddress());
                    startReceiverThread();
                }
            } catch (IOException e) {
                updateUI("Server error: " + e.getMessage());
            }
        }).start();
    }

    private void initializeStreams(Socket socket) throws IOException {
        dataOut = new DataOutputStream(socket.getOutputStream());
        dataIn = new DataInputStream(socket.getInputStream());
    }

    // Поток для приема сообщений
    private void startReceiverThread() {
        new Thread(() -> {
            try {
                while (true) {
                    if (dataIn == null) {
                        updateUI("Error: Input stream not initialized");
                        Thread.sleep(1000); // Пауза перед повторной проверкой
                        continue;
                    }

                    // Читаем заголовок сообщения
                    String header = dataIn.readUTF();

                    if (header.equals("FILE_START")) {
                        // Начало передачи файла
                        isReceivingFile = true;
                        currentFileName = dataIn.readUTF();
                        currentFileSize = dataIn.readLong();

                        // Создаем файл для записи
                        fileOut = new FileOutputStream("received_" + currentFileName);
                        updateUI("Receiving file: " + currentFileName + " (" + formatFileSize(currentFileSize) + ")");
                        updateProgress(0);

                        // Читаем содержимое файла
                        byte[] buffer = new byte[4096];
                        long totalReceived = 0;
                        while (totalReceived < currentFileSize) {
                            int bytesToRead = (int) Math.min(buffer.length, currentFileSize - totalReceived);
                            int bytesRead = dataIn.read(buffer, 0, bytesToRead);
                            if (bytesRead == -1) break;

                            fileOut.write(buffer, 0, bytesRead);
                            totalReceived += bytesRead;
                            updateProgress((double) totalReceived / currentFileSize);
                        }

                        fileOut.close();
                        isReceivingFile = false;
                        updateUI("File received: " + currentFileName);

                        // Ожидаем подтверждение конца файла
                        String endMarker = dataIn.readUTF();
                        if (!endMarker.equals("FILE_END")) {
                            updateUI("File transfer error: invalid end marker");
                        }
                    } else {
                        // Обычное текстовое сообщение
                        updateUI("Partner: " + header);
                    }
                }
            } catch (SocketException e) {
                updateUI("Connection closed");
            } catch (EOFException e) {
                updateUI("Connection terminated by remote host");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateUI("Receiver thread interrupted");
            } catch (Exception e) {
                updateUI("Receiver error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (fileOut != null) fileOut.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    // Обновление интерфейса из потока
    private void updateUI(String message) {
        javafx.application.Platform.runLater(() ->
                chatArea.appendText(message + "\n")
        );
    }

    // Очистка ресурсов при закрытии
    public void shutdown() {
        try {
            isServerRunning = false;
            if (dataOut != null) dataOut.close();
            if (dataIn != null) dataIn.close();
            if (serverSocket != null) serverSocket.close();
            if (connection != null) connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}