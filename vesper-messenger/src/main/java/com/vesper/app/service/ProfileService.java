package com.vesper.app.service;

import com.vesper.app.dao.UserDAO;
import com.vesper.app.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Сервис профиля пользователя (аватар, описание и т.д.).
 */
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private final UserDAO userDAO;

    public ProfileService() {
        this.userDAO = new UserDAO();
    }

    public Optional<User> loadUser(long userId) {
        log.info("Загрузка профиля пользователя id={}", userId);
        return userDAO.findById(userId);
    }

    public void updateProfile(long userId, String displayName, String bio, byte[] avatarBytes) {
        log.info("Сохранение профиля пользователя id={}", userId);
        userDAO.updateProfile(userId, displayName, bio, avatarBytes);
    }

    public void updateChatBackground(long userId, byte[] chatBackgroundBytes) {
        log.info("Сохранение фона чата пользователя id={}", userId);
        userDAO.updateChatBackground(userId, chatBackgroundBytes);
    }
}

