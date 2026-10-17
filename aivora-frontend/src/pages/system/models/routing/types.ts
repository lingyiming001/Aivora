export type Model = {
  composition_allowed: boolean;
  model_type: string;
  id: number;
  provider: string;
  model_name: string;
  enabled: boolean;
  provider_enabled: boolean;
};

export type Rule = {
  helper_model_ids: number[];
  min_characters: number;
  id: number;
  name: string;
  user_tier: string;
  min_stars: number;
  max_stars: number | null;
  model_id: number;
  priority: number;
  enabled: boolean;
  provider: string;
  model_name: string;
  model_available: boolean;
};
