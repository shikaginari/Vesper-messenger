package com.vesper.app.model;

/**
 * Модель пользователя мессенджера.
 *
 * Убрали Lombok и добавили явные геттеры/сеттеры и билдер,
 * чтобы IDE и компилятор без аннотаций работали корректно.
 */
public class User {
    private Long id;
    private String username;
    private String displayName;
    private String passwordHash;
    private String bio;
    private byte[] avatarBytes;
    private byte[] chatBackgroundBytes;
    private boolean online;

    public User() {
    }

    public User(Long id, String username, String displayName, String passwordHash) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.online = false;
        this.chatBackgroundBytes = null;
    }

    public User(Long id, String username, String displayName, String passwordHash, String bio, byte[] avatarBytes, byte[] chatBackgroundBytes, boolean online) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.bio = bio;
        this.avatarBytes = avatarBytes;
        this.chatBackgroundBytes = chatBackgroundBytes;
        this.online = online;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public byte[] getAvatarBytes() {
        return avatarBytes;
    }

    public void setAvatarBytes(byte[] avatarBytes) {
        this.avatarBytes = avatarBytes;
    }

    public byte[] getChatBackgroundBytes() {
        return chatBackgroundBytes;
    }

    public void setChatBackgroundBytes(byte[] chatBackgroundBytes) {
        this.chatBackgroundBytes = chatBackgroundBytes;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    // Реализация паттерна Builder для совместимости с User.builder()
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String username;
        private String displayName;
        private String passwordHash;
        private String bio;
        private byte[] avatarBytes;
        private byte[] chatBackgroundBytes;
        private boolean online;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder bio(String bio) {
            this.bio = bio;
            return this;
        }

        public Builder avatarBytes(byte[] avatarBytes) {
            this.avatarBytes = avatarBytes;
            return this;
        }

        public Builder chatBackgroundBytes(byte[] chatBackgroundBytes) {
            this.chatBackgroundBytes = chatBackgroundBytes;
            return this;
        }

        public Builder online(boolean online) {
            this.online = online;
            return this;
        }

        public User build() {
            return new User(id, username, displayName, passwordHash, bio, avatarBytes, chatBackgroundBytes, online);
        }
    }
}

