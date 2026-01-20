import React, { useState, useEffect } from 'react';
import { api } from '../../../utils/api';
import type {Mod, User} from '../../../types';
import { Search } from 'lucide-react';

interface TransferProjectModalProps {
    project: Mod;
    myOrgs?: User[];
    onClose: () => void;
    onSuccess: (message: string) => void;
    onError: (message: string) => void;
}

export const TransferProjectModal: React.FC<TransferProjectModalProps> = ({ project, myOrgs = [], onClose, onSuccess, onError }) => {
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState<User[]>([]);
    const [isSearching, setIsSearching] = useState(false);
    const [selectedUsername, setSelectedUsername] = useState('');

    useEffect(() => {
        if (!searchQuery) {
            setSearchResults([]);
            return;
        }
        const delayDebounceFn = setTimeout(async () => {
            setIsSearching(true);
            try {
                const res = await api.get(`/creators/search?query=${searchQuery}`);
                setSearchResults(res.data);
            } catch (e) {
                console.error(e);
            } finally {
                setIsSearching(false);
            }
        }, 500);
        return () => clearTimeout(delayDebounceFn);
    }, [searchQuery]);

    const handleTransferRequest = async () => {
        if (!selectedUsername) return;
        try {
            await api.post(`/projects/${project.id}/transfer`, { username: selectedUsername });
            onSuccess(`Transfer request sent to ${selectedUsername}`);
            onClose();
        } catch (e: any) {
            onError(e.response?.data || "Failed to send request.");
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-in fade-in">
            <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 p-6 rounded-2xl w-full max-w-lg shadow-2xl flex flex-col max-h-[90dvh]">
                <h3 className="text-xl font-black text-slate-900 dark:text-white mb-2">Transfer Ownership</h3>
                <p className="text-sm text-slate-500 dark:text-slate-400 mb-6">
                    Select a target to transfer <strong>{project.title}</strong> to. They must accept the request.
                </p>

                <div className="flex-1 overflow-y-auto mb-4 space-y-6 pr-2">
                    {myOrgs.length > 0 && (
                        <div>
                            <h4 className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-3">Your Organizations</h4>
                            <div className="grid grid-cols-2 gap-2">
                                {myOrgs.map(org => (
                                    <button
                                        key={org.id}
                                        onClick={() => setSelectedUsername(org.username)}
                                        className={`flex items-center gap-2 p-3 rounded-xl border text-left transition-all ${selectedUsername === org.username ? 'border-modtale-accent bg-modtale-accent/10 text-modtale-accent' : 'border-slate-200 dark:border-white/10 hover:bg-slate-50 dark:hover:bg-white/5'}`}
                                    >
                                        <div className="w-8 h-8 rounded-lg bg-slate-200 dark:bg-white/10 flex items-center justify-center flex-shrink-0 overflow-hidden">
                                            {org.avatarUrl ? <img src={org.avatarUrl} className="w-full h-full object-cover" /> : <span className="text-xs">ORG</span>}
                                        </div>
                                        <span className="font-bold text-sm truncate dark:text-white">{org.displayName || org.username}</span>
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}

                    <div>
                        <h4 className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-3">Search User or Org</h4>
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                            <input
                                type="text"
                                placeholder="Type username..."
                                value={searchQuery}
                                onChange={e => setSearchQuery(e.target.value)}
                                className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl pl-10 pr-4 py-3 outline-none focus:ring-2 focus:ring-inset focus:ring-modtale-accent dark:text-white"
                                autoFocus
                            />
                        </div>
                        {searchQuery && (
                            <div className="mt-2 bg-slate-50 dark:bg-black/20 rounded-xl border border-slate-200 dark:border-white/5 overflow-hidden">
                                {isSearching ? <div className="p-4 text-center text-xs text-slate-400">Searching...</div> : (
                                    searchResults.length > 0 ? searchResults.map(res => (
                                        <button key={res.id} onClick={() => setSelectedUsername(res.username)} className={`w-full flex items-center gap-3 p-3 hover:bg-slate-100 dark:hover:bg-white/10 transition-colors ${selectedUsername === res.username ? 'bg-modtale-accent/10' : ''}`}>
                                            <div className="w-8 h-8 rounded-full bg-slate-200 overflow-hidden"><img src={res.avatarUrl} className="w-full h-full object-cover" /></div>
                                            <div className="text-left">
                                                <div className="font-bold text-sm dark:text-white">{res.username}</div>
                                                <div className="text-[10px] text-slate-500 uppercase font-bold">{res.accountType || 'USER'}</div>
                                            </div>
                                            {selectedUsername === res.username && <div className="ml-auto w-2 h-2 bg-modtale-accent rounded-full"/>}
                                        </button>
                                    )) : <div className="p-4 text-center text-xs text-slate-400">No results found.</div>
                                )}
                            </div>
                        )}
                    </div>
                </div>

                {selectedUsername && (
                    <div className="mb-4 p-3 bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-300 rounded-lg text-sm font-bold flex items-center justify-between">
                        <span>Transferring to: {selectedUsername}</span>
                    </div>
                )}

                <div className="flex justify-end gap-2 pt-4 border-t border-slate-200 dark:border-white/10 mt-auto">
                    <button onClick={onClose} className="px-4 py-2 font-bold text-slate-500">Cancel</button>
                    <button onClick={handleTransferRequest} disabled={!selectedUsername} className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-6 py-2 rounded-xl font-bold disabled:opacity-50 transition-opacity">Send Request</button>
                </div>
            </div>
        </div>
    );
};