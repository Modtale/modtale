import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import { Link as LinkIcon, Check, Eye, EyeOff, Trash2, Plus } from 'lucide-react';
import { userClient } from '../api/userClient';
import { BACKEND_URL } from '@/utils/api';
import { StatusModal } from '@/components/ui/StatusModal';
import { ErrorBanner } from '@/components/ui/error/ErrorBanner';
import { BlueskyBrandIcon, DiscordBrandIcon, GitHubBrandIcon, GitLabBrandIcon, GoogleMonoBrandIcon, XBrandIcon } from '@/components/ui/icons/BrandIcons';
import type { User } from '@/types';

interface ConnectionsSettingsProps {
    user: User;
    onUpdate: () => void;
}

export function ConnectionsSettings({ user, onUpdate }: ConnectionsSettingsProps) {
    const [showUnlinkModal, setShowUnlinkModal] = useState<{provider: string, label: string} | null>(null);
    const [error, setError] = useState<string | null>(null);

    const accounts = user.connectedAccounts || [];

    const handleUnlink = async () => {
        if (!showUnlinkModal) return;
        try {
            await userClient.unlinkConnection(showUnlinkModal.provider);
            onUpdate();
            setShowUnlinkModal(null);
            setError(null);
        } catch (e: any) {
            setError(e.response?.data || "Failed to unlink account.");
            setShowUnlinkModal(null);
        }
    };

    const handleToggleVisibility = async (provider: string) => {
        try {
            await userClient.toggleConnectionVisibility(provider);
            onUpdate();
            setError(null);
        } catch (e: any) {
            setError(e.response?.data || "Failed to toggle visibility.");
        }
    };

    const AccountRow = ({ provider, icon: Icon, label }: { provider: string, icon: any, label: string }) => {
        const account = accounts.find(a => a.provider?.toLowerCase() === provider.toLowerCase());
        const isLinked = !!account;
        const canBeVisible = provider !== 'google';
        const isLastAuthMethod = isLinked && accounts.length <= 1 && !(user as any).hasPassword;

        return (
            <div className={`flex flex-col justify-between p-4 rounded-2xl border transition-all h-full ${isLinked ? 'bg-white/80 dark:bg-slate-900/40 border-modtale-accent/40 shadow-md' : 'bg-slate-50/80 dark:bg-white/[0.02] border-slate-200 dark:border-white/5 hover:border-slate-300 dark:hover:border-white/20'}`}>
                <div className="flex items-center gap-3 mb-4">
                    <div className={`p-2.5 rounded-xl ${isLinked ? 'text-modtale-accent bg-modtale-accent/10' : 'text-slate-400 bg-white dark:bg-white/5 shadow-sm border border-slate-200 dark:border-white/5'}`}>
                        <Icon className="w-5 h-5" />
                    </div>
                    <div className="flex-1 min-w-0">
                        <h4 className="font-bold text-xs text-slate-900 dark:text-white uppercase tracking-wider">{label}</h4>
                        {isLinked ? (
                            <p className="text-xs text-green-600 dark:text-green-400 font-bold flex items-center gap-1.5 mt-0.5 truncate">
                                <Check className="w-3.5 h-3.5 shrink-0" /> {account.username}
                            </p>
                        ) : (
                            <p className="text-xs text-slate-400 mt-0.5">Not connected</p>
                        )}
                    </div>
                </div>

                <div className="flex items-center justify-end pt-3 border-t border-slate-100 dark:border-white/5 mt-auto">
                    {isLinked ? (
                        <div className="flex gap-2 w-full">
                            {canBeVisible ? (
                                <button
                                    onClick={() => handleToggleVisibility(provider)}
                                    className={`flex-1 flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-bold transition-colors ${account.visible ? 'text-modtale-accent bg-modtale-accent/10 hover:bg-modtale-accent/20' : 'text-slate-500 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10'}`}
                                    title={account.visible ? "Publicly Visible" : "Hidden from profile"}
                                >
                                    {account.visible ? <><Eye className="w-3.5 h-3.5" /> Visible</> : <><EyeOff className="w-3.5 h-3.5" /> Hidden</>}
                                </button>
                            ) : (
                                <div className="flex-1 flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-bold text-slate-400 bg-slate-50 dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 cursor-not-allowed" title="Private connection">
                                    <EyeOff className="w-3.5 h-3.5" /> Private
                                </div>
                            )}
                            <button
                                onClick={() => setShowUnlinkModal({ provider, label })}
                                disabled={isLastAuthMethod}
                                className={`px-3 py-2 rounded-lg transition-colors ${isLastAuthMethod ? 'text-slate-300 dark:text-slate-600 bg-slate-50 dark:bg-white/[0.02] cursor-not-allowed' : 'text-red-500 hover:text-red-600 bg-red-50 hover:bg-red-100 dark:bg-red-950/30 dark:hover:bg-red-900/50'}`}
                                title={isLastAuthMethod ? "Cannot remove your only sign-in method. Set a password first." : "Unlink Account"}
                            >
                                <Trash2 className="w-4 h-4" />
                            </button>
                        </div>
                    ) : (
                        <button
                            onClick={() => window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`}
                            className="w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-200 py-2 rounded-lg text-xs font-bold hover:border-modtale-accent hover:text-modtale-accent transition-all flex items-center justify-center gap-2 shadow-sm"
                        >
                            <Plus className="w-4 h-4" /> Link Account
                        </button>
                    )}
                </div>
            </div>
        );
    };

    return (
        <>
            {error && <ErrorBanner message={error} className="mb-6" />}
            {showUnlinkModal && createPortal(
                <StatusModal type="warning" title={`Unlink ${showUnlinkModal.label}?`} message={`Are you sure you want to unlink your ${showUnlinkModal.label} account? You will no longer be able to sign in with it.`} actionLabel="Yes, Unlink" onAction={handleUnlink} onClose={() => setShowUnlinkModal(null)} secondaryLabel="Cancel" />,
                document.body
            )}

            <div className="bg-white/60 dark:bg-slate-900/40 border border-slate-200 dark:border-white/10 rounded-[2rem] p-6 md:p-8 shadow-sm backdrop-blur-xl mt-8">
                <div className="flex items-center gap-4 mb-8 border-b border-slate-200 dark:border-white/10 pb-6">
                    <div className="p-3 bg-slate-100 dark:bg-white/5 rounded-2xl text-slate-500"><LinkIcon className="w-5 h-5 text-modtale-accent" /></div>
                    <div>
                        <h3 className="text-xl font-black text-slate-900 dark:text-white tracking-tight">Connected Accounts</h3>
                        <p className="text-xs text-slate-500 font-medium mt-1">Link accounts to sign in easily and display them on your profile.</p>
                    </div>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                    <AccountRow provider="github" icon={GitHubBrandIcon} label="GitHub" />
                    <AccountRow provider="gitlab" icon={GitLabBrandIcon} label="GitLab" />
                    <AccountRow provider="discord" icon={DiscordBrandIcon} label="Discord" />
                    <AccountRow provider="twitter" icon={XBrandIcon} label="X / Twitter" />
                    <AccountRow provider="bluesky" icon={BlueskyBrandIcon} label="Bluesky" />
                    <AccountRow provider="google" icon={GoogleMonoBrandIcon} label="Google" />
                </div>
            </div>
        </>
    );
}
