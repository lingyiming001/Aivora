alter table ai_model add column model_type varchar(20) not null default 'CHAT' check(model_type in ('TEXT','CHAT','VOICE'));
alter table model_route_rule add column helper_model_ids bigint[] not null default '{}';
alter table model_route_rule add column min_characters integer not null default 0 check(min_characters between 0 and 10000);
alter table chat_request add column composition_model_ids bigint[] not null default '{}';
