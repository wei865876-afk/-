import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 开发态：npm run dev 起在 5173，接口代理到 Spring Boot 8080
// 生产态：npm run build 输出到 Spring Boot 静态目录，随 jar 一起部署（访问 /app/）
export default defineConfig({
  plugins: [vue()],
  base: '/app/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/ai': { target: 'http://localhost:8080', changeOrigin: true },
      '/user': { target: 'http://localhost:8080', changeOrigin: true },
      '/student': { target: 'http://localhost:8080', changeOrigin: true },
      '/score': { target: 'http://localhost:8080', changeOrigin: true },
      '/clazz': { target: 'http://localhost:8080', changeOrigin: true },
      '/major': { target: 'http://localhost:8080', changeOrigin: true },
      '/department': { target: 'http://localhost:8080', changeOrigin: true },
      '/rewardPunish': { target: 'http://localhost:8080', changeOrigin: true },
      '/changeApply': { target: 'http://localhost:8080', changeOrigin: true }
    }
  },
  build: {
    outDir: '../src/main/resources/static/app',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500
  }
})
