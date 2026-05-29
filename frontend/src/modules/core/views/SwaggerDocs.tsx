import React, { useEffect } from 'react';
import { ExternalLink } from 'lucide-react';
import { BACKEND_URL } from '@/utils/api';

const SWAGGER_UI_URL = `${BACKEND_URL}/api/v1/docs/swagger`;

export const SwaggerDocs: React.FC = () => {
    useEffect(() => {
        window.location.assign(SWAGGER_UI_URL);
    }, []);

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex items-center justify-center px-4">
            <div className="max-w-xl w-full bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-xl text-center">
                <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-3">Opening Swagger UI</h1>
                <p className="text-sm text-slate-600 dark:text-slate-400 mb-6">
                    If you are not redirected automatically, open the Swagger page manually.
                </p>
                <a
                    href={SWAGGER_UI_URL}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-2 px-5 py-3 rounded-xl font-bold bg-slate-900 dark:bg-white text-white dark:text-slate-900"
                >
                    Open Swagger UI <ExternalLink className="w-4 h-4" />
                </a>
            </div>
        </div>
    );
};
