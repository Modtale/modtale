import React, { useState, useRef } from 'react';
import { createPortal } from 'react-dom';
import {
    Settings as SettingsIcon,
    Upload,
    Image as ImageIcon,
    Link as LinkIcon,
    Plus,
    Trash2,
    Eye,
    EyeOff,
    Check
} from 'lucide-react';
import { theme } from '@/styles/theme';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal';
import { StatusModal } from '@/components/ui/StatusModal';
import { organizationClient, hasOrgPermission } from '../api/organizationClient';
import { BACKEND_URL } from '@/utils/api';
import type { User } from '@/types';

const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
const MAX_UPLOAD_ERROR_MESSAGE = 'File exceeds 100MB limit. Cloudflare only supports uploads up to 100MB.';
const isFileOverUploadLimit = (file: File) => file.size > MAX_UPLOAD_BYTES;

const GitLabIcon = ({ className }: { className?: string }) => (<svg className={className} viewBox="0 0 24 24" fill="currentColor"><path d="M22.65 14.39L12 22.13L1.35 14.39L4.74 3.99C4.82 3.73 5.12 3.63 5.33 3.82L8.99 7.5L12 10.5L15.01 7.5L18.67 3.82C18.88 3.63 19.18 3.73 19.26 3.99L22.65 14.39Z" /></svg>);
const BlueskyIcon = ({ className }: { className?: string }) => (<svg className={className} viewBox="0 0 568 501" fill="currentColor"><path d="M123.121 33.664C188.241 83.564 263.357 167.332 284 200.793C304.643 167.332 379.759 83.564 444.879 33.664C497.868 -6.932 568 -22.108 568 46.54V218.456C568 243.66 550.05 266.304 525.669 271.936L429.574 294.116C408.665 298.944 397.697 323.76 411.39 340.948C447.869 386.724 513.799 432.892 531.867 447.668C564.128 474.056 544.721 526 502.981 526H463.317C433.09 526 404.931 513.292 386.324 490.308C363.393 461.98 322.99 401.7 284 345.244C245.01 401.7 204.607 461.98 181.676 490.308C163.069 513.292 134.91 526 104.683 526H65.019C23.279 526 3.872 474.056 36.133 447.668C54.201 432.892 120.131 386.724 156.61 340.948C170.303 323.76 159.335 298.944 138.426 294.116L42.331 271.936C17.95 266.304 0 243.66 0 218.456V46.54C0 -22.108 70.132 -6.932 123.121 33.664Z" transform="scale(0.85) translate(10, -20)"/></svg>);
const GithubIcon = ({ className }: { className?: string }) => (<svg className={className} viewBox="0 0 24 24" fill="currentColor"><path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/></svg>);
const TwitterIcon = ({ className }: { className?: string }) => (<svg className={className} viewBox="0 0 24 24" fill="currentColor"><path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.005 4.15H5.059z"/></svg>);

interface SettingsProps {
    org: User;
    currentUser: User;
    onUpdateOrg: (org: User) => void;
    onDeleteOrg: () => void;
    showStatus: (type: 'success' | 'error' | 'warning', title: string, msg: string) => void;
}

export const Settings: React.FC<SettingsProps> = ({ org, currentUser, onUpdateOrg, onDeleteOrg, showStatus }) => {
    const [displayName, setDisplayName] = useState(org.username);
    const [bio, setBio] = useState(org.bio || '');
    const [saving, setSaving] = useState(false);

    const [cropperOpen, setCropperOpen] = useState(false);
    const [tempImage, setTempImage] = useState<string | null>(null);
    const [cropType, setCropType] = useState<'avatar' | 'banner'>('avatar');

    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [showUnlinkModal, setShowUnlinkModal] = useState<{provider: string, label: string} | null>(null);

    const avatarInputRef = useRef<HTMLInputElement>(null);
    const bannerInputRef = useRef<HTMLInputElement>(null);

    const canEditProfile = hasOrgPermission(org, currentUser.id, 'ORG_EDIT_METADATA');
    const canManageConnections = hasOrgPermission(org, currentUser.id, 'ORG_CONNECTION_MANAGE');
    const canDelete = hasOrgPermission(org, currentUser.id, 'ORG_DELETE');

    if (!canEditProfile) {
        return <div className={`text-center py-10 ${theme.colors.textMuted}`}>You do not have permission to edit this organization's settings.</div>;
    }

    const handleUpdateProfile = async (e: React.FormEvent) => {
        e.preventDefault();
        setSaving(true);
        try {
            const updatedOrg = await organizationClient.updateProfile(org.id, { displayName, bio });
            onUpdateOrg(updatedOrg);
            showStatus('success', 'Profile Updated', 'Organization settings saved successfully.');
        } catch (err: any) {
            showStatus('error', 'Update Failed', err.response?.data || "Failed to update profile.");
        } finally {
            setSaving(false);
        }
    };

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>, type: 'avatar' | 'banner') => {
        if (e.target.files && e.target.files[0]) {
            const file = e.target.files[0];
            if (isFileOverUploadLimit(file)) {
                showStatus('error', 'Upload Failed', MAX_UPLOAD_ERROR_MESSAGE);
                e.target.value = '';
                return;
            }
            setTempImage(URL.createObjectURL(file));
            setCropType(type);
            setCropperOpen(true);
            e.target.value = '';
        }
    };

    const handleCropComplete = async (croppedFile: File) => {
        try {
            const url = await organizationClient.uploadImage(org.id, cropType, croppedFile);
            onUpdateOrg({ ...org, [cropType === 'avatar' ? 'avatarUrl' : 'bannerUrl']: url });
        } catch (err: any) {
            showStatus('error', 'Upload Failed', err.response?.data || `Failed to upload ${cropType}.`);
        } finally {
            setCropperOpen(false);
            setTempImage(null);
        }
    };

    const handleDeleteOrg = async () => {
        try {
            await organizationClient.deleteOrg(org.id);
            onDeleteOrg();
            setDeleteModalOpen(false);
        } catch (err: any) {
            showStatus('error', 'Delete Failed', err.response?.data || "Failed to delete organization.");
        }
    };

    const handleOrgUnlink = async () => {
        if (!showUnlinkModal) return;
        try {
            await organizationClient.unlinkAccount(org.id, showUnlinkModal.provider);
            const updatedAccounts = org.connectedAccounts?.filter(a => a.provider !== showUnlinkModal.provider);
            onUpdateOrg({ ...org, connectedAccounts: updatedAccounts });
            setShowUnlinkModal(null);
        } catch (err: any) {
            showStatus('error', 'Unlink Failed', err.response?.data || "Failed to unlink account.");
            setShowUnlinkModal(null);
        }
    };

    const handleToggleVisibility = async (provider: string) => {
        try {
            await organizationClient.toggleVisibility(org.id, provider);
            const updatedAccounts = org.connectedAccounts?.map(a =>
                a.provider === provider ? { ...a, visible: !a.visible } : a
            );
            onUpdateOrg({ ...org, connectedAccounts: updatedAccounts });
        } catch (err: any) {
            showStatus('error', 'Update Failed', err.response?.data || "Failed to toggle visibility.");
        }
    };

    const AccountRow = ({ provider, icon: Icon, label }: { provider: string, icon: any, label: string }) => {
        const account = org.connectedAccounts?.find(a => a.provider === provider);
        const isLinked = !!account;

        return (
            <div className={`flex items-center justify-between p-3 rounded-xl border transition-all h-full ${isLinked ? `${theme.colors.bgSurfaceHover} border-modtale-accent/30 shadow-sm` : `${theme.colors.bgSurface} ${theme.colors.border}`}`}>
                <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg ${isLinked ? 'text-modtale-accent bg-modtale-accent/10' : `text-slate-400 ${theme.colors.bgSurfaceAlt}`}`}>
                        <Icon className="w-4 h-4" />
                    </div>
                    <div>
                        <h4 className={`font-bold text-[10px] ${theme.colors.textPrimary} uppercase tracking-wider`}>{label}</h4>
                        {isLinked ? (
                            <p className="text-[10px] text-green-600 dark:text-green-400 font-bold flex items-center gap-1 mt-0.5">
                                <Check className="w-3 h-3" /> {account.username}
                            </p>
                        ) : (
                            <p className={`text-[10px] ${theme.colors.textMuted} mt-0.5`}>Not connected</p>
                        )}
                    </div>
                </div>
                {isLinked ? (
                    <div className="flex gap-1.5">
                        <button onClick={() => canManageConnections && handleToggleVisibility(provider)} disabled={!canManageConnections} className={`p-1.5 rounded-lg transition-colors ${!canManageConnections ? 'opacity-50 cursor-not-allowed' : account.visible ? 'text-modtale-accent bg-modtale-accent/10 hover:bg-modtale-accent/20' : `text-slate-400 ${theme.colors.bgSurfaceHover}`}`}>
                            {account.visible ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
                        </button>
                        <button onClick={() => setShowUnlinkModal({ provider, label })} disabled={!canManageConnections} className={`p-1.5 rounded-lg transition-colors ${canManageConnections ? 'text-red-500 hover:bg-red-50 dark:hover:bg-red-950/30' : 'text-slate-300 opacity-50 cursor-not-allowed'}`}>
                            <Trash2 className="w-3.5 h-3.5" />
                        </button>
                    </div>
                ) : (
                    canManageConnections && (
                        <button onClick={() => window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`} className={`${theme.colors.bgSurfaceAlt} border ${theme.colors.border} ${theme.colors.textPrimary} px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-widest hover:border-modtale-accent hover:text-modtale-accent transition-all flex items-center gap-1.5 shadow-sm`}>
                            <Plus className="w-3 h-3" /> Link
                        </button>
                    )
                )}
            </div>
        );
    };

    return (
        <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2">
            {cropperOpen && tempImage && createPortal(
                <ImageCropperModal imageSrc={tempImage} aspect={cropType === 'banner' ? 3 : 1} onCancel={() => { setCropperOpen(false); setTempImage(null); }} onCropComplete={handleCropComplete} />,
                document.body
            )}

            {deleteModalOpen && createPortal(
                <StatusModal type="error" title="Delete Organization" message={`Are you sure you want to delete "${org.username}"? This cannot be undone.`} actionLabel="Delete Forever" onAction={handleDeleteOrg} secondaryLabel="Cancel" onClose={() => setDeleteModalOpen(false)} />,
                document.body
            )}

            {showUnlinkModal && createPortal(
                <StatusModal type="warning" title={`Unlink ${showUnlinkModal.label}?`} message={`Are you sure you want to unlink the ${showUnlinkModal.label} account from this organization?`} actionLabel="Yes, Unlink" onAction={handleOrgUnlink} secondaryLabel="Cancel" onClose={() => setShowUnlinkModal(null)} />,
                document.body
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className={`${theme.colors.bgSurface} border ${theme.colors.border} p-6 rounded-2xl shadow-sm`}>
                    <h3 className={`font-bold mb-4 ${theme.colors.textPrimary}`}>Organization Icon</h3>
                    <div className="flex items-center gap-4">
                        <img src={org.avatarUrl} alt="" className={`w-16 h-16 rounded-2xl object-cover ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} shadow-sm`} />
                        <div>
                            <input type="file" ref={avatarInputRef} onChange={e => handleFileSelect(e, 'avatar')} className="hidden" accept="image/*" />
                            <button onClick={() => avatarInputRef.current?.click()} className={theme.components.buttonSecondary}><Upload className="w-4 h-4" /> Upload</button>
                        </div>
                    </div>
                </div>
                <div className={`${theme.colors.bgSurface} border ${theme.colors.border} p-6 rounded-2xl shadow-sm`}>
                    <h3 className={`font-bold mb-4 ${theme.colors.textPrimary}`}>Profile Banner</h3>
                    <div className="flex items-center gap-4">
                        <div className={`w-32 h-16 rounded-xl overflow-hidden ${theme.colors.bgSurfaceAlt} relative border ${theme.colors.border} shadow-sm`}>
                            {org.bannerUrl && <img src={org.bannerUrl} alt="" className="w-full h-full object-cover" />}
                        </div>
                        <div>
                            <input type="file" ref={bannerInputRef} onChange={e => handleFileSelect(e, 'banner')} className="hidden" accept="image/*" />
                            <button onClick={() => bannerInputRef.current?.click()} className={theme.components.buttonSecondary}><ImageIcon className="w-4 h-4" /> Upload</button>
                        </div>
                    </div>
                </div>
            </div>

            <div className={`${theme.colors.bgSurface} border ${theme.colors.border} p-6 rounded-2xl shadow-sm`}>
                <h3 className={`font-bold text-lg mb-4 flex items-center gap-2 ${theme.colors.textPrimary}`}><SettingsIcon className={`w-5 h-5 ${theme.colors.textMuted}`} /> General Settings</h3>
                <form onSubmit={handleUpdateProfile} className="space-y-4">
                    <div>
                        <label className={`block text-xs font-bold uppercase ${theme.colors.textMuted} mb-1`}>Organization Name (Username)</label>
                        <input type="text" value={displayName} onChange={e => setDisplayName(e.target.value)} className={theme.components.inputField} />
                        <p className={`text-[10px] ${theme.colors.textMuted} mt-1`}>This is your unique organization identifier.</p>
                    </div>
                    <div>
                        <label className={`block text-xs font-bold uppercase ${theme.colors.textMuted} mb-1`}>Bio</label>
                        <textarea value={bio} onChange={e => setBio(e.target.value)} rows={3} className={theme.components.inputField} />
                    </div>
                    <div className="flex justify-end">
                        <button type="submit" disabled={saving} className={theme.components.buttonPrimary}>{saving ? 'Saving...' : 'Save Changes'}</button>
                    </div>
                </form>
            </div>

            <div className={`${theme.colors.bgSurface} border ${theme.colors.border} p-6 rounded-2xl shadow-sm`}>
                <div className="flex items-center gap-3 mb-4">
                    <LinkIcon className="w-5 h-5 text-modtale-accent" />
                    <div>
                        <h3 className={`font-bold text-lg ${theme.colors.textPrimary}`}>Connected Accounts</h3>
                        <p className={`text-xs ${theme.colors.textMuted} mt-1`}>Link organization accounts for imports and credibility.</p>
                    </div>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                    <AccountRow provider="github" icon={GithubIcon} label="GitHub" />
                    <AccountRow provider="gitlab" icon={GitLabIcon} label="GitLab" />
                    <AccountRow provider="twitter" icon={TwitterIcon} label="X / Twitter" />
                    <AccountRow provider="bluesky" icon={BlueskyIcon} label="Bluesky" />
                </div>
            </div>

            {canDelete && (
                <div className={`border ${theme.colors.dangerBorder} ${theme.colors.dangerBg} p-6 rounded-2xl shadow-sm`}>
                    <h3 className={`font-bold ${theme.colors.dangerText} mb-2`}>Danger Zone</h3>
                    <p className="text-sm text-red-500/80 mb-4">Deleting this organization will permanently remove it and unlist all associated projects.</p>
                    <button onClick={() => setDeleteModalOpen(true)} className="bg-white dark:bg-red-900/20 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-900/30 px-5 py-2.5 rounded-xl font-bold text-sm hover:bg-red-50 dark:hover:bg-red-900/30 transition-colors shadow-sm">Delete Organization</button>
                </div>
            )}
        </div>
    );
};
