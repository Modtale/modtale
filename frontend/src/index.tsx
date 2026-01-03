import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import { BrowserRouter } from 'react-router-dom';
import { ErrorBoundary } from './components/ui/error/ErrorBoundary.tsx';
import { initErrorTracking } from './utils/errorTracking';
import { HelmetProvider } from 'react-helmet-async';

initErrorTracking();

const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement);

root.render(
    <React.StrictMode>
        <HelmetProvider>
            <ErrorBoundary>
                <BrowserRouter>
                    <App />
                </BrowserRouter>
            </ErrorBoundary>
        </HelmetProvider>
    </React.StrictMode>
);