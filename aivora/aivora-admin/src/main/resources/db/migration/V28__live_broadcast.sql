create table live_broadcast (
 room_id bigint primary key references live_room(id) on delete cascade,
 stream_id uuid not null unique,
 publish_hash varchar(64) not null,
 created_at timestamptz not null default now()
);
