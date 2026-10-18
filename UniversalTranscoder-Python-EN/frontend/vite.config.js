// Vite 构建配置文件（万能转码器前端）
import {defineConfig} from 'vite'                    // 引入 Vite 的配置定义函数
import vueJsx from '@vitejs/plugin-vue-jsx'            // 引入 Vue 官方的 JSX 插件（本项目全部组件使用 .jsx）

export default defineConfig({
    // 关键设置：使用相对基址。
    // 原因：FastAPI 把 dist 目录直接挂载到根路径，产物内部引用必须相对化才能在任意挂载点工作。
    base: './',
    // 插件列表：只需要 JSX 插件，因为本项目不使用 .vue 单文件组件。
    plugins: [vueJsx()],
    // 构建配置。
    build: {
        // 产物输出目录，必须与后端约定的 frontend/dist 一致。
        outDir: 'dist',
        // 静态资源子目录名，后端会把它挂载到 /assets。
        assetsDir: 'assets',
        // 构建前清空输出目录，避免旧产物残留造成“看起来构建成功”的假象。
        emptyOutDir: true,
        // 目标浏览器语法级别；es2018 足以覆盖较老的 Chromium/Edge。
        target: 'es2018',
        // 生产构建关闭 sourcemap，减小体积并避免泄露源码路径。
        sourcemap: false,
        // 提高单文件告警阈值，避免正常体积的产物在日志里刷告警。
        chunkSizeWarningLimit: 900
    },
    // 开发服务器配置（仅在 npm run dev 时生效，生产构建完全不依赖它）。
    server: {
        // 固定端口，便于本地调试时记忆。
        port: 5173,
        // 开发代理：把 API 与 WebSocket 转发到 FastAPI 默认端口 8765，避免开发期跨域。
        proxy: {
            // 普通 HTTP 接口代理。
            '/api': {target: 'http://127.0.0.1:8765', changeOrigin: true},
            // WebSocket 进度通道代理。
            '/ws': {target: 'ws://127.0.0.1:8765', ws: true, changeOrigin: true}
        }
    }
})
