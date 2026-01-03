import typography from '@tailwindcss/typography';
import forms from '@tailwindcss/forms';

export default {
    content: [
        './src/**/*.{astro,html,js,jsx,md,mdx,svelte,ts,tsx,vue}'
    ],
    darkMode: 'class',
    theme: {
        extend: {
            colors: {
                modtale: {
                    dark: '#0f172a', // Slate-900
                    card: '#1e293b', // Slate-800
                    cardHover: '#334155', // Slate-700
                    border: 'rgba(255, 255, 255, 0.08)',
                    accent: '#3b82f6', // Blue-500
                    accentHover: '#2563eb', // Blue-600
                    accentDim: 'rgba(59, 130, 246, 0.1)'
                }
            },
            fontFamily: {
                sans: ['Inter', 'sans-serif'],
            }
        },
    },
    plugins: [
        typography,
        forms,
    ],
}