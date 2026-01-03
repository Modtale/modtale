import React, { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { captureError } from '../../../utils/errorTracking.ts';

interface Props {
    children: ReactNode;
}

interface State {
    hasError: boolean;
    error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
    public state: State = {
        hasError: false,
        error: null,
    };

    public static getDerivedStateFromError(error: Error): State {
        return { hasError: true, error };
    }

    public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
        console.error("Uncaught error:", error, errorInfo);
        captureError(error, { componentStack: errorInfo.componentStack });
    }

    public render() {
        if (this.state.hasError) {
            return (
                <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark flex items-center justify-center p-4">
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 rounded-xl p-8 max-w-lg w-full text-center shadow-2xl">
                        <div className="mx-auto w-16 h-16 bg-red-500/10 rounded-full flex items-center justify-center mb-4">
                            <AlertTriangle className="w-8 h-8 text-red-500" />
                        </div>
                        <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Application Error</h1>

                        <div className="bg-slate-100 dark:bg-black/30 p-4 rounded-lg text-left mb-6 overflow-auto max-h-48">
                            <p className="text-xs font-mono text-red-600 dark:text-red-400 whitespace-pre-wrap break-words">
                                {this.state.error?.toString()}
                            </p>
                        </div>

                        <button
                            onClick={() => window.location.href = '/'}
                            className="inline-flex items-center justify-center px-6 py-3 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-lg font-bold transition-colors shadow-lg shadow-blue-500/25"
                        >
                            <RefreshCw className="w-4 h-4 mr-2" />
                            Reload Application
                        </button>
                    </div>
                </div>
            );
        }

        return this.props.children;
    }
}