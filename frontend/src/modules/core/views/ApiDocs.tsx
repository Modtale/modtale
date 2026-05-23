import React, { useEffect, useMemo, useState } from 'react';
import {
    Shield,
    Server,
    ExternalLink,
    Lock,
    Unlock,
    Layers,
    FileText,
    AlertTriangle,
    Database,
    Globe,
    Download,
    User,
    Users,
    Bell,
    Key,
} from 'lucide-react';
import { Link } from 'react-router-dom';

type EndpointParam = {
    name: string;
    in: string;
    required: boolean;
    type?: string;
    description?: string;
};

type EndpointRequestBody = {
    required: boolean;
    contentTypes: string[];
    schemaHints?: string[];
};

type EndpointDoc = {
    method: string;
    path: string;
    summary: string;
    description?: string;
    public: boolean;
    params: EndpointParam[];
    requestBody?: EndpointRequestBody;
    responses: string[];
};

type OpenApiIndex = {
    title: string;
    version: string;
    server: string;
    totalEndpoints: number;
    endpoints: EndpointDoc[];
};

const ScrollbarStyles = () => (
    <style>{`
        .response-block::-webkit-scrollbar,
        .param-list::-webkit-scrollbar {
            height: 8px;
            width: 8px;
        }
        .response-block::-webkit-scrollbar-thumb,
        .param-list::-webkit-scrollbar-thumb {
            background: #475569;
            border-radius: 4px;
        }
        .response-block::-webkit-scrollbar-track,
        .param-list::-webkit-scrollbar-track {
            background: rgba(0, 0, 0, 0.2);
            border-radius: 4px;
        }
    `}</style>
);

const getMethodColor = (method: string): string => {
    if (method === 'GET') return 'bg-blue-600';
    if (method === 'POST') return 'bg-green-600';
    if (method === 'PUT') return 'bg-amber-500';
    if (method === 'DELETE') return 'bg-red-500';
    return 'bg-slate-500';
};

const classifySection = (path: string): string => {
    if (path.startsWith('/api/v1/auth/')) return 'Auth & Session';

    if (
        path === '/api/v1/tags' ||
        path.startsWith('/api/v1/meta/') ||
        path.startsWith('/api/v1/status') ||
        path.startsWith('/api/v1/wiki/') ||
        path.startsWith('/api/v1/og/') ||
        path === '/api/v1/analytics/platform/stats'
    ) {
        return 'Metadata & System';
    }

    if (
        path.startsWith('/api/v1/projects') ||
        path.startsWith('/api/v1/version/') ||
        path.startsWith('/api/v1/download/') ||
        path.startsWith('/api/v1/download-bundle/')
    ) {
        return 'Projects, Versions & Downloads';
    }

    if (
        path.startsWith('/api/v1/user/') ||
        path.startsWith('/api/v1/users/') ||
        path.startsWith('/api/v1/creators/')
    ) {
        return 'Users & Profiles';
    }

    if (path === '/api/v1/orgs' || path.startsWith('/api/v1/orgs/')) return 'Organizations & Connections';
    if (path === '/api/v1/notifications' || path.startsWith('/api/v1/notifications/') || path === '/api/v1/reports') return 'Notifications & Reports';

    return 'Other';
};

const sectionIcon = (section: string): React.ReactNode => {
    if (section === 'Metadata & System') return <Database className="w-6 h-6 text-slate-400" />;
    if (section === 'Projects, Versions & Downloads') return <Download className="w-6 h-6 text-slate-400" />;
    if (section === 'Users & Profiles') return <User className="w-6 h-6 text-slate-400" />;
    if (section === 'Organizations & Connections') return <Users className="w-6 h-6 text-slate-400" />;
    if (section === 'Notifications & Reports') return <Bell className="w-6 h-6 text-slate-400" />;
    if (section === 'Auth & Session') return <Key className="w-6 h-6 text-slate-400" />;
    return <Globe className="w-6 h-6 text-slate-400" />;
};

const EndpointCard: React.FC<{ endpoint: EndpointDoc }> = ({ endpoint }) => {
    return (
        <div className="border-b border-slate-100 dark:border-white/5 pb-8 mb-8 last:border-0 last:pb-0 last:mb-0 w-full">
            <div className="flex flex-col md:flex-row md:items-center gap-3 font-mono text-sm mb-2">
                <div className="flex items-center gap-2">
                    <span className={`px-2 py-1 rounded text-xs font-bold text-white shadow-sm min-w-[60px] text-center ${getMethodColor(endpoint.method)}`}>
                        {endpoint.method}
                    </span>
                    {endpoint.public ? (
                        <span className="flex items-center gap-1 text-[10px] uppercase font-bold text-slate-500 bg-slate-100 dark:text-slate-400 dark:bg-white/5 px-1.5 py-0.5 rounded border border-slate-200 dark:border-white/10">
                            <Unlock className="w-3 h-3" /> Public
                        </span>
                    ) : (
                        <span className="flex items-center gap-1 text-[10px] uppercase font-bold text-amber-600 bg-amber-50 dark:text-amber-400 dark:bg-amber-500/10 px-1.5 py-0.5 rounded border border-amber-200 dark:border-amber-500/20">
                            <Lock className="w-3 h-3" /> Auth
                        </span>
                    )}
                </div>
                <span className="text-slate-700 dark:text-slate-300 font-bold select-all break-all text-base">{endpoint.path}</span>
            </div>

            <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed max-w-4xl mb-4">
                {endpoint.summary || 'No summary provided.'}
            </p>

            {endpoint.description && (
                <div className="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-100 dark:border-blue-800 rounded-lg text-xs text-blue-700 dark:text-blue-300 mb-4">
                    {endpoint.description}
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <div className="space-y-4">
                    {endpoint.params.length > 0 && (
                        <div className="bg-slate-50 dark:bg-black/20 p-4 rounded-lg text-xs border border-slate-100 dark:border-white/5">
                            <h4 className="font-bold text-slate-500 uppercase mb-2 flex items-center gap-2">
                                <Layers className="w-3 h-3" /> Parameters
                            </h4>
                            <div className="param-list max-h-52 overflow-auto space-y-2">
                                {endpoint.params.map((p) => (
                                    <div key={`${endpoint.path}:${endpoint.method}:${p.name}`} className="border-b border-slate-200 dark:border-white/10 last:border-0 pb-2 last:pb-0">
                                        <div className="font-mono text-slate-700 dark:text-slate-200 break-all">
                                            {p.name} <span className="text-slate-400">({p.in}{p.required ? ', required' : ''}{p.type ? `, ${p.type}` : ''})</span>
                                        </div>
                                        {p.description && <div className="text-slate-500 dark:text-slate-400">{p.description}</div>}
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {endpoint.requestBody && (
                        <div className="bg-slate-50 dark:bg-black/20 p-4 rounded-lg text-xs border border-slate-100 dark:border-white/5">
                            <h4 className="font-bold text-slate-500 uppercase mb-2 flex items-center gap-2">
                                <FileText className="w-3 h-3" /> Request Body
                            </h4>
                            <div className="text-slate-600 dark:text-slate-300">
                                <div className="mb-2">
                                    Required: <span className="font-semibold">{endpoint.requestBody.required ? 'Yes' : 'No'}</span>
                                </div>
                                <div className="mb-2 break-all">Content Types: {endpoint.requestBody.contentTypes.join(', ')}</div>
                                {endpoint.requestBody.schemaHints && endpoint.requestBody.schemaHints.length > 0 && (
                                    <div>
                                        Fields/Schema:
                                        <ul className="list-disc list-inside mt-1 space-y-0.5">
                                            {endpoint.requestBody.schemaHints.map((f) => (
                                                <li key={`${endpoint.path}:${endpoint.method}:${f}`} className="break-all">{f}</li>
                                            ))}
                                        </ul>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>

                <div className="response-block bg-slate-900 p-4 rounded-lg text-xs font-mono text-slate-300 overflow-auto border border-slate-800 h-fit max-h-[360px]">
                    <h4 className="font-bold text-slate-500 uppercase mb-2">Response Codes</h4>
                    {endpoint.responses.length > 0 ? (
                        <ul className="space-y-1">
                            {endpoint.responses.map((code) => (
                                <li key={`${endpoint.path}:${endpoint.method}:${code}`}>{code}</li>
                            ))}
                        </ul>
                    ) : (
                        <span>No response metadata.</span>
                    )}
                </div>
            </div>
        </div>
    );
};

export const ApiDocs: React.FC = () => {
    const [data, setData] = useState<OpenApiIndex | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let isMounted = true;

        fetch('/openapi-index.json')
            .then((r) => {
                if (!r.ok) throw new Error(`Failed to load endpoint index (${r.status})`);
                return r.json();
            })
            .then((json: OpenApiIndex) => {
                if (isMounted) setData(json);
            })
            .catch((err: unknown) => {
                if (isMounted) setError(err instanceof Error ? err.message : 'Failed to load API docs index.');
            });

        return () => {
            isMounted = false;
        };
    }, []);

    const grouped = useMemo(() => {
        if (!data) return new Map<string, EndpointDoc[]>();

        const map = new Map<string, EndpointDoc[]>();
        data.endpoints.forEach((ep) => {
            const section = classifySection(ep.path);
            if (!map.has(section)) map.set(section, []);
            map.get(section)!.push(ep);
        });

        map.forEach((arr) => {
            arr.sort((a, b) => {
                if (a.path === b.path) return a.method.localeCompare(b.method);
                return a.path.localeCompare(b.path);
            });
        });

        return map;
    }, [data]);

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 pb-20">
            <div className="w-full max-w-[112rem] px-4 sm:px-12 md:px-16 lg:px-28 mx-auto py-16 overflow-x-hidden">
                <ScrollbarStyles />

                <div className="text-center mb-12 w-full">
                    <h1 className="text-4xl md:text-5xl font-black text-slate-900 dark:text-white mb-4 tracking-tight">
                        Modtale <span className="text-modtale-accent">API v1</span>
                    </h1>
                    <p className="text-lg text-slate-600 dark:text-slate-400 max-w-3xl mx-auto mb-6">
                        This page is generated from the current OpenAPI index and mirrors controller-backed, publicly documented routes.
                    </p>

                    <div className="inline-flex flex-wrap justify-center items-center gap-3 px-5 py-3 bg-slate-100 dark:bg-white/5 rounded-full text-sm font-mono text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-white/10 mb-6 shadow-sm max-w-full">
                        <Server className="w-4 h-4 text-modtale-accent shrink-0" />
                        <span>Base URL:</span>
                        <span className="font-bold text-slate-900 dark:text-white select-all break-all">{data?.server ?? 'https://api.modtale.net'}</span>
                        {data && <span className="text-slate-400">• {data.totalEndpoints} operations</span>}
                    </div>

                    <div className="flex flex-col sm:flex-row justify-center items-center gap-4 w-full">
                        <Link to="/dashboard/developer" className="px-6 py-3 bg-slate-900 dark:bg-white text-white dark:text-slate-900 rounded-xl font-bold hover:opacity-90 transition-transform active:scale-95 shadow-lg flex items-center justify-center gap-2 w-full sm:w-auto">
                            <Shield className="w-4 h-4" /> Get API Key
                        </Link>
                        <a href="/openapi.yml" target="_blank" rel="noreferrer" className="px-6 py-3 bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-white rounded-xl font-bold hover:border-modtale-accent hover:text-modtale-accent transition-all active:scale-95 shadow-sm flex items-center justify-center gap-2 group w-full sm:w-auto">
                            <span>View OpenAPI YAML</span>
                            <ExternalLink className="w-3 h-3 opacity-50" />
                        </a>
                    </div>
                </div>

                {error && (
                    <div className="mb-8 p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-700 dark:text-red-300">
                        {error}
                    </div>
                )}

                {!data && !error && (
                    <div className="p-6 bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-white/10 rounded-2xl text-slate-600 dark:text-slate-300">
                        Loading API index...
                    </div>
                )}

                {data && (
                    <div className="space-y-10 md:space-y-14 w-full overflow-hidden">
                        {Array.from(grouped.entries()).map(([section, endpoints]) => (
                            <section key={section} className="w-full">
                                <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-5 flex items-center gap-2">
                                    {sectionIcon(section)} {section}
                                </h2>
                                <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl p-6 md:p-8 shadow-2xl w-full overflow-hidden">
                                    {endpoints.map((endpoint) => (
                                        <EndpointCard
                                            key={`${endpoint.method}:${endpoint.path}`}
                                            endpoint={endpoint}
                                        />
                                    ))}
                                </div>
                            </section>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};
