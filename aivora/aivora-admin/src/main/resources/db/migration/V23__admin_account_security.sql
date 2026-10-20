alter table app_user add column session_version bigint not null default 0;
alter table app_user add column admin_password_failures int not null default 0;
alter table app_user add column admin_password_window timestamptz;
create table admin_phone_otp (
 id uuid primary key,user_id bigint not null references app_user(id),phone varchar(20) not null,
 purpose varchar(8) not null check(purpose in ('OLD','NEW')),session_version bigint not null,code_hash char(64) not null,
 request_ip varchar(80) not null,attempts int not null default 0,created_at timestamptz not null default now(),
 expires_at timestamptz not null,consumed_at timestamptz
);
create index idx_admin_phone_otp_user on admin_phone_otp(user_id,purpose,created_at desc);
