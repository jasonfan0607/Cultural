import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { agentProxyOptions } from './agent-proxy.js'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  css: {
    preprocessorOptions: {
      less: {
        javascriptEnabled: true,
      },
    },
  },
  server: {
    host: true,
    port: 5175,
    proxy: {
      '/agent-api': agentProxyOptions(process.env.AGENT_PORT || '8081'),
    },
  },
})
