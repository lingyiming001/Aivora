ALTER TABLE app_user ADD COLUMN username VARCHAR(64) UNIQUE;
ALTER TABLE app_user ADD COLUMN phone VARCHAR(20) UNIQUE;
ALTER TABLE character_card ADD COLUMN seed_key VARCHAR(80) UNIQUE;
CREATE TABLE login_otp (
 id UUID PRIMARY KEY,phone VARCHAR(20) NOT NULL,portal VARCHAR(10) NOT NULL CHECK(portal IN ('USER','ADMIN')),
 code_hash CHAR(64) NOT NULL,request_ip VARCHAR(80) NOT NULL,attempts INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),expires_at TIMESTAMPTZ NOT NULL,consumed_at TIMESTAMPTZ
);
CREATE INDEX idx_otp_phone_portal_created ON login_otp(phone,portal,created_at DESC);
CREATE INDEX idx_otp_ip_created ON login_otp(request_ip,created_at DESC);
