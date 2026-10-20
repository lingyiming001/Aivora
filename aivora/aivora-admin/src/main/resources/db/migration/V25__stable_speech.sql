ALTER TABLE chat_session ADD COLUMN speech_model_id BIGINT REFERENCES ai_model(id);
ALTER TABLE chat_session ADD COLUMN speech_model_name VARCHAR(120);
ALTER TABLE chat_session ADD COLUMN speech_voice VARCHAR(80) NOT NULL DEFAULT 'Cherry';
ALTER TABLE chat_session ADD COLUMN speech_language VARCHAR(30) NOT NULL DEFAULT 'Chinese';
CREATE TABLE chat_speech_audio (
 message_id BIGINT PRIMARY KEY REFERENCES chat_message(id) ON DELETE CASCADE,
 text_hash CHAR(64) NOT NULL,
 model_name VARCHAR(120) NOT NULL,
 voice VARCHAR(80) NOT NULL,
 language_type VARCHAR(30) NOT NULL,
 audio_data BYTEA NOT NULL CHECK(octet_length(audio_data) BETWEEN 44 AND 16777216),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
