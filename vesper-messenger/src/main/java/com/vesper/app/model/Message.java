package com.vesper.app.model;

import java.time.LocalDateTime;

/**
 * Модель сообщения.
 *
 * Убрали Lombok и добавили явные геттеры/сеттеры и билдер,
 * чтобы IDE и компилятор без аннотаций работали корректно.
 */
public class Message {
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String content;
    private LocalDateTime sentAt;

    public Message() {
    }

    public Message(Long id, Long senderId, Long recipientId, String content, LocalDateTime sentAt) {
        this.id = id;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.content = content;
        this.sentAt = sentAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String toShortString() {
        return content;
    }

    // Реализация паттерна Builder для совместимости с Message.builder()
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Long senderId;
        private Long recipientId;
        private String content;
        private LocalDateTime sentAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder senderId(Long senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder recipientId(Long recipientId) {
            this.recipientId = recipientId;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder sentAt(LocalDateTime sentAt) {
            this.sentAt = sentAt;
            return this;
        }

        public Message build() {
            return new Message(id, senderId, recipientId, content, sentAt);
        }
    }
}

