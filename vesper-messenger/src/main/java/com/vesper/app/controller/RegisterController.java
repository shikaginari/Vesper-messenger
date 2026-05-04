package com.vesper.app.controller;

import com.vesper.app.VesperApp;
import com.vesper.app.model.User;
import com.vesper.app.service.AuthService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Контроллер модального окна регистрации.
 */
public class RegisterController {

    private static final Logger log = LoggerFactory.getLogger(RegisterController.class);

    @FXML
    private TextField usernameField;

    @FXML
    private TextField displayNameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Button registerButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressIndicator progressIndicator;

    private final AuthService authService = new AuthService();
    private LoginController parentLoginController;

    public void setParentLoginController(LoginController parentLoginController) {
        this.parentLoginController = parentLoginController;
    }

    @FXML
    private void initialize() {
        statusLabel.setText("");
        progressIndicator.setVisible(false);

        registerButton.setOnAction(e -> onRegisterClicked());
        cancelButton.setOnAction(e -> closeWindow());
    }

    private void onRegisterClicked() {
        String username = usernameField.getText().trim();
        String displayName = displayNameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        if (username.isEmpty() || displayName.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            statusLabel.setText("Заполните все поля.");
            return;
        }
        if (!password.equals(confirm)) {
            statusLabel.setText("Пароли не совпадают.");
            return;
        }
        if (password.length() < 6) {
            statusLabel.setText("Пароль должен быть не короче 6 символов.");
            return;
        }

        registerButton.setDisable(true);
        cancelButton.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setText("Создаём аккаунт...");

        Task<User> task = new Task<>() {
            @Override
            protected User call() {
                log.info("Регистрация нового пользователя через модальное окно: {}", username);
                return authService.register(username, displayName, password);
            }
        };

        task.setOnSucceeded(e -> {
            progressIndicator.setVisible(false);
            User newUser = task.getValue();
            statusLabel.setText("Аккаунт создан, вход...");

            // Сразу авторизуем пользователя и открываем основное окно
            LoginController.setCurrentUser(newUser);
            VesperApp.switchToMainScene();
            closeWindow();
        });

        task.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            registerButton.setDisable(false);
            cancelButton.setDisable(false);
            log.error("Ошибка при регистрации пользователя", task.getException());
            statusLabel.setText("Ошибка при регистрации (возможно, такое имя уже существует).");
        });

        new Thread(task, "RegisterUserTask").start();
    }

    private void closeWindow() {
        Stage stage = (Stage) registerButton.getScene().getWindow();
        stage.close();
    }
}

