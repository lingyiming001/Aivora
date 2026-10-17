import axios from 'axios';

export const errorMessage = (e: unknown) =>
  axios.isAxiosError(e) ? e.response?.data?.message || '保存失败' : '保存失败';
