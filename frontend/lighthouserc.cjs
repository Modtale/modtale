module.exports = {
    ci: {
        collect: {
            url: [
                'http://127.0.0.1:4173/',
                'http://127.0.0.1:4173/mods',
                'http://127.0.0.1:4173/mod/mock-plugin-waystones',
            ],
            startServerCommand: 'PORT=4173 HOST=127.0.0.1 npm run start',
            startServerReadyPattern: 'Local|Network|listening|ready|http://localhost:4173|http://127.0.0.1:4173',
            startServerReadyTimeout: 30000,
            numberOfRuns: 3,
            settings: {
                chromeFlags: '--no-sandbox --disable-dev-shm-usage',
            },
        },
        assert: {
            aggregationMethod: 'median-run',
            assertions: {
                'categories:performance': ['error', { minScore: 1 }],
                'categories:accessibility': ['error', { minScore: 1 }],
                'categories:best-practices': ['error', { minScore: 1 }],
                'categories:seo': ['error', { minScore: 1 }],
            },
        },
        upload: {
            target: 'filesystem',
            outputDir: '.lighthouseci',
        },
    },
};
