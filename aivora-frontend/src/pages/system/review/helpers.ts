import axios from 'axios';

export const message = (e: unknown) =>
  axios.isAxiosError(e) ? e.response?.data?.message || '操作失败' : '操作失败';
