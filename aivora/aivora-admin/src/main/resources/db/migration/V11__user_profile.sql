alter table app_user add column avatar_url text not null default '/images/profile-1.svg';
alter table app_user add column bio varchar(300) not null default '';
