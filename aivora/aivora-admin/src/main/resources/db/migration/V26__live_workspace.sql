create table live_room (
 id bigserial primary key,
 user_id bigint not null references app_user(id),
 name varchar(100) not null,
 platform varchar(30) not null,
 external_room varchar(200) not null default '',
 status varchar(20) not null default 'READY' check(status in ('READY','LIVE','ENDED')),
 plugins varchar(200) not null default 'trim',
 overlay_hash varchar(64) not null,
 created_at timestamptz not null default now()
);
create index live_room_owner on live_room(user_id,id);
create table live_event (
 id bigserial primary key,
 room_id bigint not null references live_room(id) on delete cascade,
 external_id varchar(150),
 kind varchar(20) not null check(kind in ('CHAT','SUBTITLE')),
 author varchar(80) not null,
 content varchar(2000) not null,
 created_at timestamptz not null default now(),
 unique(room_id,external_id)
);
create index live_event_room on live_event(room_id,id);
