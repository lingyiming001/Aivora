export type Character = {
  id: number;
  name: string;
  summary: string;
  opening: string;
  tags: string;
  avatar_url?: string;
  stars_per_message: number;
  visibility: string;
  status?: string;
  review_note?: string;
  category?: string;
};

export type Card = {
  id: number;
  name: string;
  summary: string;
  opening: string;
  avatar_url?: string;
  category: string;
  tags: string;
  visibility: string;
  stars_per_message: number;
};
