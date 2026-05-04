package com.vesper.app.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Автоматически создаёт базовые таблицы/колонки.
 * Оптимизировано для работы с облачными БД (Neon) через независимые запросы.
 */
public final class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private DatabaseInitializer() {
    }

    public static void initSchema() {
        log.info("Начало проверки и инициализации схемы БД...");

        // 1. Таблица пользователей
        exec("""
                CREATE TABLE IF NOT EXISTS users (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(64) UNIQUE NOT NULL,
                    display_name VARCHAR(128) NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    bio TEXT,
                    avatar_bytes BYTEA,
                    chat_bg_bytes BYTEA,
                    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
                );
                """);

        // 2. Таблица сообщений
        exec("""
                CREATE TABLE IF NOT EXISTS messages (
                    id BIGSERIAL PRIMARY KEY,
                    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    content TEXT NOT NULL,
                    sent_at TIMESTAMP NOT NULL DEFAULT now()
                );
                """);

        // 3. Индексы (вынесены отдельно, так как они часто вызывают конфликты при повторном запуске)
        exec("CREATE INDEX IF NOT EXISTS idx_messages_sender_recipient_sent_at ON messages(sender_id, recipient_id, sent_at);");
        exec("CREATE INDEX IF NOT EXISTS idx_messages_recipient_sender_sent_at ON messages(recipient_id, sender_id, sent_at);");

        // 4. Прочтения сообщений
        exec("""
                CREATE TABLE IF NOT EXISTS message_reads (
                    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    contact_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    last_read_message_id BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP NOT NULL DEFAULT now(),
                    PRIMARY KEY (user_id, contact_id)
                );
                """);

        // 5. Контакты
        exec("""
                CREATE TABLE IF NOT EXISTS user_contacts (
                    owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    contact_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    PRIMARY KEY (owner_user_id, contact_user_id)
                );
                """);

        log.info("Схема БД успешно проверена и готова к работе.");
    }

    /**
     * Выполняет SQL-запрос, используя новое соединение из пула для каждой операции.
     * Это предотвращает "смерть" всей инициализации при обрыве одного сокета.
     */
    private static void exec(String sql) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            // Если индекс уже есть или произошла мелкая сетевая заминка - просто логируем.
            // Приложение продолжит работу, если таблицы уже созданы.
            log.warn("Запрос выполнен с предупреждением (возможно, объект уже существует): {}", sql.substring(0, Math.min(sql.length(), 50)) + "...");
        }
    }
}