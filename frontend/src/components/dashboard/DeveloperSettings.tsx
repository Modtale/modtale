import React, { useEffect, useState } from 'react';
import { api } from '../../utils/api.ts';
import { Trash2, Plus, Copy, Key, Check, Shield, Info, ExternalLink, Github, ArrowRight, Code } from 'lucide-react';
import { StatusModal } from '../ui/StatusModal.tsx';
import { Link } from 'react-router-dom';

interface ApiKey {
    id: string;
    name: string;
    prefix: string;
    tier: 'USER' | 'ENTERPRISE';
    createdAt: string;
    lastUsed: string | null;
}

export const DeveloperSettings: React.FC = () => {
    const [keys, setKeys] = useState<ApiKey[]>([]);
    const [loading, setLoading] = useState(true);
    const [newKey, setNewKey] = useState<string | null>(null);
    const [keyName, setKeyName] = useState('');
    const [isCreating, setIsCreating] = useState(false);
    const [status, setStatus] = useState<any>(null);
    const [isCopied, setIsCopied] = useState(false);

    useEffect(() => { fetchKeys(); }, []);

    const fetchKeys = async () => {
        try {
            const res = await api.get('/user/api-keys');
            setKeys(res.data);
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    };

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsCreating(true);
        try {
            const res = await api.post('/user/api-keys', { name: keyName });
            setNewKey(res.data.key);
            setKeyName('');
            fetchKeys();
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to create key.' });
        } finally { setIsCreating(false); }
    };

    const confirmRevoke = (id: string) => {
        setStatus({
            type: 'warning',
            title: 'Revoke API Key?',
            message: 'Are you sure? This will break any CI/CD pipelines using this key.',
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

    return (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full space-y-8">
            {status && <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} onAction={status.onAction} actionLabel={status.actionLabel} secondaryLabel={status.secondaryLabel} />}

            <div className="flex justify-between items-center">
                <div>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white">Developer Settings</h2>
                    <p className="text-slate-500 text-sm">Manage API keys for automation and CI/CD.</p>
                </div>
            </div>

            {newKey && (
                <div className="bg-green-500/10 border border-green-500/50 p-6 rounded-xl animate-in fade-in ring-1 ring-green-500/50 shadow-lg shadow-green-500/10">
                    <h3 className="text-green-600 dark:text-green-400 font-bold text-lg mb-2 flex items-center gap-2"><Key className="w-5 h-5" /> New Key Generated</h3>
                    <p className="text-sm text-slate-600 dark:text-slate-300 mb-4">Copy this key now. You won't be able to see it again!</p>
                    <div className="flex gap-2">
                        <code className="flex-1 bg-white dark:bg-black/30 p-3 rounded-lg font-mono text-sm break-all border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-200 select-all">{newKey}</code>
                        <button onClick={handleCopyKey} className={`px-4 rounded-lg font-bold transition-all flex items-center gap-2 min-w-[100px] justify-center ${isCopied ? 'bg-green-500 text-white cursor-default' : 'bg-white dark:bg-white/10 hover:bg-slate-50 dark:hover:bg-white/20 text-slate-700 dark:text-white border border-slate-200 dark:border-white/10'}`}>{isCopied ? <><Check className="w-4 h-4" /> Copied!</> : <><Copy className="w-4 h-4" /> Copy</>}</button>
                    </div>
                    <button onClick={() => setNewKey(null)} className="mt-4 text-xs font-bold text-slate-500 hover:text-slate-800 dark:hover:text-white hover:underline transition-colors">I have saved it, close this.</button>
                </div>
            )}

            <div className="grid grid-cols-1 xl:grid-cols-3 gap-8">
                <div className="xl:col-span-2 flex flex-col">
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl overflow-hidden shadow-sm flex flex-col h-full">
                        <div className="p-4 border-b border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-white/5 flex justify-between items-center shrink-0">
                            <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2"><Shield className="w-4 h-4 text-modtale-accent" /> Active API Keys</h3>
                        </div>
                        <div className="divide-y divide-slate-200 dark:divide-white/5 flex-1">
                            {keys.length === 0 ? (
                                <div className="p-8 text-center text-slate-500 text-sm">No active keys found.</div>
                            ) : keys.map(k => (
                                <div key={k.id} className="p-4 flex items-center justify-between group hover:bg-slate-50 dark:hover:bg-white/[0.02] transition-colors">
                                    <div>
                                        <div className="font-bold text-slate-900 dark:text-white flex items-center gap-2">{k.name}<span className={`text-[10px] px-2 py-0.5 rounded uppercase border font-mono ${k.tier === 'ENTERPRISE' ? 'bg-purple-500/10 text-purple-500 border-purple-500/20' : 'bg-slate-100 dark:bg-white/10 text-slate-500 border-slate-200 dark:border-white/10'}`}>{k.tier || 'USER'}</span></div>
                                        <div className="text-xs text-slate-500 font-mono mt-1 flex items-center gap-2"><span>Prefix: <span className="bg-slate-100 dark:bg-black/30 px-1 rounded text-slate-600 dark:text-slate-400">{k.prefix}••••••••</span></span></div>
                                        <div className="text-xs text-slate-400 mt-1">Created: {new Date(k.createdAt).toLocaleDateString()} • Last Used: {k.lastUsed ? new Date(k.lastUsed).toLocaleDateString() : 'Never'}</div>
                                    </div>
                                    <button onClick={() => confirmRevoke(k.id)} className="text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10 p-2 rounded-lg transition-colors" title="Revoke Key"><Trash2 className="w-5 h-5" /></button>
                                </div>
                            ))}
                        </div>
                        <div className="p-4 bg-slate-50 dark:bg-white/5 border-t border-slate-200 dark:border-white/10 mt-auto shrink-0">
                            <form onSubmit={handleCreate} className="flex gap-4">
                                <input type="text" value={keyName} onChange={e => setKeyName(e.target.value)} placeholder="Key Name (e.g. CI/CD Pipeline)" className="flex-1 bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-lg px-4 py-2 text-sm dark:text-white focus:ring-2 focus:ring-modtale-accent focus:outline-none transition-all" required />
                                <button disabled={isCreating} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-4 py-2 rounded-lg font-bold flex items-center justify-center gap-2 text-sm transition-all shadow-md active:scale-95 disabled:opacity-70 disabled:cursor-not-allowed"><Plus className="w-4 h-4" /> Generate</button>
                            </form>
                        </div>
                    </div>
                </div>

                <div className="space-y-6">
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl p-6 shadow-sm hover:border-modtale-accent/50 transition-colors group">
                        <div className="w-12 h-12 bg-blue-100 dark:bg-blue-900/20 rounded-xl flex items-center justify-center text-blue-600 dark:text-blue-400 mb-4 group-hover:scale-110 transition-transform">
                            <Info className="w-6 h-6" />
                        </div>
                        <h3 className="font-bold text-lg text-slate-900 dark:text-white mb-2">API Documentation</h3>
                        <p className="text-sm text-slate-600 dark:text-slate-400 mb-6">Full reference for endpoints, rate limits, and authentication.</p>
                        <Link to="/api-docs" className="text-modtale-accent font-bold text-sm flex items-center gap-2 hover:underline">View Documentation <ArrowRight className="w-4 h-4" /></Link>
                    </div>

                    <div className="bg-slate-900 dark:bg-white/[0.03] text-white rounded-xl p-6 shadow-lg border border-slate-700 dark:border-white/10 relative overflow-hidden group">
                        <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-20 transition-opacity"><Github className="w-20 h-20 transform rotate-12" /></div>
                        <div className="relative z-10">
                            <h3 className="font-bold text-lg mb-2 flex items-center gap-2 text-white"><Code className="w-5 h-5 text-modtale-accent" /> Examples</h3>
                            <p className="text-slate-400 text-sm mb-6 leading-relaxed">Ready-to-use scripts for GitHub Actions, Gradle, and Maven.</p>
                            <a href="https://github.com/Modtale/modtale-example" target="_blank" rel="noreferrer" className="inline-flex items-center justify-center w-full px-4 py-3 bg-white text-slate-900 font-bold rounded-lg hover:bg-slate-100 transition-colors gap-2 text-sm"><Github className="w-4 h-4" /> View on GitHub</a>
                        </div>
                    </div>
                </div>
            </div>

            <div className="border-t border-slate-200 dark:border-white/10 pt-8 mt-12 text-center">
                <p className="text-xs text-slate-400">
                    Need higher rate limits? <a href="mailto:support@modtale.net" className="text-modtale-accent hover:underline font-bold">Contact us</a> for Enterprise access.
                </p>
            </div>
        </div>
    );
};