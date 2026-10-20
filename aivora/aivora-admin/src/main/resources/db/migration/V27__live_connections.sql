create table live_connection (
 room_id bigint primary key references live_room(id) on delete cascade,
 source_id varchar(200) not null,
 client_id varchar(100) not null default '',
 actor_id varchar(100) not null default '',
 token_ciphertext text not null,
 enabled boolean not null default false,
 revision varchar(36) not null,
 status varchar(30) not null default 'DISCONNECTED',
 detail varchar(200) not null default '',
 updated_at timestamptz not null default now()
);
