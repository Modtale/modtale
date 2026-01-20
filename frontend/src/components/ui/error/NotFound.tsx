import React from 'react';
import { Link } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import { AlertCircle } from 'lucide-react';

const NotFound: React.FC = () => {
    return (
        <div className="min-h-[60vh] flex flex-col items-center justify-center p-4 text-center">
            <Helmet>
                <title>404 - Page Not Found | Modtale</title>
                <meta name="prerender-status-code" content="404" />
            </Helmet>

            <div className="bg-red-50 dark:bg-red-900/10 p-4 rounded-full mb-6">
                <AlertCircle className="w-12 h-12 text-red-500 dark:text-red-400" />
            </div>

            <h1 className="text-4xl font-black text-slate-900 dark:text-white mb-4">
                Page Not Found
            </h1>

            <p className="text-lg text-slate-600 dark:text-slate-400 max-w-md mb-8">
                The mod, modpack, or page you are looking for doesn't exist or has been removed.
            </p>

            <Link
                to="/"
                className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl transition-colors shadow-lg shadow-blue-600/20"
            >
                Return Home
            </Link>
        </div>
    );
};

export default NotFound;