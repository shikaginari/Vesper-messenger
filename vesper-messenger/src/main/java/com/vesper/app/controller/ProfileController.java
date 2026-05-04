package com.vesper.app.controller;

import com.vesper.app.model.User;
import com.vesper.app.service.ProfileService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Slider;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;

/**
 * Контроллер модального окна профиля.
 */
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    @FXML
    private ImageView avatarView;

    @FXML
    private Button changeAvatarButton;

    @FXML
    private TextField displayNameField;

    @FXML
    private TextArea bioArea;

    @FXML
    private Button saveButton;

    @FXML
    private Button closeButton;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressIndicator progressIndicator;

    @FXML
    private ChoiceBox<String> themeChoiceBox;

    @FXML
    private ColorPicker accentColorPicker;

    @FXML
    private Slider chatOverlayOpacitySlider;

    @FXML
    private Button changeChatBackgroundButton;

    @FXML
    private ImageView chatBackgroundPreview;

    private final ProfileService profileService = new ProfileService();
    private User currentUser;
    private byte[] selectedAvatarBytes;
    private byte[] selectedChatBackgroundBytes;

    private MainController mainController;
    private boolean suppressThemeListener = false;

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
        hydrateFromUser(currentUser);
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        if (mainController != null) {
            // Инициализируем UI профиля текущими настройками из главного окна
            applyMainThemeValuesToControls();
        }
    }

    @FXML
    private void initialize() {
        statusLabel.setText("");
        progressIndicator.setVisible(false);

        changeAvatarButton.setOnAction(e -> chooseAvatar());
        if (changeChatBackgroundButton != null) {
            changeChatBackgroundButton.setOnAction(e -> chooseChatBackground());
        }
        saveButton.setOnAction(e -> saveProfileAsync());
        closeButton.setOnAction(e -> close());

        if (themeChoiceBox != null) {
            themeChoiceBox.getItems().setAll(
                    "Тёмная / Синий",
                    "Тёмная / Зелёный",
                    "Светлая / Синий"
            );

            themeChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (suppressThemeListener) {
                    return;
                }
                if (newV == null || accentColorPicker == null) {
                    return;
                }
                accentColorPicker.setValue(presetAccentForTheme(newV));

                // Делаем "тема + акцент" единым целым: сразу применяем в главное окно.
                if (mainController != null) {
                    mainController.applyTheme(newV);
                    mainController.setAccentColor(presetAccentForTheme(newV));
                    if (chatOverlayOpacitySlider != null) {
                        mainController.setChatOverlayOpacity(chatOverlayOpacitySlider.getValue());
                    }
                }
            });
        }
    }

    private javafx.scene.paint.Color presetAccentForTheme(String themeName) {
        if (themeName == null) {
            return javafx.scene.paint.Color.web("#00BFFF");
        }
        switch (themeName) {
            case "Тёмная / Зелёный":
                return javafx.scene.paint.Color.web("#00FF7F");
            case "Светлая / Синий":
            case "Тёмная / Синий":
            default:
                return javafx.scene.paint.Color.web("#00BFFF");
        }
    }

    private void hydrateFromUser(User user) {
        if (user == null) {
            return;
        }
        displayNameField.setText(user.getDisplayName() != null ? user.getDisplayName() : "");
        bioArea.setText(user.getBio() != null ? user.getBio() : "");
        selectedAvatarBytes = user.getAvatarBytes();
        renderAvatar(selectedAvatarBytes);

        selectedChatBackgroundBytes = user.getChatBackgroundBytes();
        renderChatBackgroundPreview(selectedChatBackgroundBytes);
    }

    private void renderAvatar(byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0) {
                avatarView.setImage(null);
                return;
            }
            Image img = new Image(new java.io.ByteArrayInputStream(bytes));
            avatarView.setImage(img);
        } catch (Exception e) {
            log.warn("Не удалось отрисовать аватар", e);
            avatarView.setImage(null);
        }
    }

    private void renderChatBackgroundPreview(byte[] bytes) {
        try {
            if (chatBackgroundPreview == null) {
                return;
            }
            if (bytes == null || bytes.length == 0) {
                chatBackgroundPreview.setImage(null);
                return;
            }
            chatBackgroundPreview.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
        } catch (Exception e) {
            chatBackgroundPreview.setImage(null);
        }
    }

    private void chooseAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите аватар");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );

        File file = chooser.showOpenDialog(avatarView.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            selectedAvatarBytes = Files.readAllBytes(file.toPath());
            renderAvatar(selectedAvatarBytes);
            statusLabel.setText("Аватар выбран. Не забудьте сохранить.");
        } catch (Exception e) {
            log.error("Ошибка чтения файла аватара", e);
            statusLabel.setText("Не удалось прочитать файл аватара.");
        }
    }

    private void chooseChatBackground() {
        if (currentUser == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите фон чата");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );

        File file = chooser.showOpenDialog(chatBackgroundPreview != null ? chatBackgroundPreview.getScene().getWindow() : null);
        if (file == null) {
            return;
        }

        try {
            selectedChatBackgroundBytes = Files.readAllBytes(file.toPath());
            renderChatBackgroundPreview(selectedChatBackgroundBytes);
            statusLabel.setText("Фон выбран. Не забудьте сохранить.");
        } catch (Exception e) {
            log.error("Ошибка чтения файла фона чата", e);
            statusLabel.setText("Не удалось прочитать файл фона.");
        }
    }

    private void saveProfileAsync() {
        if (currentUser == null || currentUser.getId() == null) {
            statusLabel.setText("Пользователь не определён.");
            return;
        }

        String displayName = displayNameField.getText().trim();
        String bio = bioArea.getText() != null ? bioArea.getText().trim() : null;

        if (displayName.isEmpty()) {
            statusLabel.setText("Отображаемое имя не может быть пустым.");
            return;
        }

        setBusy(true, "Сохранение профиля...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                profileService.updateProfile(currentUser.getId(), displayName, bio, selectedAvatarBytes);

                // Сохраняем фон чата, если он был выбран/изменён
                if (selectedChatBackgroundBytes != null) {
                    profileService.updateChatBackground(currentUser.getId(), selectedChatBackgroundBytes);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false, "Профиль сохранён.");
            // Обновим текущего пользователя в памяти
            currentUser.setDisplayName(displayName);
            currentUser.setBio(bio);
            currentUser.setAvatarBytes(selectedAvatarBytes);

            currentUser.setChatBackgroundBytes(selectedChatBackgroundBytes);

            // Применяем тему/акценты/opacity в главном окне
            applyControlsToMain();
        });

        task.setOnFailed(e -> {
            setBusy(false, "Ошибка при сохранении профиля.");
            log.error("Ошибка сохранения профиля", task.getException());
        });

        new Thread(task, "SaveProfileTask").start();
    }

    private void applyMainThemeValuesToControls() {
        if (mainController == null) {
            return;
        }
        if (themeChoiceBox != null) {
            String cur = mainController.getCurrentThemeName();
            suppressThemeListener = true;
            try {
                themeChoiceBox.getSelectionModel().select(cur != null ? cur : "Тёмная / Синий");
            } finally {
                suppressThemeListener = false;
            }
        }
        if (accentColorPicker != null && mainController.getCurrentAccentColor() != null) {
            accentColorPicker.setValue(mainController.getCurrentAccentColor());
        }
        if (chatOverlayOpacitySlider != null) {
            chatOverlayOpacitySlider.setValue(mainController.getChatOverlayOpacity());
        }
    }

    private void applyControlsToMain() {
        if (mainController == null) {
            return;
        }

        if (themeChoiceBox != null && themeChoiceBox.getSelectionModel().getSelectedItem() != null) {
            mainController.applyTheme(themeChoiceBox.getSelectionModel().getSelectedItem());
        }

        if (accentColorPicker != null && accentColorPicker.getValue() != null) {
            mainController.setAccentColor(accentColorPicker.getValue());
        }

        if (chatOverlayOpacitySlider != null) {
            mainController.setChatOverlayOpacity(chatOverlayOpacitySlider.getValue());
        }

        // Применяем новый фон (мгновенно)
        mainController.applyChatBackgroundBytes(selectedChatBackgroundBytes);
    }

    private void setBusy(boolean busy, String status) {
        progressIndicator.setVisible(busy);
        saveButton.setDisable(busy);
        closeButton.setDisable(busy);
        changeAvatarButton.setDisable(busy);
        if (changeChatBackgroundButton != null) {
            changeChatBackgroundButton.setDisable(busy);
        }
        statusLabel.setText(status != null ? status : "");
    }

    private void close() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}

