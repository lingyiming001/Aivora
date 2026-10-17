import axios from 'axios';

export const msg = (e: unknown) =>
  axios.isAxiosError(e) ? e.response?.data?.message || '操作失败' : '操作失败';
