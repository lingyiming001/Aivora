import { uiTheme } from '@/config';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { QueryClientProvider } from '@tanstack/react-query';
import { qc } from '@/services/queryClient';
import Router from '@/router';

export default function App() {
  return (
    <ConfigProvider locale={zhCN} theme={uiTheme}>
      <QueryClientProvider client={qc}>
        <Router />
      </QueryClientProvider>
    </ConfigProvider>
  );
}
