alter table app_user add column admin_super boolean not null default false;
update app_user set admin_super=true where role='ADMIN';
create table admin_menu_permission(user_id bigint not null references app_user(id), menu_key varchar(40) not null, primary key(user_id,menu_key));
alter table character_card add column review_note varchar(1000) not null default '';
