-- V6: User notifications

CREATE TABLE notification (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT         NOT NULL,
    read_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Partial index for unread-count queries (avoids scanning read notifications)
CREATE INDEX idx_notification_user_unread
    ON notification (user_id, created_at DESC)
    WHERE read_at IS NULL;
