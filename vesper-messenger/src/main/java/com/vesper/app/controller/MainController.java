package com.vesper.app.controller;

import com.vesper.app.model.Message;
import com.vesper.app.model.User;
import com.vesper.app.dao.UserDAO;
import com.vesper.app.service.ChatService;
import com.vesper.app.service.PresenceService;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.vesper.app.service.PostgresRealtimeService;
import com.vesper.app.service.ProfileService;
import java.io.File;
import java.nio.file.Files;

/**
 * Контроллер основного окна мессенджера.
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML
    private ListView<User> contactsListView;

    @FXML
    private VBox messagesContainer;

    @FXML
    private ScrollPane messagesScrollPane;

    @FXML
    private TextArea messageInputArea;

    @FXML
    private Button sendButton;

    @FXML
    private Label headerTitleLabel;

    @FXML
    private Label headerSubtitleLabel;

    @FXML
    private ProgressIndicator loadingIndicator;

    @FXML
    private Button profileButton;

    @FXML
    private ImageView currentUserAvatarView;

    @FXML
    private ImageView currentContactAvatarView;

    @FXML
    private Label typingLabel;

    @FXML
    private TextField findContactField;

    @FXML
    private Button findContactButton;

    @FXML
    private StackPane chatStackPane;

    @FXML
    private ImageView chatBackgroundImageView;

    @FXML
    private Rectangle chatBackgroundOverlay;

    private Color accentColor = Color.web("#00BFFF");
    private double chatOverlayOpacity = 0.55;
    private String themeName = "Тёмная / Синий";

    private final ChatService chatService = new ChatService();
    private final UserDAO userDAO = new UserDAO();
    private final ProfileService profileService = new ProfileService();
    private final ObservableList<User> contacts = FXCollections.observableArrayList();
    private volatile User currentContact;
    private User currentUser;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private volatile long lastMessageIdInCurrentChat = 0L;

    private final Map<Long, Integer> unreadCounts = new ConcurrentHashMap<>();

    private PostgresRealtimeService realtimeService;
    private ScheduledExecutorService typingHideExecutor;
    private final PresenceService presenceService = new PresenceService();
    private ScheduledExecutorService presenceExecutor;
    private ScheduledExecutorService contactsOnlineRefreshExecutor;
    private volatile long lastTypingTsMs = 0L;
    private volatile long lastTypingSenderId = -1L;
    private volatile long lastTypingSentAtMs = 0L;

    private volatile Long pendingSelectContactId = null;

    @FXML
    private void initialize() {
        this.currentUser = LoginController.getCurrentUser();
        if (currentUser != null) {
            headerTitleLabel.setText("Vesper");
            headerSubtitleLabel.setText("Вы вошли как: " + currentUser.getDisplayName());
            renderCurrentUserAvatar();
        } else {
            headerTitleLabel.setText("Vesper");
            headerSubtitleLabel.setText("Пользователь не определён");
        }

        loadingIndicator.setVisible(false);

        if (profileButton != null) {
            profileButton.setOnAction(e -> openProfileDialog());
        }

        if (chatBackgroundOverlay != null) {
            chatBackgroundOverlay.setOpacity(chatOverlayOpacity);
        }

        // Применяем акцентный цвет для уже загруженных пузырей
        applyAccentColorToUI(accentColor);

        contactsListView.setItems(contacts);
        contactsListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    getStyleClass().remove("contact-list-cell");
                } else {
                    if (!getStyleClass().contains("contact-list-cell")) {
                        getStyleClass().add("contact-list-cell");
                    }

                    Label nameLabel = new Label(item.getDisplayName());
                    nameLabel.getStyleClass().add("contact-name");

                    ImageView avatarMini = new ImageView();
                    avatarMini.setFitWidth(18);
                    avatarMini.setFitHeight(18);
                    avatarMini.setPickOnBounds(true);
                    avatarMini.setPreserveRatio(true);
                    avatarMini.getStyleClass().add("contact-avatar-mini-view");
                    if (item.getAvatarBytes() != null && item.getAvatarBytes().length > 0) {
                        try {
                            avatarMini.setImage(new Image(new java.io.ByteArrayInputStream(item.getAvatarBytes())));
                        } catch (Exception ignore) {
                            avatarMini.setImage(null);
                        }
                    }

                    int unread = unreadCounts.getOrDefault(item.getId(), 0);
                    Label badge = new Label(unread > 0 ? String.valueOf(unread) : "");
                    badge.getStyleClass().add("unread-badge");
                    badge.setVisible(unread > 0);

                    Region onlineDot = new Region();
                    onlineDot.setPrefSize(8, 8);
                    onlineDot.getStyleClass().add("online-dot");
                    onlineDot.getStyleClass().add(item.isOnline() ? "online" : "offline");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    HBox box = new HBox(8.0, onlineDot, avatarMini, nameLabel, spacer, badge);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(box);
                }
            }
        });

        contactsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                User previous = currentContact;
                currentContact = newVal;

                if (typingLabel != null) {
                    typingLabel.setText("");
                    typingLabel.setVisible(false);
                }

                renderSelectedContactHeader();

                // Если контакт тот же (например, при обновлении онлайна обновился объект),
                // не перезагружаем историю, чтобы не было "подвисаний".
                if (previous != null && previous.getId() != null && previous.getId().equals(newVal.getId())) {
                    return;
                }

                loadConversationAsync();
            }
        });

        sendButton.disableProperty().bind(
                Bindings.or(
                        contactsListView.getSelectionModel().selectedItemProperty().isNull(),
                        messageInputArea.textProperty().isEmpty()
                )
        );

        sendButton.setOnAction(e -> sendMessageAsync());

        if (findContactButton != null) {
            findContactButton.setOnAction(e -> onFindContactClicked());
        }

        if (typingLabel != null) {
            typingLabel.setText("");
            typingLabel.setVisible(false);
        }

        messageInputArea.textProperty().addListener((obs, oldText, newText) -> onTypingTextChanged(newText));

        loadContactsAsync();
        startRealtimeListener();
        startPresenceHeartbeat();
        startContactsOnlineRefresh();
        initChatBackgroundLayer();
    }

    private void initChatBackgroundLayer() {
        if (chatStackPane == null || chatBackgroundImageView == null || chatBackgroundOverlay == null) {
            return;
        }

        // Покрываем весь слой чата изображением + затемнением поверх него.
        chatBackgroundImageView.setPreserveRatio(false);
        chatBackgroundImageView.fitWidthProperty().bind(chatStackPane.widthProperty());
        chatBackgroundImageView.fitHeightProperty().bind(chatStackPane.heightProperty());

        chatBackgroundOverlay.widthProperty().bind(chatStackPane.widthProperty());
        chatBackgroundOverlay.heightProperty().bind(chatStackPane.heightProperty());

        chatBackgroundOverlay.setVisible(true);
        chatBackgroundOverlay.setOpacity(chatOverlayOpacity);
        // Затемнение всегда полупрозрачным тёмным, чтобы картинка не "исчезала" под белой пеленой.
        chatBackgroundOverlay.setFill(Color.web("#121212"));

        renderChatBackgroundFromCurrentUser();
    }

    private void renderChatBackgroundFromCurrentUser() {
        if (chatBackgroundImageView == null) {
            return;
        }
        try {
            byte[] bytes = currentUser != null ? currentUser.getChatBackgroundBytes() : null;
            if (bytes == null || bytes.length == 0) {
                chatBackgroundImageView.setImage(null);
                chatBackgroundImageView.setVisible(false);
                chatBackgroundOverlay.setVisible(true);
                chatBackgroundOverlay.setOpacity(chatOverlayOpacity);
                chatBackgroundOverlay.setFill(Color.web("#121212"));
                return;
            }
            chatBackgroundImageView.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
            chatBackgroundImageView.setVisible(true);
        } catch (Exception e) {
            log.warn("Не удалось отрисовать фон чата", e);
            chatBackgroundImageView.setImage(null);
            chatBackgroundImageView.setVisible(false);
        }
    }

    public void setChatOverlayOpacity(double opacity) {
        double clamped = Math.max(0.1, Math.min(1.0, opacity));
        this.chatOverlayOpacity = clamped;
        if (chatBackgroundOverlay != null) {
            chatBackgroundOverlay.setOpacity(clamped);
        }
    }

    public void setAccentColor(Color newAccent) {
        if (newAccent == null) {
            return;
        }
        this.accentColor = newAccent;
        applyAccentColorToUI(this.accentColor);
    }

    public void applyChatBackgroundBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            if (chatBackgroundImageView != null) {
                chatBackgroundImageView.setImage(null);
                chatBackgroundImageView.setVisible(false);
            }
            if (chatBackgroundOverlay != null) {
                chatBackgroundOverlay.setVisible(true);
                chatBackgroundOverlay.setOpacity(chatOverlayOpacity);
            }
            if (currentUser != null) {
                currentUser.setChatBackgroundBytes(null);
            }
            return;
        }

        if (chatBackgroundImageView != null) {
            chatBackgroundImageView.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
            chatBackgroundImageView.setVisible(true);
        }

        if (currentUser != null) {
            currentUser.setChatBackgroundBytes(bytes);
        }
    }

    public String getCurrentThemeName() {
        return themeName;
    }

    public Color getCurrentAccentColor() {
        return getAccentOrDefault();
    }

    public double getChatOverlayOpacity() {
        return chatOverlayOpacity;
    }

    private void openChatBackgroundChooser() {
        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите фон чата");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );

        File file = chooser.showOpenDialog(chatStackPane.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            chatBackgroundImageView.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
            chatBackgroundImageView.setVisible(true);

            long userId = currentUser.getId();
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    profileService.updateChatBackground(userId, bytes);
                    return null;
                }
            };

            task.setOnFailed(e -> log.error("Не удалось сохранить фон чата", task.getException()));
            new Thread(task, "SaveChatBackgroundTask").start();

            // обновим объект текущего пользователя в памяти
            currentUser.setChatBackgroundBytes(bytes);
        } catch (Exception e) {
            log.error("Ошибка при выборе фона чата", e);
        }
    }

    private void renderCurrentUserAvatar() {
        if (currentUserAvatarView == null) {
            return;
        }
        try {
            byte[] bytes = currentUser != null ? currentUser.getAvatarBytes() : null;
            if (bytes == null || bytes.length == 0) {
                currentUserAvatarView.setImage(null);
                return;
            }
            currentUserAvatarView.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
        } catch (Exception e) {
            log.warn("Не удалось отрисовать аватар текущего пользователя", e);
            currentUserAvatarView.setImage(null);
        }
    }

    private void renderSelectedContactHeader() {
        if (currentContactAvatarView != null) {
            try {
                byte[] bytes = currentContact != null ? currentContact.getAvatarBytes() : null;
                if (bytes == null || bytes.length == 0) {
                    currentContactAvatarView.setImage(null);
                } else {
                    currentContactAvatarView.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
                }
            } catch (Exception e) {
                currentContactAvatarView.setImage(null);
            }
        }

        if (currentContact == null) {
            headerSubtitleLabel.setText("Выберите контакт");
            return;
        }

        String status = currentContact.isOnline() ? " (онлайн)" : " (офлайн)";
        headerSubtitleLabel.setText("Чат с: " + currentContact.getDisplayName() + status);
    }

    private void openProfileDialog() {
        if (currentUser == null) {
            return;
        }
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/fxml/ProfileView.fxml"));
            javafx.scene.Parent root = loader.load();

            ProfileController controller = loader.getController();
            controller.setCurrentUser(currentUser);
            controller.setMainController(this);

            Stage dialog = new Stage();
            dialog.setTitle("Профиль — Vesper");
            dialog.initOwner(sendButton.getScene().getWindow());
            dialog.initModality(Modality.APPLICATION_MODAL);

            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            dialog.setScene(scene);
            dialog.setResizable(false);

            dialog.showAndWait();

            // После закрытия окна профиля обновим заголовок/аватар в шапке
            headerSubtitleLabel.setText("Вы вошли как: " + currentUser.getDisplayName());
            renderCurrentUserAvatar();
        } catch (Exception e) {
            log.error("Ошибка при открытии окна профиля", e);
        }
    }

    private void onFindContactClicked() {
        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        if (findContactField == null) {
            return;
        }

        String raw = findContactField.getText() != null ? findContactField.getText().trim() : "";
        if (raw.isEmpty()) {
            return;
        }

        findContactButton.setDisable(true);
        loadingIndicator.setVisible(true);

        Task<User> task = new Task<>() {
            @Override
            protected User call() {
                try {
                    String value = raw;
                    if (value.startsWith("@")) {
                        value = value.substring(1);
                    }

                    // Если число — ищем по id, иначе по username
                    if (value.matches("\\d+")) {
                        long id = Long.parseLong(value);
                        return userDAO.findById(id).orElse(null);
                    }
                    return userDAO.findByUsername(value).orElse(null);
                } catch (Exception e) {
                    return null;
                }
            }
        };

        task.setOnSucceeded(e -> {
            loadingIndicator.setVisible(false);
            findContactButton.setDisable(false);

            User found = task.getValue();
            if (found == null) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Контакт не найден");
                alert.setHeaderText(null);
                alert.setContentText("Не удалось найти пользователя по введённому значению.");
                alert.showAndWait();
                return;
            }

            chatService.addContact(currentUser.getId(), found.getId());
            pendingSelectContactId = found.getId();
            findContactField.clear();
            loadContactsAsync();
        });

        task.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            findContactButton.setDisable(false);
            Throwable ex = task.getException();
            log.error("Ошибка при поиске контакта", ex);
        });

        new Thread(task, "FindContactTask").start();
    }

    private void loadContactsAsync() {
        if (currentUser == null) {
            log.warn("Текущий пользователь не установлен, контакты не будут загружены.");
            return;
        }

        loadingIndicator.setVisible(true);

        Task<List<User>> task = new Task<>() {
            @Override
            protected List<User> call() {
                log.info("Асинхронная загрузка списка контактов...");
                unreadCounts.clear();
                List<User> list = chatService.loadContactsForUser(currentUser.getId());
                for (User u : list) {
                    if (u.getId() != null) {
                        unreadCounts.put(u.getId(), chatService.countUnread(currentUser.getId(), u.getId()));
                    }
                }
                return list;
            }
        };

        task.setOnSucceeded(e -> {
            loadingIndicator.setVisible(false);
            contacts.setAll(task.getValue());
            contactsListView.refresh();

            // Если контакт был добавлен/найден, выделим его в списке и сразу откроем диалог.
            if (pendingSelectContactId != null) {
                Long id = pendingSelectContactId;
                pendingSelectContactId = null;
                for (User u : contacts) {
                    if (u.getId() != null && u.getId().equals(id)) {
                        contactsListView.getSelectionModel().select(u);
                        currentContact = u;
                        renderSelectedContactHeader();
                        break;
                    }
                }
            }
        });

        task.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            log.error("Ошибка при загрузке списка контактов", task.getException());
        });

        new Thread(task, "ContactsLoadTask").start();
    }

    private void loadConversationAsync() {
        if (currentUser == null || currentContact == null) {
            return;
        }

        loadingIndicator.setVisible(true);
        messagesContainer.getChildren().clear();
        lastMessageIdInCurrentChat = 0L;

        Label placeholder = new Label("Сообщений пока нет. Напишите первым!");
        placeholder.getStyleClass().add("messages-placeholder");
        messagesContainer.getChildren().add(placeholder);

        Task<List<Message>> task = new Task<>() {
            @Override
            protected List<Message> call() {
                log.info("Асинхронная загрузка истории сообщений...");
                return chatService.loadConversation(currentUser.getId(), currentContact.getId());
            }
        };

        task.setOnSucceeded(e -> {
            loadingIndicator.setVisible(false);
            List<Message> msgs = task.getValue();

            messagesContainer.getChildren().clear();

            if (msgs == null || msgs.isEmpty()) {
                messagesContainer.getChildren().add(placeholder);
                scrollToBottom();
                // При пустой истории тоже помечаем диалог как прочитанный
                Task<Void> markReadTask = new Task<>() {
                    @Override
                    protected Void call() {
                        chatService.markConversationRead(currentUser.getId(), currentContact.getId(), 0L);
                        return null;
                    }
                };
                markReadTask.setOnSucceeded(ev -> {
                    unreadCounts.put(currentContact.getId(), 0);
                    contactsListView.refresh();
                });
                new Thread(markReadTask, "MarkReadTaskEmpty").start();
                return;
            }

            msgs.forEach(this::addMessageBubble);
            for (Message m : msgs) {
                if (m.getId() != null && m.getId() > lastMessageIdInCurrentChat) {
                    lastMessageIdInCurrentChat = m.getId();
                }
            }

            long lastReadId = lastMessageIdInCurrentChat;
            // Сразу помечаем диалог прочитанным, чтобы непрочитанные не копились.
            Task<Void> markReadTask = new Task<>() {
                @Override
                protected Void call() {
                    chatService.markConversationRead(currentUser.getId(), currentContact.getId(), lastReadId);
                    return null;
                }
            };
            markReadTask.setOnSucceeded(ev -> {
                unreadCounts.put(currentContact.getId(), 0);
                contactsListView.refresh();
            });
            new Thread(markReadTask, "MarkReadTask").start();
            scrollToBottom();
        });

        task.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            log.error("Ошибка при загрузке истории сообщений", task.getException());
        });

        new Thread(task, "ConversationLoadTask").start();
    }

    private void sendMessageAsync() {
        if (currentUser == null || currentContact == null) {
            return;
        }

        String text = messageInputArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        String toSend = text;
        messageInputArea.clear();

        Task<Message> task = new Task<>() {
            @Override
            protected Message call() {
                log.info("Асинхронная отправка сообщения...");
                return chatService.sendMessage(currentUser.getId(), currentContact.getId(), toSend);
            }
        };

        task.setOnSucceeded(e -> {
            Message msg = task.getValue();
            addMessageBubble(msg);
            if (msg.getId() != null && msg.getId() > lastMessageIdInCurrentChat) {
                lastMessageIdInCurrentChat = msg.getId();
            }

            if (msg.getId() != null) {
                long readId = msg.getId();
                Task<Void> markReadTask = new Task<>() {
                    @Override
                    protected Void call() {
                        chatService.markConversationRead(currentUser.getId(), currentContact.getId(), readId);
                        return null;
                    }
                };
                markReadTask.setOnSucceeded(ev -> {
                    unreadCounts.put(currentContact.getId(), 0);
                    contactsListView.refresh();
                });
                new Thread(markReadTask, "MarkReadTaskSend").start();
            }
            scrollToBottom();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            log.error("Ошибка при отправке сообщения", ex);
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Ошибка отправки сообщения");
                alert.setHeaderText("Не удалось отправить сообщение");
                alert.setContentText("Проверьте подключение к базе данных и наличие таблицы messages.\n" +
                        "Техническая ошибка: " + (ex != null ? ex.getClass().getSimpleName() : "неизвестно"));
                alert.showAndWait();
            });
        });

        new Thread(task, "SendMessageTask").start();
    }

    private static final String MESSAGES_CHANNEL = "vesper_messages";
    private static final String TYPING_CHANNEL = "vesper_typing";

    private void startRealtimeListener() {
        if (realtimeService != null) {
            return;
        }

        realtimeService = new PostgresRealtimeService(MESSAGES_CHANNEL, TYPING_CHANNEL);
        realtimeService.start(
                (senderId, recipientId, messageId) -> handleRealtimeMessage(senderId, recipientId, messageId),
                (senderId, recipientId, timestampMillis, isTyping) -> handleRealtimeTyping(senderId, recipientId, timestampMillis, isTyping)
        );

        // Авто-остановка при закрытии сцены
        messagesScrollPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                stopRealtimeListener();
            }
        });
    }

    private void stopRealtimeListener() {
        if (realtimeService != null) {
            realtimeService.close();
            realtimeService = null;
        }
        if (typingHideExecutor != null) {
            typingHideExecutor.shutdownNow();
            typingHideExecutor = null;
        }
        if (presenceExecutor != null) {
            presenceExecutor.shutdownNow();
            presenceExecutor = null;
        }
        if (contactsOnlineRefreshExecutor != null) {
            contactsOnlineRefreshExecutor.shutdownNow();
            contactsOnlineRefreshExecutor = null;
        }
    }

    private void startPresenceHeartbeat() {
        if (presenceExecutor != null) {
            return;
        }
        presenceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PresenceHeartbeat");
            t.setDaemon(true);
            return t;
        });

        // Обновляем "last_seen_at" каждые 8 секунд.
        presenceExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (currentUser == null || currentUser.getId() == null) {
                    return;
                }
                presenceService.touch(currentUser.getId());
            } catch (Exception e) {
                log.debug("Не удалось обновить присутствие", e);
            }
        }, 0, 8, TimeUnit.SECONDS);
    }

    private void startContactsOnlineRefresh() {
        if (contactsOnlineRefreshExecutor != null) {
            return;
        }
        contactsOnlineRefreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ContactsOnlineRefresh");
            t.setDaemon(true);
            return t;
        });

        // Подгружаем онлайн-статус контактов каждые 5 секунд.
        contactsOnlineRefreshExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (currentUser == null || currentUser.getId() == null) {
                    return;
                }

                Long activeId = currentContact != null ? currentContact.getId() : null;
                List<User> list = chatService.loadContactsForUser(currentUser.getId());

                Platform.runLater(() -> {
                    contacts.setAll(list);

                    if (activeId != null) {
                        User selected = null;
                        for (User u : contacts) {
                            if (activeId.equals(u.getId())) {
                                selected = u;
                                break;
                            }
                        }
                        if (selected != null) {
                            currentContact = selected;
                            renderSelectedContactHeader();

                            // Сохраняем выбранный элемент в ListView, чтобы чат не "терял" выбор.
                            contactsListView.getSelectionModel().select(selected);
                        }
                    }

                    contactsListView.refresh();
                });
            } catch (Exception e) {
                log.debug("Ошибка при обновлении онлайн-статуса контактов", e);
            }
        }, 1, 15, TimeUnit.SECONDS);
    }

    private void handleRealtimeMessage(long senderId, long recipientId, long messageId) {
        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        long myId = currentUser.getId();
        if (senderId != myId && recipientId != myId) {
            return;
        }

        long otherId = senderId == myId ? recipientId : senderId;

        Message msg = chatService.findMessageById(messageId);
        if (msg == null) {
            return;
        }

        User contactSnapshot = currentContact;
        boolean isCurrentChat = contactSnapshot != null && contactSnapshot.getId() != null && contactSnapshot.getId().equals(otherId);

        if (isCurrentChat) {
            if (msg.getId() != null && msg.getId() <= lastMessageIdInCurrentChat) {
                return;
            }

            addMessageBubble(msg);
            if (msg.getId() != null && msg.getId() > lastMessageIdInCurrentChat) {
                lastMessageIdInCurrentChat = msg.getId();
            }
            scrollToBottom();

            if (msg.getId() != null) {
                Task<Void> markReadTask = new Task<>() {
                    @Override
                    protected Void call() {
                        chatService.markConversationRead(myId, otherId, msg.getId());
                        return null;
                    }
                };
                markReadTask.setOnSucceeded(ev -> {
                    unreadCounts.put(otherId, 0);
                    contactsListView.refresh();
                });
                new Thread(markReadTask, "MarkReadRealtime").start();
            }
        } else {
            int unread = chatService.countUnread(myId, otherId);
            unreadCounts.put(otherId, unread);
            Platform.runLater(() -> contactsListView.refresh());
        }
    }

    private void handleRealtimeTyping(long senderId, long recipientId, long timestampMillis, boolean isTyping) {
        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        long myId = currentUser.getId();
        if (recipientId != myId) {
            return;
        }

        User contactSnapshot = currentContact;
        boolean isCurrentChat = contactSnapshot != null && contactSnapshot.getId() != null && contactSnapshot.getId().equals(senderId);
        if (!isCurrentChat) {
            return;
        }

        lastTypingTsMs = timestampMillis;
        lastTypingSenderId = senderId;

        Platform.runLater(() -> {
            if (isTyping) {
                typingLabel.setVisible(true);
                String name = contactSnapshot.getDisplayName() != null ? contactSnapshot.getDisplayName() : "Пользователь";
                typingLabel.setText(name + " печатает...");
            } else {
                typingLabel.setText("");
                typingLabel.setVisible(false);
            }
        });

        if (isTyping) {
            scheduleTypingHide(timestampMillis, senderId);
        }
    }

    private void scheduleTypingHide(long timestampMillis, long senderId) {
        if (typingHideExecutor == null) {
            typingHideExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TypingHide");
                t.setDaemon(true);
                return t;
            });
        }

        typingHideExecutor.schedule(() -> {
            // Если за время задержки пришло новое typing-сообщение, не прячем.
            if (System.currentTimeMillis() - timestampMillis < 2500) {
                return;
            }
            if (currentContact != null && currentContact.getId() != null && currentContact.getId().equals(senderId)) {
                Platform.runLater(() -> {
                    typingLabel.setText("");
                    typingLabel.setVisible(false);
                });
            }
        }, 3, TimeUnit.SECONDS);
    }

    private void onTypingTextChanged(String newText) {
        if (currentUser == null || currentUser.getId() == null || currentContact == null || currentContact.getId() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean typing = newText != null && !newText.trim().isEmpty();

        // Троттлинг, чтобы не спамить БД notify.
        if (typing) {
            if (now - lastTypingSentAtMs < 1200) {
                return;
            }
            chatService.sendTypingEvent(currentUser.getId(), currentContact.getId(), true);
            lastTypingSentAtMs = now;
        } else {
            if (now - lastTypingSentAtMs < 1500) {
                return;
            }
            chatService.sendTypingEvent(currentUser.getId(), currentContact.getId(), false);
            lastTypingSentAtMs = now;
        }
    }

    private Color getAccentOrDefault() {
        return accentColor != null ? accentColor : Color.web("#00BFFF");
    }

    private boolean isLightThemeActive(String activeThemeName) {
        if (activeThemeName == null) {
            return false;
        }
        return activeThemeName.startsWith("Светлая");
    }

    private String toHex(Color c) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private String toRgba(Color c, double alpha) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        double a = Math.max(0, Math.min(1, alpha));
        return "rgba(" + r + "," + g + "," + b + "," + a + ")";
    }

    private void applyAccentColorToUI(Color accent) {
        if (accent == null) {
            return;
        }

        String hex = toHex(accent);

        if (headerTitleLabel != null) {
            headerTitleLabel.setStyle("-fx-text-fill: " + hex + ";");
        }
        if (typingLabel != null) {
            typingLabel.setStyle("-fx-text-fill: " + hex + ";");
        }
        if (sendButton != null) {
            // Inline стиль переопределяет CSS и гарантирует читаемость
            sendButton.setStyle("-fx-background-color: " + hex + "; -fx-text-fill: black;");
        }

        refreshExistingMessageBubblesAccent();
    }

    private void refreshExistingMessageBubblesAccent() {
        if (messagesContainer == null) {
            return;
        }
        Color accent = getAccentOrDefault();
        boolean light = isLightThemeActive(themeName);

        double inAlpha = light ? 0.22 : 0.35;
        double outAlpha = light ? 0.55 : 0.72;
        String inText = light ? "#263238" : "#ECEFF1";

        for (Node node : messagesContainer.getChildren()) {
            if (!(node instanceof HBox wrapper)) {
                continue;
            }
            for (Node child : wrapper.getChildren()) {
                if (child instanceof VBox box && !box.getChildren().isEmpty() && box.getChildren().get(0) instanceof Label lbl) {
                    boolean out = lbl.getStyleClass().contains("message-bubble-out");
                    if (out) {
                        lbl.setStyle("-fx-background-color: " + toRgba(accent, outAlpha) + "; -fx-text-fill: black;");
                    } else {
                        lbl.setStyle("-fx-background-color: " + toRgba(accent, inAlpha) + "; -fx-text-fill: " + inText + ";");
                    }
                }
            }
        }
    }

    public void applyTheme(String newThemeName) {
        if (newThemeName == null) {
            return;
        }
        this.themeName = newThemeName;

        try {
            javafx.scene.Scene scene = chatStackPane != null ? chatStackPane.getScene() : null;
            if (scene == null) {
                return;
            }
            scene.getStylesheets().clear();

            // Всегда загружаем базовый стиль + поверх него тему.
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            String overrideCss = null;
            switch (newThemeName) {
                case "Тёмная / Зелёный":
                    overrideCss = "/css/style-dark-green.css";
                    break;
                case "Светлая / Синий":
                    overrideCss = "/css/style-light-blue.css";
                    break;
                case "Тёмная / Синий":
                default:
                    overrideCss = null;
                    break;
            }

            if (overrideCss != null) {
                scene.getStylesheets().add(getClass().getResource(overrideCss).toExternalForm());
            }
        } catch (Exception e) {
            log.error("Ошибка при применении темы: {}", newThemeName, e);
        }

        // После переключения CSS подчёркиваем выбранный акцент на динамических элементах.
        applyAccentColorToUI(getAccentOrDefault());
        if (chatBackgroundOverlay != null) {
            chatBackgroundOverlay.setOpacity(chatOverlayOpacity);
            chatBackgroundOverlay.setFill(Color.web("#121212"));
        }
    }

    private void addMessageBubble(Message msg) {
        boolean isOwn = msg.getSenderId().equals(currentUser.getId());

        HBox wrapper = new HBox();
        wrapper.setFillHeight(false);
        wrapper.setSpacing(8);
        wrapper.setPadding(new Insets(4, 12, 4, 12));

        Label bubble = new Label();
        bubble.setText(msg.getContent());
        bubble.setWrapText(true);
        bubble.getStyleClass().add(isOwn ? "message-bubble-out" : "message-bubble-in");

        // Применяем акцентный цвет к bubble (CSS задаёт базу, inline фиксирует цвет)
        Color accent = getAccentOrDefault();
        boolean light = isLightThemeActive(themeName);
        double inAlpha = light ? 0.22 : 0.35;
        double outAlpha = light ? 0.55 : 0.72;
        String inText = light ? "#263238" : "#ECEFF1";
        if (isOwn) {
            bubble.setStyle("-fx-background-color: " + toRgba(accent, outAlpha) + "; -fx-text-fill: black;");
        } else {
            bubble.setStyle("-fx-background-color: " + toRgba(accent, inAlpha) + "; -fx-text-fill: " + inText + ";");
        }

        String timeText = msg.getSentAt() != null ? msg.getSentAt().format(timeFormatter) : "";
        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("message-time");

        VBox bubbleBox = new VBox(bubble, timeLabel);
        bubbleBox.setSpacing(4);

        if (isOwn) {
            wrapper.setAlignment(Pos.CENTER_RIGHT);
            bubbleBox.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        } else {
            wrapper.setAlignment(Pos.CENTER_LEFT);
        }

        wrapper.getChildren().add(bubbleBox);

        Platform.runLater(() -> {
            // Если показывался плейсхолдер при пустой истории — уберём его при первом сообщении.
            if (!messagesContainer.getChildren().isEmpty()) {
                Node first = messagesContainer.getChildren().get(0);
                if (first instanceof Label label && label.getStyleClass().contains("messages-placeholder")) {
                    messagesContainer.getChildren().remove(0);
                }
            }
            messagesContainer.getChildren().add(wrapper);
        });
    }

    private void scrollToBottom() {
        Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
    }
}

