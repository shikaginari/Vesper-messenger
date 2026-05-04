package com.vesper.app.controller;

import com.vesper.app.VesperApp;
import com.vesper.app.model.User;
import com.vesper.app.service.AuthService;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Контроллер окна входа.
 */
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressIndicator progressIndicator;

    @FXML
    private Button registerButton;

    private final AuthService authService = new AuthService();

    // Текущий авторизованный пользователь (упрощённо, как псевдо-сессия)
    private static User currentUser;

    public static User getCurrentUser() {
        return currentUser;
    }

    @FXML
    private void initialize() {
        statusLabel.setText("");
        progressIndicator.setVisible(false);

        loginButton.setOnAction(this::onLoginClicked);
        if (registerButton != null) {
            registerButton.setOnAction(this::onRegisterClicked);
        }
    }

    private void onLoginClicked(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Пожалуйста, заполните все поля.");
            return;
        }

        loginButton.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setText("Выполняется вход...");

        Task<Optional<User>> loginTask = new Task<>() {
            @Override
            protected Optional<User> call() {
                log.info("Асинхронный вход пользователя: {}", username);
                return authService.login(username, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            progressIndicator.setVisible(false);
            loginButton.setDisable(false);
            Optional<User> userOpt = loginTask.getValue();
            if (userOpt.isPresent()) {
                currentUser = userOpt.get();
                statusLabel.setText("Успешный вход. Загрузка мессенджера...");
                VesperApp.switchToMainScene();
            } else {
                statusLabel.setText("Неверное имя пользователя или пароль.");
            }
        });

        loginTask.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            loginButton.setDisable(false);
            statusLabel.setText("Произошла ошибка при входе.");
            log.error("Ошибка при выполнении задачи входа", loginTask.getException());
        });

        new Thread(loginTask, "LoginTaskThread").start();
    }

    /**
     * Открытие модального окна регистрации поверх экрана входа.
     */
    private void onRegisterClicked(ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/fxml/RegisterView.fxml"));
            javafx.scene.Parent root = loader.load();

            RegisterController controller = loader.getController();
            controller.setParentLoginController(this);

            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.setTitle("Регистрация — Vesper");
            dialog.initOwner(loginButton.getScene().getWindow());
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            scene.getStylesheets().add(
                    getClass().getResource("/css/style.css").toExternalForm());

            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.showAndWait();
        } catch (Exception ex) {
            log.error("Ошибка при открытии окна регистрации", ex);
        }
    }

    /**
     * Используется регистрационным окном для установки авторизованного пользователя.
     */
    public static void setCurrentUser(User user) {
        currentUser = user;
    }
}

