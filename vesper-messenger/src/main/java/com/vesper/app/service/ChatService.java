package com.vesper.app.service;

import com.vesper.app.dao.MessageDAO;
import com.vesper.app.dao.ContactDAO;
import com.vesper.app.model.Message;
import com.vesper.app.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Сервис бизнес-логики чата.
 */
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final MessageDAO messageDAO;
    private final ContactDAO contactDAO;

    public ChatService() {
        this.messageDAO = new MessageDAO();
        this.contactDAO = new ContactDAO();
    }

    public List<User> loadContactsForUser(Long userId) {
        log.info("Загрузка списка контактов для пользователя id={}", userId);
        return contactDAO.findContactsForUser(userId);
    }

    public void addContact(Long ownerUserId, Long contactId) {
        log.info("Добавление контакта: owner={}, contact={}", ownerUserId, contactId);
        contactDAO.addContact(ownerUserId, contactId);
    }

    public List<Message> loadConversation(Long userId, Long contactId) {
        log.info("Загрузка истории диалога: userId={}, contactId={}", userId, contactId);
        return messageDAO.getConversation(userId, contactId);
    }

    public List<Message> loadNewMessages(Long userId, Long contactId, long afterMessageId) {
        log.info("Догрузка новых сообщений: userId={}, contactId={}, afterId={}", userId, contactId, afterMessageId);
        return messageDAO.getConversationAfterId(userId, contactId, afterMessageId);
    }

    public Message sendMessage(Long senderId, Long recipientId, String content) {
        log.info("Отправка сообщения от {} к {}", senderId, recipientId);
        return messageDAO.saveMessage(senderId, recipientId, content);
    }

    public Message findMessageById(long messageId) {
        return messageDAO.findById(messageId);
    }

    public int countUnread(Long userId, Long contactId) {
        return messageDAO.countUnread(userId, contactId);
    }

    public void markConversationRead(Long userId, Long contactId, Long lastReadMessageId) {
        messageDAO.markConversationRead(userId, contactId, lastReadMessageId);
    }

    public void sendTypingEvent(Long senderId, Long recipientId, boolean isTyping) {
        messageDAO.sendTypingNotification(senderId, recipientId, isTyping);
    }
}

