package com.vesper.app;

import com.vesper.app.dao.DatabaseConfig;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class VesperApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(VesperApp.class);
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        log.info("Запуск приложения Vesper...");
        primaryStage = stage;

        try {
            // Проверка наличия ресурса перед использованием
            var fxmlRes = getClass().getResource("/fxml/LoginView.fxml");
            var cssRes = getClass().getResource("/css/style.css");

            if (fxmlRes == null) throw new RuntimeException("Не найден FXML файл!");

            FXMLLoader loader = new FXMLLoader(fxmlRes);
            Parent root = loader.load();
            Scene scene = new Scene(root);

            if (cssRes != null) {
                scene.getStylesheets().add(cssRes.toExternalForm());
            } else {
                log.warn("Файл стилей не найден, запускаемся без него");
            }

            stage.setTitle("Vesper — Вход");
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            log.error("Ошибка при запуске окна входа", e);
            throw e;
        }
    }

    public static void switchToMainScene() {
        try {
            FXMLLoader loader = new FXMLLoader(VesperApp.class.getResource("/fxml/MainView.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(VesperApp.class.getResource("/css/style.css").toExternalForm());

            primaryStage.setTitle("Vesper — Мессенджер");
            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            primaryStage.centerOnScreen();
        } catch (Exception e) {
            LoggerFactory.getLogger(VesperApp.class).error("Ошибка при переключении на основное окно", e);
        }
    }

    @Override
    public void stop() {
        log.info("Остановка приложения Vesper...");
        DatabaseConfig.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

