module org.example.frontend {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires static lombok;
    requires org.slf4j;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires java.sql;

  exports org.example.frontend.app;
    opens org.example.frontend.app to javafx.fxml;
    exports org.example.frontend.controller;
    opens org.example.frontend.controller to javafx.fxml;
}