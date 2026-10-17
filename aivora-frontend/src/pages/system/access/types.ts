export type Role = {
  id: number;
  code: string;
  name: string;
  description: string;
  enabled: boolean;
  menus: string[];
  member_count: number;
};

export type MenuPermission = {
  menu_key: string;
  name: string;
  group_name: string;
  path: string;
  description: string;
  builtin: boolean;
  system_only: boolean;
  sort_order: number;
};
