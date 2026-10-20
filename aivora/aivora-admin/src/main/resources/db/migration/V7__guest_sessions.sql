CREATE TABLE guest_session (
 token_hash CHAR(64) PRIMARY KEY,user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id),ip_hash CHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_guest_ip_created ON guest_session(ip_hash,created_at DESC);
