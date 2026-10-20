CREATE TABLE app_user (
 id BIGSERIAL PRIMARY KEY, email VARCHAR(320) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 nickname VARCHAR(80) NOT NULL, role VARCHAR(20) NOT NULL DEFAULT 'USER', status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE refresh_token (
 id UUID PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES app_user(id), token_hash CHAR(64) NOT NULL UNIQUE,
 expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_refresh_user ON refresh_token(user_id);
CREATE TABLE character_card (
 id BIGSERIAL PRIMARY KEY, owner_id BIGINT NOT NULL REFERENCES app_user(id), name VARCHAR(100) NOT NULL,
 summary VARCHAR(1000) NOT NULL DEFAULT '', persona TEXT NOT NULL DEFAULT '', system_prompt TEXT NOT NULL DEFAULT '',
 opening TEXT NOT NULL DEFAULT '', avatar_url TEXT, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 version INTEGER NOT NULL DEFAULT 1, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_character_status_created ON character_card(status,created_at DESC);
CREATE INDEX idx_character_owner ON character_card(owner_id);
CREATE TABLE wallet (
 user_id BIGINT PRIMARY KEY REFERENCES app_user(id), balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0)
);
CREATE TABLE wallet_ledger (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES app_user(id), delta BIGINT NOT NULL,
 balance_after BIGINT NOT NULL CHECK(balance_after>=0), reason VARCHAR(40) NOT NULL,
 reference_key VARCHAR(150) NOT NULL UNIQUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_user_created ON wallet_ledger(user_id,created_at DESC);
CREATE TABLE ai_provider (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(80) NOT NULL UNIQUE, kind VARCHAR(40) NOT NULL,
 base_url VARCHAR(500) NOT NULL, api_key_ciphertext TEXT, enabled BOOLEAN NOT NULL DEFAULT false
);
CREATE TABLE ai_model (
 id BIGSERIAL PRIMARY KEY, provider_id BIGINT NOT NULL REFERENCES ai_provider(id), model_name VARCHAR(120) NOT NULL,
 capabilities VARCHAR(200) NOT NULL DEFAULT 'TEXT', enabled BOOLEAN NOT NULL DEFAULT false,
 star_per_request BIGINT NOT NULL DEFAULT 1 CHECK(star_per_request>=0), UNIQUE(provider_id,model_name)
);
CREATE TABLE chat_session (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES app_user(id), character_id BIGINT NOT NULL REFERENCES character_card(id),
 model_id BIGINT NOT NULL REFERENCES ai_model(id), title VARCHAR(120) NOT NULL DEFAULT '新对话',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_session_user ON chat_session(user_id,created_at DESC);
CREATE TABLE chat_message (
 id BIGSERIAL PRIMARY KEY, session_id BIGINT NOT NULL REFERENCES chat_session(id), role VARCHAR(20) NOT NULL,
 content TEXT NOT NULL, request_key VARCHAR(150), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_session ON chat_message(session_id,id);
CREATE TABLE chat_request (
 id BIGSERIAL PRIMARY KEY, session_id BIGINT NOT NULL REFERENCES chat_session(id), user_id BIGINT NOT NULL REFERENCES app_user(id),
 request_key VARCHAR(150) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL, cost_stars BIGINT NOT NULL,
 error_message VARCHAR(500), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE audit_log (
 id BIGSERIAL PRIMARY KEY, actor_id BIGINT REFERENCES app_user(id), action VARCHAR(80) NOT NULL,
 subject VARCHAR(160) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE recharge_order (
 id UUID PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES app_user(id), amount_cents BIGINT NOT NULL CHECK(amount_cents>0),
 stars BIGINT NOT NULL CHECK(stars>0), status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 provider_ref VARCHAR(160) UNIQUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
