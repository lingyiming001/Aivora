export type Admin = {
  id: number;
  nickname: string;
  email: string;
  admin_super: boolean;
  status: string;
  role_ids: number[];
  permissions: string[];
  phone?: string;
  session_count: number;
};
