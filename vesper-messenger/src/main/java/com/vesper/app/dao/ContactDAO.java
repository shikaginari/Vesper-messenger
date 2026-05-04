package com.vesper.app.dao;

import com.vesper.app.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO для списка контактов:
 * 1) участники диалогов из таблицы messages
 * 2) пользователи, которых явно добавили через поиск (user_contacts)
 */
public class ContactDAO {

    private static final Logger log = LoggerFactory.getLogger(ContactDAO.class);
    private final DataSource dataSource;

    public ContactDAO() {
        this.dataSource = DatabaseConfig.getDataSource();
        ensureUserContactsTable();
        ensurePresenceColumns();
    }

    private void ensureUserContactsTable() {
        String sql =
                "CREATE TABLE IF NOT EXISTS user_contacts (" +
                        "  owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE," +
                        "  contact_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE," +
                        "  PRIMARY KEY (owner_user_id, contact_user_id)" +
                        ");";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            log.warn("Не удалось обеспечить таблицу user_contacts", e);
        }
    }

    private void ensurePresenceColumns() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps1 = conn.prepareStatement("ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT");
             PreparedStatement ps2 = conn.prepareStatement("ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_bytes BYTEA");
             PreparedStatement psChat = conn.prepareStatement("ALTER TABLE users ADD COLUMN IF NOT EXISTS chat_bg_bytes BYTEA");
             PreparedStatement ps3 = conn.prepareStatement("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()")) {
            ps1.execute();
            ps2.execute();
            psChat.execute();
            ps3.execute();
        } catch (SQLException e) {
            log.warn("Не удалось обеспечить колонки presence/users для контактов", e);
        }
    }

    public List<User> findContactsForUser(Long userId) {
        String sql =
                "WITH participants AS (" +
                "   SELECT CASE WHEN m.sender_id = ? THEN m.recipient_id ELSE m.sender_id END AS contact_id " +
                "   FROM messages m " +
                "   WHERE m.sender_id = ? OR m.recipient_id = ? " +
                "   UNION " +
                "   SELECT uc.contact_user_id AS contact_id " +
                "   FROM user_contacts uc " +
                "   WHERE uc.owner_user_id = ?" +
                ") " +
                "SELECT u.id, u.username, u.display_name, u.password_hash, u.bio, u.avatar_bytes, u.chat_bg_bytes, " +
                "       ((now() - u.last_seen_at) <= interval '30 seconds') AS is_online " +
                "FROM users u " +
                "JOIN participants p ON p.contact_id = u.id " +
                "WHERE u.id <> ? " +
                "ORDER BY u.display_name";

        List<User> res = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setLong(3, userId);
            ps.setLong(4, userId);
            ps.setLong(5, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    res.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при загрузке списка контактов", e);
        }

        return res;
    }

    public void addContact(Long ownerUserId, Long contactUserId) {
        if (ownerUserId == null || contactUserId == null) {
            return;
        }
        if (ownerUserId.equals(contactUserId)) {
            return;
        }

        String sql = "INSERT INTO user_contacts(owner_user_id, contact_user_id) VALUES (?, ?) " +
                "ON CONFLICT (owner_user_id, contact_user_id) DO NOTHING";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerUserId);
            ps.setLong(2, contactUserId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Ошибка при добавлении контакта", e);
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

