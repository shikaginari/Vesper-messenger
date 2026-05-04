package com.vesper.app.service;

import com.vesper.app.dao.DatabaseConfig;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

/**
 * Подписка на события через PostgreSQL LISTEN/NOTIFY.
 *
 * Сервис не знает ничего про UI, только парсит payload и вызывает коллбэки.
 */
public class PostgresRealtimeService implements AutoCloseable {

    public interface MessageListener {
        void onMessage(long senderId, long recipientId, long messageId);
    }

    public interface TypingListener {
        void onTyping(long senderId, long recipientId, long timestampMillis, boolean isTyping);
    }

    private static final Logger log = LoggerFactory.getLogger(PostgresRealtimeService.class);

    private final String messagesChannel;
    private final String typingChannel;

    private Connection rawConnection;
    private PGConnection pgConnection;

    private Thread listenerThread;
    private volatile boolean running;

    public PostgresRealtimeService(String messagesChannel, String typingChannel) {
        this.messagesChannel = messagesChannel;
        this.typingChannel = typingChannel;
    }

    public void start(MessageListener messageListener, TypingListener typingListener) {
        if (running) {
            return;
        }
        running = true;

        listenerThread = new Thread(() -> {
            try {
                rawConnection = DatabaseConfig.getRawConnection();
                rawConnection.setAutoCommit(true);
                pgConnection = rawConnection.unwrap(PGConnection.class);

                try (java.sql.Statement st = rawConnection.createStatement()) {
                    st.execute("LISTEN " + messagesChannel);
                    st.execute("LISTEN " + typingChannel);
                }

                while (running) {
                    PGNotification[] notifications = pgConnection.getNotifications();
                    if (notifications != null && notifications.length > 0) {
                        for (PGNotification n : notifications) {
                            String channel = n.getName();
                            String payload = n.getParameter();
                            handleNotification(channel, payload, messageListener, typingListener);
                        }
                    }
                    Thread.sleep(250);
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Ошибка realtime слушателя", e);
                }
            } finally {
                try {
                    if (rawConnection != null) {
                        rawConnection.close();
                    }
                } catch (Exception ignore) {
                }
            }
        }, "PostgresRealtimeService");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void handleNotification(
            String channel,
            String payload,
            MessageListener messageListener,
            TypingListener typingListener
    ) {
        if (payload == null) {
            return;
        }

        try {
            if (channel != null && channel.equals(messagesChannel)) {
                // payload: senderId:recipientId:messageId
                String[] parts = payload.split(":");
                if (parts.length < 3) return;
                long senderId = Long.parseLong(parts[0]);
                long recipientId = Long.parseLong(parts[1]);
                long messageId = Long.parseLong(parts[2]);
                messageListener.onMessage(senderId, recipientId, messageId);
            } else if (channel != null && channel.equals(typingChannel)) {
                // payload: senderId:recipientId:timestampMillis:isTyping(1/0)
                String[] parts = payload.split(":");
                if (parts.length < 4) return;
                long senderId = Long.parseLong(parts[0]);
                long recipientId = Long.parseLong(parts[1]);
                long ts = Long.parseLong(parts[2]);
                boolean isTyping = "1".equals(parts[3]);
                typingListener.onTyping(senderId, recipientId, ts, isTyping);
            }
        } catch (Exception e) {
            log.warn("Не удалось обработать notification: channel={}, payload={}", channel, payload, e);
        }
    }

    @Override
    public void close() {
        running = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }
}

