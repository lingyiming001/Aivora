-- Replace available offers while retaining old packages and immutable order snapshots.
UPDATE recharge_package SET enabled = false WHERE enabled = true;
INSERT INTO recharge_package (name, amount_cents, stars, enabled) VALUES
 ('轻享套餐', 1000, 1000, true),
 ('畅聊套餐', 3000, 3000, true),
 ('沉浸套餐', 10000, 10000, true);
