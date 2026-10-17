export type Provider = {
  kind: string;
  id: number;
  name: string;
  base_url: string;
  enabled: boolean;
  key_configured: boolean;
};

export type Model = {
  model_type: string;
  id: number;
  provider_id: number;
  provider: string;
  model_name: string;
  star_per_request: number;
  enabled: boolean;
  is_default: boolean;
  provider_enabled: boolean;
};
