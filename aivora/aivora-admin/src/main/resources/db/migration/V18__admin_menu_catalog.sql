create table admin_menu (
 menu_key varchar(40) primary key, name varchar(80) not null, group_name varchar(40) not null,
 path varchar(100) not null unique, description varchar(300) not null default '',
 builtin boolean not null default false, system_only boolean not null default false,
 sort_order integer not null default 0 check(sort_order>=0), updated_at timestamptz not null default now()
);
insert into admin_menu(menu_key,name,group_name,path,builtin,system_only,sort_order) values
('overview','运营概览','平台运营','/admin',true,false,10),
('users','用户管理','平台运营','/admin/users',true,false,20),
('review','角色卡管理','平台运营','/admin/review',true,false,30),
('models','模型与路由','AI 配置','/admin/models',true,false,40),
('orders','充值订单','财务管理','/admin/orders',true,false,50),
('ledger','钱包流水','财务管理','/admin/ledger',true,false,60),
('reconciliation','异常对账','财务管理','/admin/reconciliation',true,false,70),
('access','管理员管理','系统管理','/admin/access',true,true,80),
('roles','角色管理','系统管理','/admin/roles',true,true,90),
('permissions','菜单权限','系统管理','/admin/permissions',true,true,100),
('audit','操作日志','系统管理','/admin/audit',true,false,110),
('status','系统状态','系统管理','/admin/status',true,false,120);
