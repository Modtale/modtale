import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import { Link as LinkIcon, Check, Eye, EyeOff, Trash2, Plus } from 'lucide-react';
import { userClient } from '../api/userClient';
import { BACKEND_URL } from '@/utils/api';
import { StatusModal } from '@/components/ui/StatusModal';
import { ErrorBanner } from '@/components/ui/error/ErrorBanner';
import type { User } from '@/types';

const DiscordIcon = ({ className }: { className?: string }) => (<svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36"><path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0A105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36A77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19a77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" /></svg>);
const GitLabIcon = ({ className }: { className?: string }) => (<svg className={className} viewBox="0 0 24 24" fill="currentColor"><path d="M22.65 14.39L12 22.13L1.35 14.39L4.74 3.99C4.82 3.73 5.12 3.63 5.33 3.82L8.99 7.5L12 10.5L15.01 7.5L18.67 3.82C18.88 3.63 19.18 3.73 19.26 3.99L22.65 14.39Z" /></svg>);
const BlueskyIcon = ({ className }: { className?: string }) => (<svg className={className} viewBox="0 -3.268 64 68.414" fill="currentColor"><path d="M13.873 3.805C21.21 9.332 29.103 20.537 32 26.55v15.882c0-.338-.13.044-.41.867-1.512 4.456-7.418 21.847-20.923 7.944-7.111-7.32-3.819-14.64 9.125-16.85-7.405 1.264-15.73-.825-18.014-9.015C1.12 23.022 0 8.51 0 6.55 0-3.268 8.579-.182 13.873 3.805zm36.254 0C42.79 9.332 34.897 20.537 32 26.55v15.882c0-.338.13.044.41.867 1.512 4.456 7.418 21.847 20.923 7.944 7.111-7.32 3.819-14.64-9.125-16.85 7.405 1.264 15.73-.825 18.014-9.015C62.88 23.022 64 8.51 64 6.55c0-9.818-8.578-6.732-13.873-2.745z"/></svg>);
const GithubIcon = ({ className }: { className?: string }) => (<svg className={className} viewBox="0 0 24 24" fill="currentColor"><path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/></svg>);
const TwitterIcon = ({ className }: { className?: string }) => (<svg className={className} viewBox="0 0 24 24" fill="currentColor"><path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.005 4.15H5.059z"/></svg>);
const GoogleIcon = ({ className }: { className?: string }) => (<svg className={className} viewBox="0 0 24 24" fill="currentColor"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.04-3.71 1.04-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>);

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
        const account = accounts.find(a => a.provider === provider);
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
                    <AccountRow provider="github" icon={GithubIcon} label="GitHub" />
                    <AccountRow provider="gitlab" icon={GitLabIcon} label="GitLab" />
                    <AccountRow provider="discord" icon={DiscordIcon} label="Discord" />
                    <AccountRow provider="twitter" icon={TwitterIcon} label="X / Twitter" />
                    <AccountRow provider="bluesky" icon={BlueskyIcon} label="Bluesky" />
                    <AccountRow provider="google" icon={GoogleIcon} label="Google" />
                </div>
            </div>
        </>
    );
}