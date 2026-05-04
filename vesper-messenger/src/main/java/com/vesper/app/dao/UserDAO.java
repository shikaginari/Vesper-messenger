package com.vesper.app.dao;

import com.vesper.app.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO для работы с таблицей пользователей.
 *
 * Ожидаемая структура таблицы users:
 *
 * CREATE TABLE users (
 *   id BIGSERIAL PRIMARY KEY,
 *   username VARCHAR(64) UNIQUE NOT NULL,
 *   display_name VARCHAR(128) NOT NULL,
 *   password_hash VARCHAR(255) NOT NULL
 * );
 */
public class UserDAO {

    private static final Logger log = LoggerFactory.getLogger(UserDAO.class);
    private final DataSource dataSource;

    public UserDAO() {
        this.dataSource = DatabaseConfig.getDataSource();
        // Убираем тяжёлую операцию с БД из основного потока JavaFX
        new Thread(() -> {
            try {
                ensureProfileAndPresenceColumns();
            } catch (Exception e) {
                log.warn("Не удалось проверить колонки профиля при старте: {}", e.getMessage());
            }
        }, "UserDAO-Init-Thread").start();
    }

    private void ensureProfileAndPresenceColumns() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps1 = conn.prepareStatement(
                     "ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT");
             PreparedStatement ps2 = conn.prepareStatement(
                     "ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_bytes BYTEA");
             PreparedStatement psChat = conn.prepareStatement(
                     "ALTER TABLE users ADD COLUMN IF NOT EXISTS chat_bg_bytes BYTEA");
             PreparedStatement ps3 = conn.prepareStatement(
                     "ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()")) {
            ps1.execute();
            ps2.execute();
            psChat.execute();
            ps3.execute();
        } catch (SQLException e) {
            log.warn("Не удалось обеспечить колонки профиля/онлайна", e);
        }
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, display_name, password_hash, bio, avatar_bytes, chat_bg_bytes, " +
                "       ((now() - last_seen_at) <= interval '30 seconds') AS is_online " +
                "FROM users WHERE username = ?";
        log.debug("Поиск пользователя по имени: {}", username);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = mapRow(rs);
                    return Optional.of(user);
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при поиске пользователя по username", e);
        }
        return Optional.empty();
    }

    public List<User> findAllExcept(Long userId) {
        String sql = "SELECT id, username, display_name, password_hash, bio, avatar_bytes, chat_bg_bytes, " +
                "       ((now() - last_seen_at) <= interval '30 seconds') AS is_online " +
                "FROM users WHERE id <> ? ORDER BY display_name";
        List<User> users = new ArrayList<>();
        log.debug("Загрузка списка пользователей, исключая id={}", userId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }

            // Если в системе только один пользователь (текущий),
            // вернём его самого как "личный чат", чтобы приложение было функциональным.
            if (users.isEmpty()) {
                log.debug("Других пользователей нет, возвращаем текущего пользователя как личный контакт.");
                try (PreparedStatement selfPs = conn.prepareStatement(
                        "SELECT id, username, display_name, password_hash, bio, avatar_bytes, chat_bg_bytes, " +
                                "       ((now() - last_seen_at) <= interval '30 seconds') AS is_online " +
                                "FROM users WHERE id = ?")) {
                    selfPs.setLong(1, userId);
                    try (ResultSet rsSelf = selfPs.executeQuery()) {
                        while (rsSelf.next()) {
                            users.add(mapRow(rsSelf));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при загрузке списка пользователей", e);
        }
        return users;
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT id, username, display_name, password_hash, bio, avatar_bytes, chat_bg_bytes, " +
                "       ((now() - last_seen_at) <= interval '30 seconds') AS is_online " +
                "FROM users WHERE id = ?";
        log.debug("Поиск пользователя по id: {}", id);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при поиске пользователя по id", e);
        }
        return Optional.empty();
    }

    public User createUser(String username, String displayName, String passwordHash) {
        String sql = "INSERT INTO users (username, display_name, password_hash, bio, avatar_bytes, chat_bg_bytes, last_seen_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, now()) RETURNING id";
        log.debug("Создание нового пользователя: {}", username);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, displayName);
            ps.setString(3, passwordHash);
            ps.setString(4, null);
            ps.setBytes(5, null);
            ps.setBytes(6, null);
            // last_seen_at проставится default/now() в запросе

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    return User.builder()
                            .id(id)
                            .username(username)
                            .displayName(displayName)
                            .passwordHash(passwordHash)
                            .bio(null)
                            .avatarBytes(null)
                            .chatBackgroundBytes(null)
                            .online(true)
                            .build();
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при создании пользователя", e);
            throw new RuntimeException("Не удалось создать пользователя", e);
        }
        throw new RuntimeException("Не удалось создать пользователя, ID не получен");
    }

    public void touchPresence(Long userId) {
        String sql = "UPDATE users SET last_seen_at = now() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Ошибка touchPresence для userId={}", userId, e);
        }
    }

    public void updateProfile(long userId, String displayName, String bio, byte[] avatarBytes) {
        String sql = "UPDATE users SET display_name = ?, bio = ?, avatar_bytes = ? WHERE id = ?";
        log.debug("Обновление профиля пользователя id={}", userId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, displayName);
            ps.setString(2, bio);
            ps.setBytes(3, avatarBytes);
            ps.setLong(4, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Ошибка при обновлении профиля", e);
            throw new RuntimeException("Не удалось обновить профиль", e);
        }
    }

    public void updateChatBackground(long userId, byte[] chatBackgroundBytes) {
        String sql = "UPDATE users SET chat_bg_bytes = ? WHERE id = ?";
        log.debug("Обновление фона чата пользователя id={}", userId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBytes(1, chatBackgroundBytes);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Ошибка при обновлении фона чата", e);
            throw new RuntimeException("Не удалось обновить фон чата", e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        byte[] avatar = rs.getBytes("avatar_bytes");
        byte[] chatBg = rs.getBytes("chat_bg_bytes");
        return User.builder()
                .id(rs.getLong("id"))
                .username(rs.getString("username"))
                .displayName(rs.getString("display_name"))
                .passwordHash(rs.getString("password_hash"))
                .bio(rs.getString("bio"))
                .avatarBytes(avatar)
                .chatBackgroundBytes(chatBg)
                .online(rs.getBoolean("is_online"))
                .build();
    }
}

