import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwind from '@astrojs/tailwind';
import node from '@astrojs/node';

export default defineConfig({
    output: 'server',
    adapter: node({
        mode: 'standalone',
    }),
    integrations: [
        react(),
        tailwind({
            applyBaseStyles: false,
        })
    ],
    server: {
        port: Number(process.env.PORT) || 5173,
        host: true
    },
    vite: {
        ssr: {
            noExternal: ['react-router-dom', 'lucide-react', 'react-easy-crop', 'react-helmet-async']
        },
        define: {
            'import.meta.env.PUBLIC_API_URL': JSON.stringify(process.env.PUBLIC_API_URL)
        },
        build: {
            target: 'esnext',
            cssCodeSplit: true,
            rollupOptions: {
                output: {
                    manualChunks: (id) => {
                        if (id.includes('node_modules')) {
                            if (id.includes('react')) return 'vendor-react';
                            if (id.includes('lucide')) return 'vendor-icons';
                            return 'vendor';
                        }
                    }
                }
            }
        }
    }
});