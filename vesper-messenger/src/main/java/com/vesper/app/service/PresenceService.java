package com.vesper.app.service;

import com.vesper.app.dao.UserDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сервис присутствия: обновляет last_seen_at в таблице users.
 */
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private final UserDAO userDAO;

    public PresenceService() {
        this.userDAO = new UserDAO();
    }

    public void touch(long userId) {
        if (userId <= 0) {
            return;
        }
        try {
            userDAO.touchPresence(userId);
        } catch (Exception e) {
            log.warn("Не удалось обновить присутствие userId={}", userId, e);
        }
    }
}

