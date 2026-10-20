create table chat_image_attachment (
 id uuid primary key, user_id bigint not null references app_user(id),
 session_id bigint not null references chat_session(id), message_id bigint unique references chat_message(id),
 image_data text not null, description text not null default '', source varchar(20) not null check(source in ('UPLOAD','GENERATED')),
 created_at timestamptz not null default now()
);
create index idx_chat_image_session on chat_image_attachment(session_id);
