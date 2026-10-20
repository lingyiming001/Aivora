-- Merge duplicate active conversations without exposing previously cleared messages.
ALTER TABLE chat_message ADD COLUMN history_hidden BOOLEAN NOT NULL DEFAULT false;
CREATE TEMP TABLE chat_session_merge ON COMMIT DROP AS
 SELECT id,history_after_id,
 first_value(id) OVER (PARTITION BY user_id,character_id ORDER BY updated_at DESC,id DESC) AS target_id,
 count(*) OVER (PARTITION BY user_id,character_id) AS siblings
 FROM chat_session WHERE deleted_at IS NULL;
UPDATE chat_message m SET history_hidden=true FROM chat_session_merge s
 WHERE s.siblings>1 AND m.session_id=s.id AND m.id<=s.history_after_id;
UPDATE chat_message m SET session_id=s.target_id FROM chat_session_merge s
 WHERE s.siblings>1 AND s.id<>s.target_id AND m.session_id=s.id;
UPDATE chat_request r SET session_id=s.target_id FROM chat_session_merge s
 WHERE s.siblings>1 AND s.id<>s.target_id AND r.session_id=s.id;
UPDATE chat_image_attachment a SET session_id=s.target_id FROM chat_session_merge s
 WHERE s.siblings>1 AND s.id<>s.target_id AND a.session_id=s.id;
UPDATE chat_file_attachment a SET session_id=s.target_id FROM chat_session_merge s
 WHERE s.siblings>1 AND s.id<>s.target_id AND a.session_id=s.id;
UPDATE chat_session c SET history_after_id=0 FROM chat_session_merge s
 WHERE s.siblings>1 AND c.id=s.target_id;
UPDATE chat_session c SET deleted_at=now() FROM chat_session_merge s
 WHERE s.siblings>1 AND s.id<>s.target_id AND c.id=s.id;
CREATE UNIQUE INDEX uq_chat_user_character_active ON chat_session(user_id,character_id) WHERE deleted_at IS NULL;
ALTER TABLE app_user ADD COLUMN memory_enabled BOOLEAN NOT NULL DEFAULT true;
CREATE TABLE user_memory (
 user_id BIGINT NOT NULL REFERENCES app_user(id),
 memory_key VARCHAR(40) NOT NULL,
 content VARCHAR(300) NOT NULL,
 source VARCHAR(20) NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,memory_key)
);
