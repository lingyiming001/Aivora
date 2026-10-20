CREATE TABLE recharge_package (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(80) NOT NULL, amount_cents BIGINT NOT NULL CHECK(amount_cents>0),
 stars BIGINT NOT NULL CHECK(stars>0), enabled BOOLEAN NOT NULL DEFAULT true
);
ALTER TABLE recharge_order ADD COLUMN package_id BIGINT REFERENCES recharge_package(id);
CREATE INDEX idx_recharge_user_created ON recharge_order(user_id,created_at DESC);
