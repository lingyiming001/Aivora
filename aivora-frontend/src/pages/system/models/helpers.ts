import axios from 'axios';
export const explain = (e: unknown) =>
  axios.isAxiosError(e) ? e.response?.data?.message || '请求失败，请稍后重试' : '操作失败';
