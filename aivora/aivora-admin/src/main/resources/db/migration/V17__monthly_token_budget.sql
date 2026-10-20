create table model_usage_record(id uuid primary key,model_name varchar(120) not null,kind varchar(30) not null,total_tokens bigint check(total_tokens>=0),succeeded boolean not null,created_at timestamptz not null default now());
create index idx_model_usage_created on model_usage_record(created_at);
create table token_budget_policy(id int primary key check(id=1),token_limit bigint not null default 0 check(token_limit>=0),warning_percent int not null default 20 check(warning_percent between 1 and 99),enabled boolean not null default true,updated_at timestamptz not null default now());
insert into token_budget_policy(id) values(1);
create table token_budget_month(month varchar(7) primary key,baseline_tokens bigint not null default 0 check(baseline_tokens>=0),alerted boolean not null default false);
create table platform_notice(id bigserial primary key,month varchar(7) not null,title varchar(100) not null,body text not null,created_at timestamptz not null default now());
create table platform_notice_read(notice_id bigint not null references platform_notice(id),user_id bigint not null references app_user(id),primary key(notice_id,user_id));
