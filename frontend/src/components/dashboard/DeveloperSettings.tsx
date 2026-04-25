import React, { useEffect, useState } from 'react';
import { api } from '../../utils/api.ts';
import { Trash2, Plus, Copy, Key, Check, Shield, Info, ExternalLink, Github, ArrowRight, Code, CheckSquare, Square, Building2, User as UserIcon, Box } from 'lucide-react';
import { StatusModal } from '../ui/StatusModal.tsx';
import { Link } from 'react-router-dom';
import type { User, Mod } from '../../types.ts';
import { PermissionSelector, ALL_PERMISSION_GROUPS, getPermissionLabel, TOTAL_PERMISSIONS } from '../ui/PermissionSelector.tsx';

interface ApiKey {
    id: string;
    name: string;
    prefix: string;
    tier: 'USER' | 'ENTERPRISE';
    contextPermissions: Record<string, string[]>;
    createdAt: string;
    lastUsed: string | null;
}

const DEFAULT_PERMISSIONS = ['PROJECT_READ', 'VERSION_READ', 'VERSION_DOWNLOAD', 'PROFILE_READ', 'ORG_READ', 'NOTIFICATION_READ'];

interface DeveloperSettingsProps {
    user: User;
}

export const DeveloperSettings: React.FC<DeveloperSettingsProps> = ({ user }) => {
    const [keys, setKeys] = useState<ApiKey[]>([]);
    const [orgs, setOrgs] = useState<User[]>([]);
    const [projects, setProjects] = useState<Mod[]>([]);
    const [loading, setLoading] = useState(true);

    const [newKey, setNewKey] = useState<string | null>(null);
    const [keyName, setKeyName] = useState('');

    const [contextPerms, setContextPerms] = useState<Record<string, string[]>>({ PERSONAL: DEFAULT_PERMISSIONS });
    const [activeTab, setActiveTab] = useState<string>('PERSONAL');

    const [isCreating, setIsCreating] = useState(false);
    const [status, setStatus] = useState<any>(null);
    const [isCopied, setIsCopied] = useState(false);

    useEffect(() => {
        fetchKeys();
        fetchOrgs();
        fetchProjects();
    }, []);

    const fetchKeys = async () => {
        try {
            const res = await api.get('/user/api-keys');
            setKeys(res.data);
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    };

    const fetchOrgs = async () => {
        try {
            const res = await api.get('/user/orgs');
            setOrgs(res.data);

            setContextPerms(prev => {
                const updated = { ...prev };
                res.data.forEach((org: User) => {
                    if (!updated[org.id]) updated[org.id] = [];
                });
                return updated;
            });
        } catch (e) { console.error(e); }
    };

    const fetchProjects = async () => {
        try {
            const contribRes = await api.get('/projects/user/contributed?size=100');
            const ownedRes = await api.get(`/creators/${user.id}/projects?size=100`);

            const allProjects = [...(contribRes.data.content || []), ...(ownedRes.data.content || [])];

            const uniqueProjects = Array.from(new Map(allProjects.map(p => [p.id, p])).values());

            setProjects(uniqueProjects);

            setContextPerms(prev => {
                const updated = { ...prev };
                uniqueProjects.forEach((proj: Mod) => {
                    if (!updated[proj.id]) updated[proj.id] = [];
                });
                return updated;
            });
        } catch (e) { console.error(e); }
    };

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();

        const cleanContexts: Record<string, string[]> = {};
        let hasAnyPerms = false;

        Object.entries(contextPerms).forEach(([ctx, perms]) => {
            if (perms && perms.length > 0) {
                cleanContexts[ctx] = perms;
                hasAnyPerms = true;
            }
        });

        if (!hasAnyPerms) {
            setStatus({ type: 'error', title: 'Error', msg: 'Please select at least one permission across any profile, organization, or project.' });
            return;
        }

        setIsCreating(true);
        try {
            const res = await api.post('/user/api-keys', {
                name: keyName,
                contextPermissions: cleanContexts
            });
            setNewKey(res.data.key);
            setKeyName('');
            setContextPerms({ PERSONAL: DEFAULT_PERMISSIONS });
            setActiveTab('PERSONAL');
            fetchKeys();
        } catch (e: any) {
            if (e.response?.status === 403 && e.response?.data?.error === "Email verification required.") {
                setStatus({ type: 'error', title: 'Verification Required', msg: "You must verify your email address to generate API keys." });
            } else if (e.response?.data?.error) {
                setStatus({ type: 'error', title: 'Permission Error', msg: e.response.data.error });
            } else {
                setStatus({ type: 'error', title: 'Error', msg: 'Failed to create key.' });
            }
        } finally { setIsCreating(false); }
    };

    const confirmRevoke = (id: string) => {
        setStatus({
            type: 'warning',
            title: 'Revoke API Key?',
            message: 'Are you sure? This will break any integrations using this key.',
            actionLabel: 'Revoke Key',
            secondaryLabel: 'Cancel',
            onAction: () => executeRevoke(id)
        });
    };

    const executeRevoke = async (id: string) => {
        try {
            await api.delete(`/user/api-keys/${id}`);
            fetchKeys();
            setStatus(null);
        } catch(e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to revoke key.' });
        }
    };

    const handleCopyKey = () => {
        if (!newKey) return;
        navigator.clipboard.writeText(newKey);
        setIsCopied(true);
        setTimeout(() => setIsCopied(false), 2000);
    };

    const activePerms = contextPerms[activeTab] || [];

    const toggleAllPerms = () => {
        setContextPerms(prev => ({
            ...prev,
            [activeTab]: activePerms.length === TOTAL_PERMISSIONS ? [] : ALL_PERMISSION_GROUPS.flatMap(g => g.permissions.map(p => p.id))
        }));
    };

    return (
        <div className="w-full space-y-8">
            {status && <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} onAction={status.onAction} actionLabel={status.actionLabel} secondaryLabel={status.secondaryLabel} />}

            <div className="flex justify-between items-center mb-6">
                <div>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white">Developer Settings</h2>
                    <p className="text-slate-500 text-sm">Manage highly granular, context-aware API keys.</p>
                </div>
            </div>

            {newKey && (
                <div className="bg-white/40 dark:bg-white/5 backdrop-blur-md border border-green-500/30 p-6 rounded-2xl animate-in fade-in shadow-sm">
                    <h3 className="text-green-600 dark:text-green-400 font-bold text-lg mb-2 flex items-center gap-2">
                        <Key className="w-5 h-5" /> New Key Generated
                    </h3>
                    <p className="text-sm text-slate-600 dark:text-slate-300 mb-4">
                        Copy this key now. You won't be able to see it again!
                    </p>
                    <div className="flex flex-col sm:flex-row gap-2">
                        <code className="flex-1 bg-white/60 dark:bg-black/40 p-3 rounded-xl font-mono text-sm break-all border border-slate-200 dark:border-white/10 text-slate-800 dark:text-slate-200 select-all shadow-inner">
                            {newKey}
                        </code>
                        <button onClick={handleCopyKey} className={`px-6 py-3 rounded-xl font-bold transition-all flex items-center justify-center gap-2 min-w-[120px] ${isCopied ? 'bg-green-500 text-white shadow-lg shadow-green-500/20' : 'bg-slate-900 dark:bg-white text-white dark:text-slate-900 hover:bg-slate-800 dark:hover:bg-slate-200 shadow-lg'}`}>
                            {isCopied ? <><Check className="w-4 h-4" /> Copied!</> : <><Copy className="w-4 h-4" /> Copy</>}
                        </button>
                    </div>
                    <button onClick={() => setNewKey(null)} className="mt-4 text-xs font-bold text-slate-500 hover:text-slate-800 dark:hover:text-white transition-colors">
                        I have saved it, close this.
                    </button>
                </div>
            )}

            <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
                <div className="xl:col-span-2 flex flex-col">
                    <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden shadow-sm flex flex-col h-full backdrop-blur-md">
                        <div className="p-5 border-b border-slate-200 dark:border-white/10 flex justify-between items-center shrink-0">
                            <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2">
                                <Shield className="w-4 h-4 text-modtale-accent" /> Active API Keys
                            </h3>
                        </div>

                        <div className="divide-y divide-slate-200 dark:divide-white/5 flex-1 max-h-[400px] overflow-y-auto custom-scrollbar">
                            {keys.length === 0 ? (
                                <div className="p-8 text-center text-slate-500 text-sm">No active keys found.</div>
                            ) : keys.map(k => {
                                const activeContexts = Object.keys(k.contextPermissions || {});
                                return (
                                    <div key={k.id} className="p-5 flex items-start justify-between group hover:bg-white/50 dark:hover:bg-white/[0.02] transition-colors">
                                        <div className="w-full">
                                            <div className="flex justify-between items-start">
                                                <div>
                                                    <div className="font-bold text-slate-900 dark:text-white flex items-center gap-2">
                                                        {k.name}
                                                        <span className={`text-[10px] px-2 py-0.5 rounded-md uppercase border font-mono font-bold tracking-wider ${k.tier === 'ENTERPRISE' ? 'bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20' : 'bg-slate-200/50 dark:bg-white/10 text-slate-600 dark:text-slate-300 border-slate-300 dark:border-white/10'}`}>
                                                            {k.tier || 'USER'}
                                                        </span>
                                                    </div>
                                                    <div className="text-sm text-slate-500 font-mono mt-1.5 flex items-center gap-2">
                                                        <span>Prefix: <span className="bg-white/60 dark:bg-black/30 px-1.5 py-0.5 rounded text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-white/5">{k.prefix}••••••••</span></span>
                                                    </div>
                                                </div>
                                                <button onClick={() => confirmRevoke(k.id)} className="text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10 p-2.5 rounded-xl transition-colors mt-1" title="Revoke Key">
                                                    <Trash2 className="w-5 h-5" />
                                                </button>
                                            </div>

                                            <div className="mt-4 space-y-3">
                                                {activeContexts.map(ctx => {
                                                    const perms = k.contextPermissions[ctx] || [];
                                                    const isPersonal = ctx === 'PERSONAL';
                                                    const orgMatch = orgs.find(o => o.id === ctx);
                                                    const projMatch = projects.find(p => p.id === ctx);

                                                    const ctxName = isPersonal ? 'Personal Account' : (orgMatch?.username || projMatch?.title || 'Unknown Context');
                                                    const Icon = isPersonal ? UserIcon : (orgMatch ? Building2 : Box);

                                                    if (perms.length === 0) return null;

                                                    return (
                                                        <div key={ctx} className="flex flex-col gap-1.5">
                                                            <span className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
                                                                <Icon className="w-3.5 h-3.5" /> {ctxName}
                                                            </span>
                                                            <div className="flex flex-wrap gap-2">
                                                                {perms.length === TOTAL_PERMISSIONS ? (
                                                                    <span className="text-xs px-2 py-1 rounded bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20 font-bold">All Permissions ({TOTAL_PERMISSIONS})</span>
                                                                ) : (
                                                                    <>
                                                                        {perms.slice(0, 4).map(p => (
                                                                            <span key={p} className="text-xs px-2 py-1 rounded bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-500/20 font-medium whitespace-nowrap">
                                                                                {getPermissionLabel(p)}
                                                                            </span>
                                                                        ))}
                                                                        {perms.length > 4 && (
                                                                            <span className="text-xs px-2 py-1 rounded bg-slate-500/10 text-slate-600 dark:text-slate-300 border border-slate-500/20 font-medium">
                                                                                +{perms.length - 4} more
                                                                            </span>
                                                                        )}
                                                                    </>
                                                                )}
                                                            </div>
                                                        </div>
                                                    );
                                                })}
                                            </div>

                                            <div className="text-xs text-slate-400 mt-4 font-medium border-t border-slate-100 dark:border-white/5 pt-3">
                                                Created: {new Date(k.createdAt).toLocaleDateString()} • Last Used: {k.lastUsed ? new Date(k.lastUsed).toLocaleDateString() : 'Never'}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        <div className="p-5 border-t border-slate-200 dark:border-white/10 mt-auto shrink-0 bg-slate-50/50 dark:bg-white/[0.01]">
                            <form onSubmit={handleCreate} className="flex flex-col gap-6">
                                <input
                                    type="text"
                                    value={keyName}
                                    onChange={e => setKeyName(e.target.value)}
                                    placeholder="Key Name (e.g. GitHub Actions - Deploy)"
                                    className="w-full bg-white/60 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 text-base dark:text-white focus:ring-2 focus:ring-modtale-accent focus:border-transparent outline-none transition-all shadow-inner"
                                    required
                                />

                                <div className="space-y-4">
                                    <div className="flex flex-col gap-3 border-b border-slate-200 dark:border-white/10 pb-4">
                                        <div className="flex items-center justify-between">
                                            <label className="text-sm font-bold text-slate-800 dark:text-slate-200 uppercase tracking-wider">Configure Contexts & Permissions</label>
                                            <button
                                                type="button"
                                                onClick={toggleAllPerms}
                                                className="text-xs font-bold text-modtale-accent hover:text-modtale-accentHover transition-colors flex items-center gap-1.5"
                                            >
                                                {activePerms.length === TOTAL_PERMISSIONS ? <CheckSquare className="w-4 h-4" /> : <Square className="w-4 h-4" />}
                                                {activePerms.length === TOTAL_PERMISSIONS ? 'Deselect All' : 'Select All'}
                                            </button>
                                        </div>

                                        <div className="flex w-full overflow-x-auto gap-2 pb-2 pt-1 px-1 custom-scrollbar">
                                            <button
                                                type="button"
                                                onClick={() => setActiveTab('PERSONAL')}
                                                className={`shrink-0 px-4 py-2 text-sm font-bold rounded-xl flex items-center gap-2 transition-all whitespace-nowrap border ${activeTab === 'PERSONAL' ? 'bg-modtale-accent text-white border-modtale-accent shadow-md' : 'bg-white/60 dark:bg-white/5 text-slate-600 dark:text-slate-300 hover:bg-white dark:hover:bg-white/10 border-slate-200 dark:border-white/5'}`}
                                            >
                                                <UserIcon className="w-4 h-4" /> Personal Profile
                                                {contextPerms['PERSONAL']?.length > 0 && <span className={`ml-1 px-2 py-0.5 rounded-full text-xs ${activeTab === 'PERSONAL' ? 'bg-white/20' : 'bg-slate-200 dark:bg-white/10'}`}>{contextPerms['PERSONAL'].length}</span>}
                                            </button>

                                            {orgs.length > 0 && <div className="w-px h-6 bg-slate-200 dark:bg-white/10 my-auto mx-1" />}

                                            {orgs.map(org => (
                                                <button
                                                    key={org.id}
                                                    type="button"
                                                    onClick={() => setActiveTab(org.id)}
                                                    className={`shrink-0 px-4 py-2 text-sm font-bold rounded-xl flex items-center gap-2 transition-all whitespace-nowrap border ${activeTab === org.id ? 'bg-modtale-accent text-white border-modtale-accent shadow-md' : 'bg-white/60 dark:bg-white/5 text-slate-600 dark:text-slate-300 hover:bg-white dark:hover:bg-white/10 border-slate-200 dark:border-white/5'}`}
                                                >
                                                    <Building2 className="w-4 h-4" /> {org.username}
                                                    {contextPerms[org.id]?.length > 0 && <span className={`ml-1 px-2 py-0.5 rounded-full text-xs ${activeTab === org.id ? 'bg-white/20' : 'bg-slate-200 dark:bg-white/10'}`}>{contextPerms[org.id].length}</span>}
                                                </button>
                                            ))}

                                            {projects.length > 0 && <div className="w-px h-6 bg-slate-200 dark:bg-white/10 my-auto mx-1" />}

                                            {projects.map(proj => (
                                                <button
                                                    key={proj.id}
                                                    type="button"
                                                    onClick={() => setActiveTab(proj.id)}
                                                    className={`shrink-0 px-4 py-2 text-sm font-bold rounded-xl flex items-center gap-2 transition-all whitespace-nowrap border ${activeTab === proj.id ? 'bg-modtale-accent text-white border-modtale-accent shadow-md' : 'bg-white/60 dark:bg-white/5 text-slate-600 dark:text-slate-300 hover:bg-white dark:hover:bg-white/10 border-slate-200 dark:border-white/5'}`}
                                                >
                                                    <Box className="w-4 h-4" /> {proj.title}
                                                    {contextPerms[proj.id]?.length > 0 && <span className={`ml-1 px-2 py-0.5 rounded-full text-xs ${activeTab === proj.id ? 'bg-white/20' : 'bg-slate-200 dark:bg-white/10'}`}>{contextPerms[proj.id].length}</span>}
                                                </button>
                                            ))}
                                        </div>
                                    </div>

                                    <PermissionSelector
                                        groups={ALL_PERMISSION_GROUPS}
                                        selectedPermissions={activePerms}
                                        onChange={(perms) => setContextPerms(prev => ({ ...prev, [activeTab]: perms }))}
                                        variant="panel"
                                    />
                                </div>

                                <div className="flex justify-between items-center pt-4 border-t border-slate-200 dark:border-white/10">
                                    <span className="text-sm text-slate-500 font-medium">
                                        Total Permissions Selected: <span className="font-black text-modtale-accent">{Object.values(contextPerms).reduce((sum, arr) => sum + (arr?.length || 0), 0)}</span>
                                    </span>
                                    <button disabled={isCreating} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-3 rounded-xl font-bold flex items-center justify-center gap-2 text-base transition-all shadow-xl shadow-modtale-accent/10 active:scale-95 disabled:opacity-70 disabled:cursor-not-allowed w-full sm:w-auto">
                                        <Plus className="w-5 h-5" /> Generate Key
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>

                <div className="space-y-6">
                    <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl p-6 shadow-sm hover:border-modtale-accent/30 transition-colors group backdrop-blur-md">
                        <div className="w-12 h-12 bg-blue-500/10 dark:bg-blue-400/10 rounded-xl flex items-center justify-center text-blue-600 dark:text-blue-400 mb-4 group-hover:scale-110 group-hover:bg-blue-500/20 transition-all">
                            <Info className="w-6 h-6" />
                        </div>
                        <h3 className="font-bold text-lg text-slate-900 dark:text-white mb-2">API Documentation</h3>
                        <p className="text-sm text-slate-600 dark:text-slate-400 mb-6">Full reference for endpoints, rate limits, and authentication.</p>
                        <Link to="/api-docs" className="text-modtale-accent font-bold text-sm flex items-center gap-2 hover:underline">
                            View Documentation <ArrowRight className="w-4 h-4" />
                        </Link>
                    </div>

                    <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl p-6 shadow-sm relative overflow-hidden group backdrop-blur-md">
                        <div className="absolute top-0 right-0 p-4 opacity-[0.03] dark:opacity-5 group-hover:opacity-[0.05] dark:group-hover:opacity-10 transition-opacity">
                            <Github className="w-24 h-24 transform rotate-12 text-slate-900 dark:text-white" />
                        </div>
                        <div className="relative z-10">
                            <h3 className="font-bold text-lg mb-2 flex items-center gap-2 text-slate-900 dark:text-white">
                                <Code className="w-5 h-5 text-modtale-accent" /> Examples
                            </h3>
                            <p className="text-slate-600 dark:text-slate-400 text-sm mb-6 leading-relaxed">
                                Ready-to-use scripts for GitHub Actions, Gradle, and Maven integrations.
                            </p>
                            <a href="https://github.com/Modtale/modtale-example" target="_blank" rel="noreferrer" className="inline-flex items-center justify-center w-full px-4 py-3 bg-white/80 dark:bg-white/10 text-slate-900 dark:text-white font-bold rounded-xl hover:bg-white dark:hover:bg-white/20 border border-slate-200 dark:border-white/5 transition-all gap-2 text-sm shadow-sm">
                                <Github className="w-4 h-4" /> View on GitHub
                            </a>
                        </div>
                    </div>
                </div>
            </div>

            <div className="border-t border-slate-200 dark:border-white/10 pt-8 mt-12 text-center">
                <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">
                    Need higher rate limits? <a href="mailto:support@modtale.net" className="text-modtale-accent hover:underline font-bold">Contact us</a> for Enterprise access.
                </p>
            </div>
        </div>
    );
};