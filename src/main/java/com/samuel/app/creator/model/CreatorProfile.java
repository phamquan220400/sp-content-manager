package com.samuel.app.creator.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "creator_profiles")
public class CreatorProfile {
    
    @Id
    private String id;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "display_name")
    private String displayName;
    
    private String bio;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "creator_category")
    private CreatorCategory creatorCategory;
    
    @Column(name = "content_preferences", columnDefinition = "JSON")
    private String contentPreferences;
    
    @Column(name = "notification_settings", columnDefinition = "JSON")
    private String notificationSettings;
    
    @Column(name = "profile_image_url")
    private String profileImageUrl;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // No-arg constructor
    public CreatorProfile() {
    }
    
    // Nested enum
    public enum CreatorCategory {
        LIFESTYLE, GAMING, EDUCATION, TECH, FINANCE, FITNESS, ENTERTAINMENT, OTHER
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getBio() {
        return bio;
    }
    
    public void setBio(String bio) {
        this.bio = bio;
    }
    
    public CreatorCategory getCreatorCategory() {
        return creatorCategory;
    }
    
    public void setCreatorCategory(CreatorCategory creatorCategory) {
        this.creatorCategory = creatorCategory;
    }
    
    public String getContentPreferences() {
        return contentPreferences;
    }
    
    public void setContentPreferences(String contentPreferences) {
        this.contentPreferences = contentPreferences;
    }
    
    public String getNotificationSettings() {
        return notificationSettings;
    }
    
    public void setNotificationSettings(String notificationSettings) {
        this.notificationSettings = notificationSettings;
    }
    
    public String getProfileImageUrl() {
        return profileImageUrl;
    }
    
    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}