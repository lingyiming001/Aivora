import '@ant-design/v5-patch-for-react-19';
import '@/assets/styles/index.css';
import { createRoot } from 'react-dom/client';
import App from './App';
import { initializeTheme } from '@/layouts/components/ThemeSwitch';

initializeTheme();
const rootElement = document.getElementById('root')! as HTMLElement & {
  reactRoot?: ReturnType<typeof createRoot>;
};
const root = rootElement.reactRoot ?? (rootElement.reactRoot = createRoot(rootElement));

root.render(<App />);
