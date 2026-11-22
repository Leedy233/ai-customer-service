import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [
      vue(),
      tailwindcss(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver({ importStyle: false })],
        dts: 'auto-imports.d.ts',
      }),
      Components({
        resolvers: [ElementPlusResolver({ importStyle: false })],
        dts: 'components.d.ts',
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      proxy: {
        '/ai': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8082',
          changeOrigin: true,
        },
      },
    },
    build: {
      target: 'es2020',
      chunkSizeWarningLimit: 1200,
    },
  }
})