CREATE TABLE platform_connections (
    id                      VARCHAR(36)     NOT NULL PRIMARY KEY,
    creator_profile_id      VARCHAR(36)     NOT NULL,
    platform_type           VARCHAR(20)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'DISCONNECTED',
    platform_user_id        VARCHAR(255)    NULL,
    platform_name           VARCHAR(255)    NULL,
    follower_count          BIGINT          NULL,
    access_token_encrypted  TEXT            NULL,
    refresh_token_encrypted TEXT            NULL,
    token_expires_at        DATETIME(6)     NULL,
    last_sync_at            DATETIME(6)     NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_platform_connections_creator FOREIGN KEY (creator_profile_id) REFERENCES creator_profiles(id),
    CONSTRAINT uq_creator_platform UNIQUE (creator_profile_id, platform_type)
);