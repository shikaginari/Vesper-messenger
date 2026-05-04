package com.vesper.app.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Конфигурация подключения к базе данных PostgreSQL (Neon) через HikariCP.
 * Настроено специально для предотвращения разрывов (Connection reset) в облаке.
 */
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private static HikariDataSource dataSource;

    // ДОБАВЛЕНО: tcpKeepAlive=true и socketTimeout=60 для защиты от обрывов соединения
    private static final String DEFAULT_JDBC_URL = "jdbc:postgresql://ep-plain-hat-ag60yw91.c-2.eu-central-1.aws.neon.tech/neondb?sslmode=require&tcpKeepAlive=true&socketTimeout=60";
    private static final String DEFAULT_JDBC_USERNAME = "neondb_owner";
    private static final String DEFAULT_JDBC_PASSWORD = "npg_5XMvL2SRgWhf";

    // Приоритет переменным окружения, если они заданы
    private static final String JDBC_URL = DEFAULT_JDBC_URL;
    private static final String JDBC_USERNAME = DEFAULT_JDBC_USERNAME;
    private static final String JDBC_PASSWORD = DEFAULT_JDBC_PASSWORD;
    // Гарантируем наличие sslmode=require для Neon
    private static final String EFFECTIVE_JDBC_URL = ensureSslIfNeeded(JDBC_URL);

    static {
        init();
    }

    private static void init() {
        try {
            HikariConfig config = new HikariConfig();

            config.setJdbcUrl(EFFECTIVE_JDBC_URL);
            config.setUsername(JDBC_USERNAME);
            config.setPassword(JDBC_PASSWORD);

            config.setDriverClassName("org.postgresql.Driver");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);

            // --- ВАЖНЫЕ НАСТРОЙКИ ДЛЯ ОБЛАКА (NEON) ---
            config.setConnectionTimeout(30000);   // 30 сек ожидания подключения
            config.setIdleTimeout(300000);        // 5 минут до закрытия простаивающего соединения
            config.setMaxLifetime(540000);        // ДОБАВЛЕНО: 9 минут (Neon принудительно рубит соединения старше 10 минут)
            config.setKeepaliveTime(30000);       // ДОБАВЛЕНО: Пинговать базу каждые 30 секунд, чтобы не уснула

            config.setPoolName("VesperHikariPool");
            // -1 позволяет приложению не падать сразу, если Neon "просыпается" дольше 30 сек
            config.setInitializationFailTimeout(-1);

            dataSource = new HikariDataSource(config);
            log.info("!!! ИСПОЛЬЗУЕМЫЙ URL: {}", EFFECTIVE_JDBC_URL);
            log.info("!!! ИСПОЛЬЗУЕМЫЙ USER: {}", JDBC_USERNAME);

            // Инициализация таблиц в отдельном потоке
            new Thread(() -> {
                try {
                    // ДОБАВЛЕНО: Небольшая пауза, чтобы поток логина и поток создания таблиц не "дрались" за первое подключение
                    Thread.sleep(1000);
                    DatabaseInitializer.initSchema();
                    log.info("Схема БД Vesper успешно проверена.");
                } catch (Exception e) {
                    log.error("Не удалось инициализировать схему БД. Проверьте статус проекта в Neon.", e);
                }
            }, "DB-Init-Thread").start();

        } catch (Exception e) {
            log.error("Критический сбой инициализации пула HikariCP", e);
        }
    }

    private static String getEnvOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String ensureSslIfNeeded(String url) {
        if (url == null || url.isBlank()) return url;
        String lower = url.toLowerCase();
        if (lower.contains("sslmode=") || lower.contains("localhost")) return url;
        return url.contains("?") ? url + "&sslmode=require" : url + "?sslmode=require";
    }

    public static DataSource getDataSource() {
        return dataSource;
    }

    public static java.sql.Connection getRawConnection() {
        try {
            return java.sql.DriverManager.getConnection(EFFECTIVE_JDBC_URL, JDBC_USERNAME, JDBC_PASSWORD);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка прямого подключения к БД", e);
        }
    }

    public static void close() {
        if (dataSource != null) {
            log.info("Остановка пула подключений к БД...");
            dataSource.close();
        }
    }
}