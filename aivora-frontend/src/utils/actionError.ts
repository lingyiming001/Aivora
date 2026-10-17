import axios from 'axios';

export const explain = (e: unknown) =>
  axios.isAxiosError(e) ? e.response?.data?.message || '操作失败，请重试' : '操作失败，请重试';
