import DefaultTheme from 'vitepress/theme'
import './custom.css'

import TelemetryPanel from './components/TelemetryPanel.vue'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    // 首页尾部的实时功率面板（示意数据）
    app.component('TelemetryPanel', TelemetryPanel)
  },
}
