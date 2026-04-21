CREATE TABLE email_verifications (
    id          VARCHAR(36)     NOT NULL,
    user_id     VARCHAR(36)     NOT NULL,
    token       VARCHAR(36)     NOT NULL,
    expires_at  DATETIME(6)     NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_email_verifications_token (token),
    CONSTRAINT fk_email_verifications_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
