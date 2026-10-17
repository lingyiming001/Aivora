export type Profile = {
  id: number;
  nickname: string;
  username: string | null;
  email: string;
  phone: string | null;
  avatar_url: string;
  bio: string;
  membership_tier: string;
  balance: number;
  character_count: number;
  created_at: string;
};

export type ProfileUpdate = { nickname: string; bio: string; avatarUrl: string };
