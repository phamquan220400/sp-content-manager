CREATE TABLE creator_profiles (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)     NOT NULL UNIQUE,
    display_name    VARCHAR(50)     NOT NULL,
    bio             VARCHAR(500)    NULL,
    creator_category VARCHAR(30)   NOT NULL,
    content_preferences JSON       NULL,
    notification_settings JSON     NULL,
    profile_image_url VARCHAR(500) NULL,
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_creator_profiles_user FOREIGN KEY (user_id) REFERENCES users(id)
);