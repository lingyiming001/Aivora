import { type ThemeConfig } from 'antd';

export const uiTheme: ThemeConfig = {
  token: {
    colorPrimary: '#267b69',
    colorInfo: '#267b69',
    borderRadius: 10,
    fontFamily: 'Inter,"PingFang SC","Microsoft YaHei",sans-serif',
    colorText: '#293e35',
    colorTextSecondary: '#708277',
    colorBorder: '#dfe7e2',
  },
  components: {
    Table: { headerBg: '#f4f7f5', headerColor: '#698073', cellPaddingBlock: 18 },
    Button: { controlHeight: 36 },
    Tabs: { horizontalItemGutter: 28 },
  },
};
