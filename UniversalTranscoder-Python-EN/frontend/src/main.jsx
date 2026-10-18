// 应用入口文件：创建 Vue 应用实例并挂载到页面节点上。
import {createApp} from 'vue'      // 从 vue 包引入应用工厂函数
import {App} from './App.jsx'      // 引入根组件（命名导出的 App）
import './styles.css'                // 引入全局样式（深色科技风主题）

// 创建应用实例并立即挂载到 index.html 中的 #app 节点。
createApp(App).mount('#app')
