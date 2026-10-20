alter table character_card add column offline_reason varchar(1000) not null default '';
alter table character_card add column offlined_at timestamptz;
alter table character_card add column offlined_by bigint references app_user(id);
