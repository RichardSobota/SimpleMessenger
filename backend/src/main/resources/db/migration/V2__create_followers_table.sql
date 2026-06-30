CREATE TABLE followers (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, follower_id),
    CONSTRAINT chk_no_self_follow CHECK (user_id <> follower_id)
);
