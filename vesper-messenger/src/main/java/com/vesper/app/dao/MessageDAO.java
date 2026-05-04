package com.vesper.app.dao;

import com.vesper.app.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO для работы с таблицей сообщений.
 *
 * Ожидаемая структура таблицы messages:
 *
 * CREATE TABLE messages (
 *   id BIGSERIAL PRIMARY KEY,
 *   sender_id BIGINT NOT NULL REFERENCES users(id),
 *   recipient_id BIGINT NOT NULL REFERENCES users(id),
 *   content TEXT NOT NULL,
 *   sent_at TIMESTAMP NOT NULL DEFAULT now()
 * );
 */
public class MessageDAO {

    private static final Logger log = LoggerFactory.getLogger(MessageDAO.class);
    private final DataSource dataSource;

    private static final String MESSAGES_CHANNEL = "vesper_messages";
    private static final String TYPING_CHANNEL = "vesper_typing";

    public MessageDAO() {
        this.dataSource = DatabaseConfig.getDataSource();
        ensureMessageReadsTable();
    }

    private void ensureMessageReadsTable() {
        String sql =
                "CREATE TABLE IF NOT EXISTS message_reads (" +
                        "  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE," +
                        "  contact_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE," +
                        "  last_read_message_id BIGINT NOT NULL DEFAULT 0," +
                        "  updated_at TIMESTAMP NOT NULL DEFAULT now()," +
                        "  PRIMARY KEY (user_id, contact_id)" +
                        ");";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            log.warn("Не удалось обеспечить таблицу message_reads (непрочитанные сообщения могут не работать)", e);
        }
    }

    public List<Message> getConversation(Long userId, Long contactId) {
        String sql =
                "SELECT id, sender_id, recipient_id, content, sent_at " +
                        "FROM messages " +
                        "WHERE (sender_id = ? AND recipient_id = ?) " +
                        "   OR (sender_id = ? AND recipient_id = ?) " +
                        "ORDER BY sent_at ASC";

        List<Message> messages = new ArrayList<>();
        log.debug("Загрузка истории сообщений между {} и {}", userId, contactId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setLong(2, contactId);
            ps.setLong(3, contactId);
            ps.setLong(4, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при загрузке истории сообщений", e);
        }
        return messages;
    }

    /**
     * Догрузка новых сообщений после указанного id (для обновления в реальном времени).
     */
    public List<Message> getConversationAfterId(Long userId, Long contactId, long afterMessageId) {
        String sql =
                "SELECT id, sender_id, recipient_id, content, sent_at " +
                        "FROM messages " +
                        "WHERE (" +
                        "   ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))" +
                        "   AND id > ?" +
                        ") " +
                        "ORDER BY sent_at ASC";

        List<Message> messages = new ArrayList<>();
        log.debug("Догрузка сообщений после id={} между {} и {}", afterMessageId, userId, contactId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setLong(2, contactId);
            ps.setLong(3, contactId);
            ps.setLong(4, userId);
            ps.setLong(5, afterMessageId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при догрузке новых сообщений", e);
        }
        return messages;
    }

    public Message saveMessage(Long senderId, Long recipientId, String content) {
        String sql =
                "INSERT INTO messages (sender_id, recipient_id, content, sent_at) " +
                        "VALUES (?, ?, ?, ?) RETURNING id, sent_at";

        LocalDateTime now = LocalDateTime.now();
        log.debug("Сохранение сообщения от {} для {}", senderId, recipientId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, senderId);
            ps.setLong(2, recipientId);
            ps.setString(3, content);
            ps.setTimestamp(4, Timestamp.valueOf(now));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    Timestamp ts = rs.getTimestamp("sent_at");
                    LocalDateTime sent = ts != null ? ts.toLocalDateTime() : now;

                    // Push-уведомление получателям (LISTEN/NOTIFY)
                    // payload: senderId:recipientId:messageId
                    String payload = senderId + ":" + recipientId + ":" + id;
                    try (PreparedStatement notifyPs = conn.prepareStatement("SELECT pg_notify(?, ?)")) {
                        notifyPs.setString(1, MESSAGES_CHANNEL);
                        notifyPs.setString(2, payload);
                        notifyPs.execute();
                    }

                    return Message.builder()
                            .id(id)
                            .senderId(senderId)
                            .recipientId(recipientId)
                            .content(content)
                            .sentAt(sent)
                            .build();
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при сохранении сообщения", e);
            throw new RuntimeException("Не удалось сохранить сообщение", e);
        }
        throw new RuntimeException("Не удалось сохранить сообщение, ID не получен");
    }

    public Message findById(long messageId) {
        String sql = "SELECT id, sender_id, recipient_id, content, sent_at FROM messages WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при поиске сообщения id={}", messageId, e);
        }
        return null;
    }

    public long getLastReadMessageId(Long userId, Long contactId) {
        String sql = "SELECT last_read_message_id FROM message_reads WHERE user_id = ? AND contact_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, contactId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при чтении last_read_message_id", e);
        }
        return 0L;
    }

    public void markConversationRead(Long userId, Long contactId, Long lastReadMessageId) {
        String sql = "INSERT INTO message_reads (user_id, contact_id, last_read_message_id) " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT (user_id, contact_id) DO UPDATE " +
                "SET last_read_message_id = EXCLUDED.last_read_message_id, updated_at = now()";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, contactId);
            ps.setLong(3, lastReadMessageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Ошибка при markConversationRead", e);
            throw new RuntimeException("Не удалось обновить статус прочтения", e);
        }
    }

    public int countUnread(Long userId, Long contactId) {
        String sql =
                "SELECT COUNT(*) " +
                        "FROM messages m " +
                        "WHERE ((m.sender_id = ? AND m.recipient_id = ?) OR (m.sender_id = ? AND m.recipient_id = ?)) " +
                        "  AND m.id > COALESCE((" +
                        "     SELECT mr.last_read_message_id " +
                        "     FROM message_reads mr " +
                        "     WHERE mr.user_id = ? AND mr.contact_id = ?" +
                        "  ), 0)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setLong(2, contactId);
            ps.setLong(3, contactId);
            ps.setLong(4, userId);
            ps.setLong(5, userId);
            ps.setLong(6, contactId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.error("Ошибка при подсчёте непрочитанных сообщений", e);
        }
        return 0;
    }

    public void sendTypingNotification(Long senderId, Long recipientId, boolean isTyping) {
        // payload: senderId:recipientId:timestampMillis:isTyping(1/0)
        long ts = System.currentTimeMillis();
        String payload = senderId + ":" + recipientId + ":" + ts + ":" + (isTyping ? 1 : 0);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT pg_notify(?, ?)")) {
            ps.setString(1, TYPING_CHANNEL);
            ps.setString(2, payload);
            ps.execute();
        } catch (SQLException e) {
            log.error("Ошибка отправки typing notification", e);
        }
    }

    private Message mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("sent_at");
        LocalDateTime sentAt = ts != null ? ts.toLocalDateTime() : null;

        return Message.builder()
                .id(rs.getLong("id"))
                .senderId(rs.getLong("sender_id"))
                .recipientId(rs.getLong("recipient_id"))
                .content(rs.getString("content"))
                .sentAt(sentAt)
                .build();
    }
}

