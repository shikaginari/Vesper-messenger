package com.vesper.app.service;

import com.vesper.app.dao.UserDAO;
import com.vesper.app.model.User;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Сервис авторизации и регистрации пользователей.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserDAO userDAO;

    public AuthService() {
        this.userDAO = new UserDAO();
    }

    public Optional<User> login(String username, String passwordPlain) {
        log.info("Попытка входа пользователя: {}", username);
        Optional<User> userOpt = userDAO.findByUsername(username);

        if (userOpt.isEmpty()) {
            log.warn("Пользователь не найден: {}", username);
            return Optional.empty();
        }

        User user = userOpt.get();
        if (BCrypt.checkpw(passwordPlain, user.getPasswordHash())) {
            log.info("Успешный вход пользователя: {}", username);
            return Optional.of(user);
        } else {
            log.warn("Неверный пароль для пользователя: {}", username);
            return Optional.empty();
        }
    }

    public User register(String username, String displayName, String passwordPlain) {
        log.info("Регистрация нового пользователя: {}", username);
        String hash = BCrypt.hashpw(passwordPlain, BCrypt.gensalt(12));
        return userDAO.createUser(username, displayName, hash);
    }
}

