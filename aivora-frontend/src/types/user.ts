export type AdminSession = {
  role: string;
  id?: number;
  nickname?: string;
  avatar_url?: string;
  menus: string[];
  superAdmin: boolean;
  menuCatalog?: import('@/pages/system/access/types').MenuPermission[];
};

export type Tokens = { accessToken: string; refreshToken: string; expiresAt?: number };
