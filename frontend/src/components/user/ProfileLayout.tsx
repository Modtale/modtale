import React, { useState, useEffect } from 'react';
import {
    ChevronLeft, Upload, Plus, Image as ImageIcon,
    Github, Twitter, Gitlab, Globe, Check, Copy, ExternalLink,
    UserPlus, UserCheck, Building2, Settings, Flag
} from 'lucide-react';
import { ImageCropperModal } from '../ui/ImageCropperModal';
import { Spinner } from '../ui/Spinner';
import { OptimizedImage } from '../ui/OptimizedImage';
import type { User } from '../../types';
import { BACKEND_URL } from '../../utils/api';

const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36">
        <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" />
    </svg>
);

const Badge = ({ type }: { type: string }) => {
    if (type === 'OG') return <span className="bg-orange-500 text-white text-[10px] font-bold px-1.5 py-0.5 md:px-2.5 md:py-1 rounded-md uppercase tracking-tighter cursor-help align-middle" title="Early Adopter">OG</span>;
    if (type === 'VERIFIED') return <span className="bg-blue-500 text-white text-[10px] font-bold px-1.5 py-0.5 md:px-2.5 md:py-1 rounded-md uppercase tracking-tight cursor-help align-middle" title="Verified Creator">Verified</span>;
    return null;
};

interface ProfileLayoutProps {
    user: User;
    stats?: {
        downloads: number;
        favorites: number;
        followers: number;
        projects: number;
    };
    isEditing?: boolean;
    isSelf?: boolean;
    isFollowing?: boolean;
    isLoggedIn?: boolean;
    onBack?: () => void;
    onToggleFollow?: () => void;
    onReport?: () => void;
    onBannerUpload?: (file: File) => Promise<void>;
    onAvatarUpload?: (file: File) => Promise<void>;
    headerInput?: React.ReactNode;
    bioInput?: React.ReactNode;
    actionInput?: React.ReactNode;
    children?: React.ReactNode;
}

export const ProfileLayout: React.FC<ProfileLayoutProps> = ({
                                                                user, stats, isEditing = false, isSelf = false, isFollowing = false, isLoggedIn = false,
                                                                onBack, onToggleFollow, onReport, onBannerUpload, onAvatarUpload, headerInput, bioInput, actionInput, children
                                                            }) => {
    const [bannerToCrop, setBannerToCrop] = useState<string | null>(null);
    const [avatarToCrop, setAvatarToCrop] = useState<string | null>(null);
    const [uploadingBanner, setUploadingBanner] = useState(false);
    const [uploadingAvatar, setUploadingAvatar] = useState(false);
    const [scrollY, setScrollY] = useState(0);

    const [copied, setCopied] = useState(false);
    const [popupCopied, setPopupCopied] = useState<string | null>(null);

    const isOrg = user.accountType === 'ORGANIZATION';
    const displayTitle = user.username;
    const linkedAccounts = (user.connectedAccounts || []).filter(a => a.visible);

    const containerClasses = isEditing
        ? "w-full px-6"
        : "max-w-[112rem] px-4 sm:px-12 md:px-16 lg:px-28 mx-auto";

    const glassCardMargin = isEditing ? "-mt-8 md:-mt-16" : "-mt-2 md:-mt-32";
    const avatarMargin = isEditing ? "md:-mt-12" : "md:-mt-24";

    useEffect(() => {
        if (isEditing) return;
        let ticking = false;
        const handleScroll = () => {
            if (!ticking) {
                window.requestAnimationFrame(() => {
                    setScrollY(Math.min(Math.max(0, window.scrollY), 1500));
                    ticking = false;
                });
                ticking = true;
            }
        };

        window.addEventListener('scroll', handleScroll, { passive: true });
        handleScroll();
        return () => window.removeEventListener('scroll', handleScroll);
    }, [isEditing]);

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>, type: 'banner' | 'avatar') => {
        if (!e.target.files || !e.target.files.length) return;
        const file = e.target.files[0];
        const url = URL.createObjectURL(file);
        if (type === 'banner') setBannerToCrop(url);
        else setAvatarToCrop(url);
        e.target.value = '';
    };

    const handleCropComplete = async (file: File, type: 'banner' | 'avatar') => {
        if (type === 'banner') {
            setBannerToCrop(null);
            if (onBannerUpload) {
                setUploadingBanner(true);
                await onBannerUpload(file).finally(() => setUploadingBanner(false));
            }
        } else {
            setAvatarToCrop(null);
            if (onAvatarUpload) {
                setUploadingAvatar(true);
                await onAvatarUpload(file).finally(() => setUploadingAvatar(false));
            }
        }
    };

    const copyId = () => {
        navigator.clipboard.writeText(user.id);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    const copyHandle = (text: string, provider: string) => {
        navigator.clipboard.writeText(text);
        setPopupCopied(provider);
        setTimeout(() => setPopupCopied(null), 2000);
    };

    const getProviderDetails = (provider: string) => {
        switch (provider) {
            case 'github': return { icon: Github, label: 'GitHub', activeClass: 'group-hover/social:text-slate-900 dark:group-hover/social:text-white group-hover/social:border-slate-300 dark:group-hover/social:border-white/20', iconBg: 'bg-slate-900/10 dark:bg-white/10 text-slate-900 dark:text-white', profileBtnBg: 'bg-[#24292e]' };
            case 'gitlab': return { icon: Gitlab, label: 'GitLab', activeClass: 'group-hover/social:text-[#FC6D26] group-hover/social:border-[#FC6D26]/30', iconBg: 'bg-[#FC6D26]/10 text-[#FC6D26]', profileBtnBg: 'bg-[#FC6D26]' };
            case 'twitter': return { icon: Twitter, label: 'Twitter', activeClass: 'group-hover/social:text-[#1DA1F2] group-hover/social:border-[#1DA1F2]/30', iconBg: 'bg-[#1DA1F2]/10 text-[#1DA1F2]', profileBtnBg: 'bg-[#1DA1F2]' };
            case 'discord': return { icon: DiscordIcon, label: 'Discord', activeClass: 'group-hover/social:text-[#5865F2] group-hover/social:border-[#5865F2]/30', iconBg: 'bg-[#5865F2]/10 text-[#5865F2]', profileBtnBg: 'bg-[#5865F2]' };
            default: return { icon: Globe, label: 'Website', activeClass: 'group-hover/social:text-blue-500 group-hover/social:border-blue-500/30', iconBg: 'bg-blue-500/10 text-blue-500', profileBtnBg: 'bg-blue-500' };
        }
    };

    const resolveImageUrl = (url?: string | null) => {
        if (!url || url === 'null') return null;
        if (url.startsWith('http') || url.startsWith('data:') || url.startsWith('blob:')) return url;
        return `${BACKEND_URL}${url.startsWith('/') ? '' : '/'}${url}`;
    };

    const resolvedAvatar = resolveImageUrl(user.avatarUrl);
    const resolvedBanner = resolveImageUrl(user.bannerUrl);

    const SocialButton = ({ account, compact = false, isRightMost = false }: { account: any, compact?: boolean, isRightMost?: boolean }) => {
        const { icon: Icon, label, activeClass, iconBg, profileBtnBg } = getProviderDetails(account.provider);
        const displayUrl = account.profileUrl || '#';
        const isDiscord = account.provider === 'discord';
        const finalUrl = isDiscord ? `https://discord.com/users/${account.providerId}` : displayUrl;

        const popupPositionClasses = isRightMost
            ? "right-0 left-auto translate-x-0"
            : "left-1/2 -translate-x-1/2";

        const trianglePositionClasses = isRightMost
            ? "right-3 left-auto translate-x-0"
            : "left-1/2 -translate-x-1/2";

        return (
            <div className="relative inline-block align-middle group/social">
                <a
                    href={finalUrl}
                    target="_blank"
                    rel="noreferrer"
                    className={`${compact ? 'p-1.5' : 'p-2.5 md:p-2.5 p-2'} rounded-lg md:rounded-xl bg-white/50 dark:bg-white/5 border border-slate-200 dark:border-white/5 transition-all relative flex-shrink-0 inline-flex items-center justify-center text-slate-400 backdrop-blur-md ${activeClass}`}
                >
                    <Icon className={compact ? "w-3.5 h-3.5" : "w-4 h-4 md:w-5 md:h-5"} />
                </a>

                <div className={`absolute bottom-full mb-3 w-64 bg-white/90 dark:bg-slate-800/90 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-xl shadow-2xl p-4 z-[110] text-left cursor-default opacity-0 invisible group-hover/social:visible group-hover/social:opacity-100 transition-all duration-200 ${popupPositionClasses}`}>
                    <div className="absolute top-full left-0 w-full h-4 bg-transparent"></div>

                    <div className="flex justify-between items-start mb-3">
                        <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">{label} Identity</h4>
                    </div>
                    <div className="flex items-center gap-3 mb-4">
                        <div className={`p-2 rounded-lg ${iconBg}`}><Icon className="w-6 h-6" /></div>
                        <div className="min-w-0">
                            <p className="font-black text-slate-900 dark:text-white text-sm truncate">{account.username}</p>
                            <p className="text-[10px] text-slate-500">Connected Account</p>
                        </div>
                    </div>
                    <div className="grid grid-cols-2 gap-2 relative z-10">
                        <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); copyHandle(account.username, account.provider); }} className="flex items-center justify-center gap-2 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 py-2 rounded-lg text-xs font-bold transition-colors text-slate-700 dark:text-slate-200">
                            {popupCopied === account.provider ? <Check className="w-3 h-3 text-green-500" /> : <Copy className="w-3 h-3" />} {popupCopied === account.provider ? 'Copied' : 'Copy'}
                        </button>
                        <a href={finalUrl} target="_blank" rel="noreferrer" className={`flex items-center justify-center gap-2 text-white py-2 rounded-lg text-xs font-bold transition-colors hover:opacity-90 ${profileBtnBg}`}>
                            <ExternalLink className="w-3 h-3" /> Profile
                        </a>
                    </div>
                    <div className={`absolute top-full -mt-[1px] border-[6px] border-transparent border-t-white/90 dark:border-t-slate-800/90 pointer-events-none ${trianglePositionClasses}`}></div>
                </div>
            </div>
        );
    };

    const Avatar = ({ className, forceSizeClass }: { className?: string, forceSizeClass?: string }) => (
        <div className={`relative group ${className}`}>
            <div className={`w-full h-full ${forceSizeClass || ''} rounded-3xl border-[6px] md:border-[8px] border-white dark:border-slate-800 shadow-xl overflow-hidden bg-slate-100 dark:bg-slate-800 relative z-20 backdrop-blur-md transition-colors`}>
                {resolvedAvatar ? (
                    <OptimizedImage
                        src={resolvedAvatar}
                        alt={`${user.username} Avatar`}
                        baseWidth={224}
                        priority={true}
                        className="w-full h-full bg-transparent object-cover"
                    />
                ) : (
                    <div className="w-full h-full bg-slate-200 dark:bg-slate-700 flex items-center justify-center">
                        <Building2 className="w-10 h-10 md:w-20 md:h-20 text-slate-400" />
                    </div>
                )}
                {isEditing && (
                    <label className="absolute inset-0 bg-white/60 dark:bg-black/60 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 backdrop-blur-sm cursor-pointer text-slate-900 dark:text-white z-30">
                        <input type="file" className="hidden" accept="image/*" onChange={(e) => handleFileSelect(e, 'avatar')} disabled={uploadingAvatar} />
                        {uploadingAvatar ? <Spinner className="w-6 h-6" /> : <Upload className="w-6 h-6 mb-1 drop-shadow-sm" />}
                        <span className="text-[9px] font-bold uppercase text-center px-2 mt-1 drop-shadow-sm">Change</span>
                        <span className="opacity-70 text-[8px] font-bold uppercase text-center px-2 mt-0.5 drop-shadow-sm">Rec: 512x512</span>
                    </label>
                )}
            </div>
        </div>
    );

    const parallaxOffset = isEditing ? 0 : 500 * (1 - Math.exp(-scrollY / 600));

    return (
        <div className={`relative z-0 overflow-x-hidden ${isEditing ? '' : 'mb-6 md:mb-16'}`}>
            {bannerToCrop && <ImageCropperModal imageSrc={bannerToCrop} onCancel={() => setBannerToCrop(null)} onCropComplete={(f) => handleCropComplete(f, 'banner')} aspect={3/1} />}
            {avatarToCrop && <ImageCropperModal imageSrc={avatarToCrop} onCancel={() => setAvatarToCrop(null)} onCropComplete={(f) => handleCropComplete(f, 'avatar')} aspect={1/1} />}

            <div
                className={`absolute top-0 left-0 right-0 w-full aspect-[3/1] bg-slate-800 z-0 will-change-transform overflow-hidden ${isEditing ? 'rounded-2xl md:rounded-3xl' : 'md:rounded-b-3xl'}`}
                style={{ transform: `translateY(${parallaxOffset}px)` }}
            >
                <div className="absolute inset-0 z-0">
                    {resolvedBanner ? (
                        <OptimizedImage
                            src={resolvedBanner}
                            alt={`${user.username} Banner`}
                            baseWidth={1920}
                            priority={true}
                            className="w-full h-full object-cover opacity-100"
                        />
                    ) : (
                        <div className="w-full h-full bg-gradient-to-br from-modtale-accent/20 via-slate-900 to-black" />
                    )}
                </div>

                <div
                    className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-slate-50 dark:from-slate-950 to-transparent z-10 pointer-events-none will-change-[height] [--fade-base:0.5rem] md:[--fade-base:8rem]"
                    style={{ height: `calc(var(--fade-base) + ${parallaxOffset}px)` }}
                />

                {isEditing && (
                    <label className={`cursor-pointer transition-all duration-300 pointer-events-auto ${
                        resolvedBanner
                            ? "absolute top-6 right-6 z-30 bg-white/80 dark:bg-black/60 hover:bg-white dark:hover:bg-black/80 text-slate-900 dark:text-white px-5 py-2.5 rounded-xl text-xs font-bold border border-slate-200 dark:border-white/20 backdrop-blur-md shadow-sm hover:scale-105"
                            : "absolute inset-x-6 top-6 bottom-0 z-30 flex flex-col items-center justify-center rounded-t-3xl border-2 border-b-0 border-dashed border-slate-400/50 dark:border-white/20 hover:border-slate-500/60 dark:hover:border-white/40 bg-slate-100/50 dark:bg-white/5 hover:bg-slate-200/50 dark:hover:bg-white/10 group/banner backdrop-blur-sm pb-4 md:pb-16"
                    }`}>
                        <input type="file" className="hidden" accept="image/*" onChange={(e) => handleFileSelect(e, 'banner')} disabled={uploadingBanner} />
                        {resolvedBanner ? (
                            <div className="flex flex-col items-end">
                                <div className="flex items-center gap-2 drop-shadow-sm">
                                    {uploadingBanner ? <Spinner className="w-4 h-4" /> : <ImageIcon className="w-4 h-4" />}
                                    {uploadingBanner ? 'Uploading...' : 'Change Banner'}
                                </div>
                                <span className="text-[10px] font-bold opacity-70 drop-shadow-sm mt-0.5">Rec: 1920x640</span>
                            </div>
                        ) : (
                            <>
                                <div className="w-16 h-16 rounded-full bg-white/80 dark:bg-black/40 flex items-center justify-center mb-4 group-hover/banner:scale-110 transition-transform shadow-sm">
                                    <Plus className="w-8 h-8 text-slate-500 dark:text-white/80 group-hover/banner:text-modtale-accent transition-colors" />
                                </div>
                                <span className="text-lg font-bold text-slate-700 dark:text-white/90 group-hover/banner:text-slate-900 dark:group-hover/banner:text-white drop-shadow-sm">Upload Profile Banner</span>
                                <span className="text-xs font-bold text-slate-500 dark:text-white/70 mt-1 group-hover/banner:text-slate-600 dark:group-hover/banner:text-white/90 drop-shadow-sm">Recommended: 1920x640 (3:1)</span>
                            </>
                        )}
                    </label>
                )}
            </div>

            <div className="w-full aspect-[3/1] pointer-events-none relative z-0" />

            {onBack && (
                <div className={`absolute top-0 left-0 right-0 z-40 w-full ${containerClasses} h-full pointer-events-none transition-[max-width,padding] duration-300`}>
                    <div className="pt-6 pointer-events-auto w-fit">
                        <button onClick={onBack} className="flex items-center justify-center w-10 h-10 md:w-auto md:h-auto text-white/90 font-bold transition-all bg-black/30 hover:bg-black/50 backdrop-blur-md border border-white/10 md:px-4 md:py-2 rounded-full md:rounded-xl shadow-lg group">
                            <ChevronLeft className="w-5 h-5 md:w-4 md:h-4 md:mr-1 group-hover:-translate-x-1 transition-transform" />
                            <span className="hidden md:inline">Back</span>
                        </button>
                    </div>
                </div>
            )}

            {/* GLASS CARD */}
            <div className={`w-full mx-auto ${containerClasses} relative z-50 transition-[max-width,padding] duration-300 ${glassCardMargin}`}>
                <div className={`bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl shadow-2xl p-6 md:px-10 md:pb-6 flex flex-col md:flex-row gap-4 md:gap-10 items-start transition-colors ${
                    isEditing ? 'md:pt-8' : 'md:pt-10'
                }`}>

                    <div className={`hidden md:block flex-shrink-0 self-start relative z-20 ${avatarMargin}`}>
                        <Avatar className="w-48 h-48" />
                    </div>

                    <div className="flex-1 w-full min-w-0 md:pt-0">

                        <div className={`md:hidden -mt-16 flex justify-start relative z-50 ${isEditing ? 'mb-4 ml-2' : 'mb-3'}`}>
                            <Avatar className="w-28 h-28" forceSizeClass="rounded-3xl" />
                        </div>

                        <div className="mb-4 relative z-10">
                            <div className="flex flex-col md:flex-row md:justify-between md:items-start gap-4">
                                <div className="w-full flex-1 min-w-0">
                                    <div className="flex flex-col md:flex-row md:items-center justify-start gap-1 md:gap-3">
                                        <div className="flex items-center gap-2 flex-wrap">
                                            {headerInput ? headerInput : (
                                                <h1 className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tighter leading-tight overflow-visible break-words pb-1 min-h-[1.2em]">{displayTitle}</h1>
                                            )}

                                            {!isEditing && (
                                                <div className="flex gap-1.5 flex-wrap items-center mt-1 md:mt-0">
                                                    {isOrg && <span className="bg-purple-500 text-white text-[10px] md:text-xs font-bold px-1.5 py-0.5 md:px-3 md:py-1 rounded-md shadow-md uppercase tracking-wide flex items-center gap-1"><Building2 className="w-3 h-3" /> <span className="hidden md:inline">Organization</span><span className="md:hidden">Org</span></span>}
                                                    {user.badges && user.badges.map(b => <Badge key={b} type={b} />)}

                                                    <div className="md:hidden flex items-center gap-1.5">
                                                        {linkedAccounts.map(acc => <SocialButton key={acc.provider} account={acc} compact={true} />)}
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                                <div className={`items-center gap-2 relative z-20 ${isEditing ? 'flex mt-2 md:mt-0' : 'hidden md:flex mt-1'}`}>
                                    {actionInput ? actionInput : (
                                        <>
                                            {isSelf ? (
                                                <a href="/dashboard/profile" className="px-6 py-3 rounded-xl font-black text-base flex items-center justify-center gap-2 transition-all shadow-lg active:scale-95 bg-white/50 dark:bg-white/5 text-slate-700 dark:text-slate-300 hover:bg-white/80 dark:hover:bg-white/10 border border-slate-200 dark:border-white/5 backdrop-blur-md">
                                                    <Settings className="w-5 h-5" /> Manage Profile
                                                </a>
                                            ) : (
                                                <button onClick={onToggleFollow} className={`px-8 py-3 rounded-xl font-black text-base flex items-center justify-center gap-2 transition-all shadow-lg active:scale-95 ${isFollowing ? 'bg-white/50 dark:bg-white/5 text-slate-600 dark:text-slate-300 hover:text-red-500 backdrop-blur-md' : 'bg-modtale-accent text-white hover:bg-modtale-accentHover'}`}>
                                                    {isLoggedIn ? (isFollowing ? <><UserCheck className="w-5 h-5" /> Following</> : <><UserPlus className="w-5 h-5" /> Follow</>) : "Sign in"}
                                                </button>
                                            )}
                                            <button onClick={copyId} className="p-3 rounded-xl border border-slate-200 dark:border-white/10 bg-white/50 dark:bg-white/5 text-slate-400 hover:text-modtale-accent transition-all backdrop-blur-md" title="Copy ID">
                                                {copied ? <Check className="w-5 h-5 text-green-500" /> : <Copy className="w-5 h-5" />}
                                            </button>
                                            {onReport && (
                                                <button onClick={onReport} className="p-3 rounded-xl border border-slate-200 dark:border-white/10 bg-white/50 dark:bg-white/5 text-slate-400 hover:text-red-600 hover:border-red-500/20 transition-all backdrop-blur-md" title="Report User">
                                                    <Flag className="w-5 h-5" />
                                                </button>
                                            )}
                                        </>
                                    )}
                                </div>
                            </div>
                        </div>

                        {!isEditing && stats && (
                            <div className="md:hidden grid grid-cols-3 gap-3 mb-5 relative z-10">
                                <div className="bg-white/50 dark:bg-white/5 rounded-2xl p-3 flex flex-col items-center justify-center text-center shadow-sm border border-slate-200 dark:border-white/5 backdrop-blur-md">
                                    <span className="text-slate-900 dark:text-white font-black text-lg leading-none mb-1">{stats.downloads < 1000 ? stats.downloads : (stats.downloads / 1000).toFixed(1) + 'k'}</span>
                                    <span className="text-[9px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest">Downloads</span>
                                </div>
                                <div className="bg-white/50 dark:bg-white/5 rounded-2xl p-3 flex flex-col items-center justify-center text-center shadow-sm border border-slate-200 dark:border-white/5 backdrop-blur-md">
                                    <span className="text-slate-900 dark:text-white font-black text-lg leading-none mb-1">{stats.favorites.toLocaleString()}</span>
                                    <span className="text-[9px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest">Likes</span>
                                </div>
                                <div className="bg-white/50 dark:bg-white/5 rounded-2xl p-3 flex flex-col items-center justify-center text-center shadow-sm border border-slate-200 dark:border-white/5 backdrop-blur-md">
                                    <span className="text-slate-900 dark:text-white font-black text-lg leading-none mb-1">{stats.projects}</span>
                                    <span className="text-[9px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest">Projects</span>
                                </div>
                            </div>
                        )}

                        {!isEditing && (
                            <div className="md:hidden flex items-center gap-3 w-full mb-6 h-12 relative z-20">
                                {isSelf ? (
                                    <a href="/dashboard/profile" className="flex-1 h-12 rounded-xl font-bold text-sm flex items-center justify-center gap-2 transition-all shadow-lg active:scale-95 bg-white/50 dark:bg-white/5 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-white/10 backdrop-blur-md">
                                        <Settings className="w-4 h-4" /> Manage Profile
                                    </a>
                                ) : (
                                    <button onClick={onToggleFollow} className={`flex-1 h-12 rounded-xl font-bold text-sm flex items-center justify-center gap-2 transition-all shadow-lg active:scale-95 ${isFollowing ? 'bg-white/50 dark:bg-white/5 text-slate-600 dark:text-slate-300 hover:text-red-500 backdrop-blur-md' : 'bg-modtale-accent text-white hover:bg-modtale-accentHover'}`}>
                                        {isLoggedIn ? (isFollowing ? <><UserCheck className="w-4 h-4" /> Following</> : <><UserPlus className="w-4 h-4" /> Follow</>) : "Sign in to Follow"}
                                    </button>
                                )}
                                <button onClick={copyId} className="h-12 w-12 flex items-center justify-center rounded-xl border border-slate-200 dark:border-white/10 bg-white/50 dark:bg-white/5 text-slate-400 hover:text-modtale-accent transition-all flex-shrink-0 backdrop-blur-md" title="Copy ID">
                                    {copied ? <Check className="w-5 h-5 text-green-500" /> : <Copy className="w-5 h-5" />}
                                </button>
                                {onReport && (
                                    <button onClick={onReport} className="h-12 w-12 flex items-center justify-center rounded-xl border border-slate-200 dark:border-white/10 bg-white/50 dark:bg-white/5 text-slate-400 hover:text-red-500 transition-all flex-shrink-0 backdrop-blur-md" title="Report User">
                                        <Flag className="w-5 h-5" />
                                    </button>
                                )}
                            </div>
                        )}

                        <div className="mb-4 md:mb-6 relative z-10">
                            {bioInput ? bioInput : (
                                user.bio && <p className="text-slate-600 dark:text-slate-300 leading-snug md:leading-relaxed text-sm md:text-lg text-left line-clamp-3 md:line-clamp-none">{user.bio}</p>
                            )}
                        </div>

                        {!isEditing && stats && (
                            <div className="hidden pt-4 md:pt-6 border-t border-slate-200 dark:border-white/5 md:flex flex-col md:flex-row items-start md:items-center justify-between gap-4 md:gap-6 relative z-10">
                                <div className="flex justify-between w-full md:w-auto md:flex md:gap-8 text-left">
                                    <div className="text-center md:text-left">
                                        <div className="text-sm md:text-2xl font-black text-slate-900 dark:text-white">{stats.downloads < 1000 ? stats.downloads : (stats.downloads / 1000).toFixed(1) + 'k'}</div>
                                        <div className="text-[9px] md:text-[10px] font-bold text-slate-400 uppercase tracking-widest">Downloads</div>
                                    </div>
                                    <div className="text-center md:text-left">
                                        <div className="text-sm md:text-2xl font-black text-slate-900 dark:text-white">{stats.favorites.toLocaleString()}</div>
                                        <div className="text-[9px] md:text-[10px] font-bold text-slate-400 uppercase tracking-widest">Favorites</div>
                                    </div>
                                    <div className="text-center md:text-left">
                                        <div className="text-sm md:text-2xl font-black text-slate-900 dark:text-white">{stats.followers >= 1000 ? (stats.followers / 1000).toFixed(1) + 'k' : stats.followers}</div>
                                        <div className="text-[9px] md:text-[10px] font-bold text-slate-400 uppercase tracking-widest">Followers</div>
                                    </div>
                                    <div className="text-center md:text-left">
                                        <div className="text-sm md:text-2xl font-black text-slate-900 dark:text-white">{stats.projects}</div>
                                        <div className="text-[9px] md:text-[10px] font-bold text-slate-400 uppercase tracking-widest">Projects</div>
                                    </div>
                                </div>
                                <div className="hidden md:flex gap-2 flex-wrap justify-end">
                                    {linkedAccounts.map((acc, index) => (
                                        <SocialButton
                                            key={acc.provider}
                                            account={acc}
                                            isRightMost={index === linkedAccounts.length - 1}
                                        />
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {children && (
                <div className={`${containerClasses} transition-[max-width,padding] duration-300 ${isEditing ? 'mt-8' : 'mt-0 md:mt-16'} relative z-10`}>
                    {children}
                </div>
            )}
        </div>
    );
};