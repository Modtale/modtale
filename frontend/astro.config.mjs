import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import node from '@astrojs/node';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
    output: 'server',
    adapter: node({
        mode: 'middleware',
    }),
    integrations: [
        react()
    ],
    server: {
        port: Number(process.env.PORT) || 5173,
        host: true
    },
    vite: {
        plugins: [
            tailwindcss()
        ],
        optimizeDeps: {
            include: ['@sentry/react', 'react-easy-crop']
        },
        ssr: {
            noExternal: ['lucide-react', 'react-easy-crop', 'react-helmet-async']
        },
        define: {
            'import.meta.env.PUBLIC_API_URL': JSON.stringify(process.env.PUBLIC_API_URL),
            'import.meta.env.PUBLIC_STATUS_URL': JSON.stringify(process.env.PUBLIC_STATUS_URL)
        },
        build: {
            target: 'esnext',
            cssCodeSplit: true,
            rollupOptions: {
                output: {
                    manualChunks(id) {
                        if (id.includes('/node_modules/@sentry/')) {
                            return 'sentry';
                        }
                        if (
                            id.includes('/node_modules/react/') ||
                            id.includes('/node_modules/react-dom/') ||
                            id.includes('/node_modules/react-router-dom/') ||
                            id.includes('/node_modules/react-helmet-async/')
                        ) {
                            return 'react-core';
                        }
                        if (id.includes('/node_modules/lucide-react/')) {
                            return 'icons';
                        }
                    }
                }
            }
        }
    }
});
