create table chat_file_attachment (
 id uuid primary key,user_id bigint not null references app_user(id),session_id bigint not null references chat_session(id),
 message_id bigint references chat_message(id),name varchar(160) not null,media_type varchar(80) not null,
 byte_size bigint not null check(byte_size between 1 and 5242880),file_data bytea not null,
 extracted_text text not null,truncated boolean not null default false,created_at timestamptz not null default now()
);
create index idx_chat_file_session_message on chat_file_attachment(session_id,message_id);
