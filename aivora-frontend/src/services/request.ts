import axios from 'axios';
import { useAuth } from '@/store/user';
import { accessToken } from './auth';
import { API_BASE_URL, HTTP_TIMEOUT } from '@/config/env';

export const api = axios.create({ baseURL: API_BASE_URL, timeout: HTTP_TIMEOUT });
api.interceptors.request.use(async (config) => {
  const token = await accessToken();
  if (token) config.headers.Authorization = 'Bearer ' + token;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const token = useAuth.getState().adminTokens;
      if (token && error.config?.headers?.Authorization === 'Bearer ' + token.accessToken)
        useAuth.getState().setTokens(null, 'admin');
    }
    return Promise.reject(error);
  },
);
