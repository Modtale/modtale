import React, { useState, useRef, useEffect } from 'react';
import { ChevronLeft, UserPlus, UserCheck, Github, Twitter, Copy, Check, Globe, Gitlab, X, ExternalLink, Building2 } from 'lucide-react';
import type { User as UserType } from '../../types';

const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36">
        <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" />
    </svg>
);

const Badge = ({ type }: { type: string }) => {
    if (type === 'OG') return <span className="bg-gradient-to-r from-amber-400 to-orange-500 text-white text-[10px] font-black px-2.5 py-1 rounded shadow-md border border-orange-400/50 uppercase tracking-tighter cursor-help" title="Early Adopter">OG</span>;
    if (type === 'VERIFIED') return <span className="bg-blue-500 text-white text-[10px] font-bold px-2.5 py-1 rounded shadow-md uppercase tracking-tight cursor-help" title="Verified Creator">Verified</span>;
    return null;
};

interface CreatorHeaderProps {
    creator: UserType;
    totalItems: number;
    totalDownloads: number;
    totalFavorites: number;
    followerCount: number;
    username: string;
    onBack: () => void;
    isFollowing?: boolean;
    onToggleFollow?: () => void;
    isSelf?: boolean;
    isLoggedIn?: boolean;
}

export const CreatorHeader: React.FC<CreatorHeaderProps> = ({
                                                                creator, totalItems, totalDownloads, totalFavorites, followerCount, username, onBack, isFollowing, onToggleFollow, isSelf, isLoggedIn
                                                            }) => {
    const [copied, setCopied] = useState(false);
    const [popupCopied, setPopupCopied] = useState<string | null>(null);
    const [activePopup, setActivePopup] = useState<string | null>(null);

    const popupRef = useRef<HTMLDivElement>(null);
    const btnContainerRef = useRef<HTMLDivElement>(null);

    const isOrg = creator.accountType === 'ORGANIZATION';
    const displayTitle = creator.username;

    const copyId = () => {
        navigator.clipboard.writeText(creator.id);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    const copyHandle = (text: string, provider: string) => {
        navigator.clipboard.writeText(text);
        setPopupCopied(provider);
        setTimeout(() => setPopupCopied(null), 2000);
    };

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (
                popupRef.current && !popupRef.current.contains(event.target as Node) &&
                btnContainerRef.current && !btnContainerRef.current.contains(event.target as Node)
            ) {
                setActivePopup(null);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const linkedAccounts = (creator.connectedAccounts || []).filter(a => a.visible);

    const getProviderDetails = (provider: string) => {
        switch (provider) {
            case 'github': return {
                icon: Github,
                label: 'GitHub',
                activeClass: 'text-slate-900 dark:text-white border-slate-900 dark:border-white',
                btnHover: 'hover:text-slate-900 dark:hover:text-white hover:border-slate-900 dark:hover:border-white',
                iconBg: 'bg-slate-900/10 dark:bg-white/10 text-slate-900 dark:text-white',
                profileBtnBg: 'bg-[#24292e]'
            };
            case 'gitlab': return {
                icon: Gitlab,
                label: 'GitLab',
                activeClass: 'text-[#FC6D26] dark:text-[#FC6D26] border-[#FC6D26] dark:border-[#FC6D26]',
                btnHover: 'hover:text-[#FC6D26] dark:hover:text-[#FC6D26] hover:border-[#FC6D26] dark:hover:border-[#FC6D26]',
                iconBg: 'bg-[#FC6D26]/10 text-[#FC6D26]',
                profileBtnBg: 'bg-[#FC6D26]'
            };
            case 'twitter': return {
                icon: Twitter,
                label: 'Twitter',
                activeClass: 'text-[#1DA1F2] dark:text-[#1DA1F2] border-[#1DA1F2] dark:border-[#1DA1F2]',
                btnHover: 'hover:text-[#1DA1F2] dark:hover:text-[#1DA1F2] hover:border-[#1DA1F2] dark:hover:border-[#1DA1F2]',
                iconBg: 'bg-[#1DA1F2]/10 text-[#1DA1F2]',
                profileBtnBg: 'bg-[#1DA1F2]'
            };
            case 'discord': return {
                icon: DiscordIcon,
                label: 'Discord',
                activeClass: 'text-[#5865F2] dark:text-[#5865F2] border-[#5865F2] dark:border-[#5865F2]',
                btnHover: 'hover:text-[#5865F2] dark:hover:text-[#5865F2] hover:border-[#5865F2] dark:hover:border-[#5865F2]',
                iconBg: 'bg-[#5865F2]/10 text-[#5865F2]',
                profileBtnBg: 'bg-[#5865F2]'
            };
            default: return {
                icon: Globe,
                label: 'Website',
                activeClass: 'text-blue-500 dark:text-blue-500 border-blue-500 dark:border-blue-500',
                btnHover: 'hover:text-blue-500 dark:hover:text-blue-500 hover:border-blue-500 dark:hover:border-blue-500',
                iconBg: 'bg-blue-500/10 text-blue-500',
                profileBtnBg: 'bg-blue-500'
            };
        }
    };

    const SocialButton = ({ account, mobile = false }: { account: any, mobile?: boolean }) => {
        const { icon: Icon, label, activeClass, btnHover, iconBg, profileBtnBg } = getProviderDetails(account.provider);
        const isOpen = activePopup === account.provider;

        const displayUrl = account.profileUrl || '#';
        const isDiscord = account.provider === 'discord';
        const finalUrl = isDiscord ? `https://discord.com/users/${account.providerId}` : displayUrl;

        const baseClass = mobile
            ? "p-2 rounded-full bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-white/10 transition-colors relative"
            : "p-2.5 rounded-xl bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/5 transition-all relative";

        const currentClass = isOpen ? `${baseClass} ${activeClass}` : `${baseClass} ${mobile ? 'text-slate-500' : 'text-slate-400'} ${btnHover}`;

        const popupClasses = mobile
            ? "absolute bottom-full right-0 mb-2 w-64 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl p-4 z-50 animate-in zoom-in-95 duration-200 text-left cursor-default origin-bottom-right"
            : "absolute bottom-full left-1/2 -translate-x-1/2 mb-3 w-64 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl p-4 z-50 animate-in zoom-in-95 duration-200 text-left cursor-default";

        const arrowClasses = mobile
            ? "absolute top-full right-3.5 -mt-[1px] border-[6px] border-transparent border-t-white dark:border-t-slate-800 pointer-events-none"
            : "absolute top-full left-1/2 -translate-x-1/2 -mt-[1px] border-[6px] border-transparent border-t-white dark:border-t-slate-800 pointer-events-none";

        return (
            <div className="relative inline-block">
                <button
                    onClick={(e) => { e.stopPropagation(); setActivePopup(isOpen ? null : account.provider); }}
                    className={currentClass}
                >
                    <Icon className={mobile ? "w-4 h-4" : "w-5 h-5"} />
                </button>

                {isOpen && (
                    <div
                        ref={popupRef}
                        className={popupClasses}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="flex justify-between items-start mb-3">
                            <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">{label} Identity</h4>
                            <button onClick={() => setActivePopup(null)}><X className="w-3 h-3 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200" /></button>
                        </div>
                        <div className="flex items-center gap-3 mb-4">
                            <div className={`p-2 rounded-lg ${iconBg}`}>
                                <Icon className="w-6 h-6" />
                            </div>
                            <div className="min-w-0">
                                <p className="font-black text-slate-900 dark:text-white text-sm truncate">{account.username}</p>
                                <p className="text-[10px] text-slate-500">Connected Account</p>
                            </div>
                        </div>
                        <div className="grid grid-cols-2 gap-2">
                            <button
                                onClick={() => copyHandle(account.username, account.provider)}
                                className="flex items-center justify-center gap-2 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 py-2 rounded-lg text-xs font-bold transition-colors text-slate-700 dark:text-slate-200"
                            >
                                {popupCopied === account.provider ? <Check className="w-3 h-3 text-green-500" /> : <Copy className="w-3 h-3" />}
                                {popupCopied === account.provider ? 'Copied' : 'Copy'}
                            </button>
                            <a
                                href={finalUrl}
                                target="_blank"
                                rel="noreferrer"
                                className={`flex items-center justify-center gap-2 text-white py-2 rounded-lg text-xs font-bold transition-colors hover:opacity-90 ${profileBtnBg}`}
                            >
                                <ExternalLink className="w-3 h-3" />
                                Profile
                            </a>
                        </div>
                        <div className={arrowClasses}></div>
                    </div>
                )}
            </div>
        );
    };

    const MobileView = () => (
        <div className="md:hidden flex flex-col bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-white/5 mb-6">
            <div className="relative w-full aspect-[3/1] bg-slate-800">
                {creator.bannerUrl ? (
                    <img src={creator.bannerUrl} alt="" className="w-full h-full object-cover opacity-80" />
                ) : (
                    <div className="w-full h-full bg-gradient-to-br from-modtale-accent/20 via-slate-900 to-black" />
                )}
                <div className="absolute top-4 left-4 z-10">
                    <button onClick={onBack} className="p-2 rounded-full bg-black/40 backdrop-blur text-white hover:bg-black/60 transition-colors">
                        <ChevronLeft className="w-5 h-5" />
                    </button>
                </div>
            </div>

            <div className="px-4 pb-6 relative">
                <div className="flex justify-between items-end -mt-10 mb-3">
                    <div className="w-24 h-24 rounded-2xl border-4 border-white dark:border-slate-900 shadow-md overflow-hidden bg-slate-100 dark:bg-slate-800">
                        {isOrg ? (
                            creator.avatarUrl ? (
                                <img src={creator.avatarUrl} alt={creator.username} className="w-full h-full object-cover" />
                            ) : (
                                <div className="w-full h-full bg-slate-200 dark:bg-slate-700 flex items-center justify-center">
                                    <Building2 className="w-10 h-10 text-slate-400" />
                                </div>
                            )
                        ) : (
                            <img src={creator.avatarUrl || `https://api.dicebear.com/7.x/identicon/svg?seed=${username}`} alt={creator.username} className="w-full h-full object-cover" />
                        )}
                    </div>

                    <div className="flex gap-2 mb-1 flex-wrap justify-end" ref={btnContainerRef}>
                        {linkedAccounts.slice(0, 3).map(acc => <SocialButton key={acc.provider} account={acc} mobile={true} />)}
                    </div>
                </div>

                <div className="mb-4">
                    <div className="flex items-center gap-2 mb-1">
                        <h1 className="text-2xl font-black text-slate-900 dark:text-white tracking-tight">{displayTitle}</h1>
                        <div className="flex gap-1">
                            {isOrg && <span className="bg-purple-500 text-white text-[10px] font-bold px-2.5 py-1 rounded shadow-md uppercase tracking-tight" title="Organization">Organization</span>}
                            {creator.badges && creator.badges.map(b => <Badge key={b} type={b} />)}
                        </div>
                    </div>
                    {creator.bio && <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed mb-4">{creator.bio}</p>}
                </div>

                <div className="grid grid-cols-4 gap-2 mb-5">
                    <div className="bg-slate-50 dark:bg-white/5 p-3 rounded-xl border border-slate-100 dark:border-white/5 text-center">
                        <div className="text-lg font-black text-slate-900 dark:text-white">{totalDownloads < 1000 ? totalDownloads : (totalDownloads / 1000).toFixed(1) + 'k'}</div>
                        <div className="text-[10px] uppercase font-bold text-slate-400">Downloads</div>
                    </div>
                    <div className="bg-slate-50 dark:bg-white/5 p-3 rounded-xl border border-slate-100 dark:border-white/5 text-center">
                        <div className="text-lg font-black text-slate-900 dark:text-white">{totalFavorites}</div>
                        <div className="text-[10px] uppercase font-bold text-slate-400">Likes</div>
                    </div>
                    <div className="bg-slate-50 dark:bg-white/5 p-3 rounded-xl border border-slate-100 dark:border-white/5 text-center">
                        <div className="text-lg font-black text-slate-900 dark:text-white">{followerCount >= 1000 ? (followerCount / 1000).toFixed(1) + 'k' : followerCount}</div>
                        <div className="text-[10px] uppercase font-bold text-slate-400">Followers</div>
                    </div>
                    <div className="bg-slate-50 dark:bg-white/5 p-3 rounded-xl border border-slate-100 dark:border-white/5 text-center">
                        <div className="text-lg font-black text-slate-900 dark:text-white">{totalItems}</div>
                        <div className="text-[10px] uppercase font-bold text-slate-400">Projects</div>
                    </div>
                </div>

                <div className="flex gap-3">
                    {!isSelf && (
                        <button
                            onClick={onToggleFollow}
                            className={`flex-1 py-2.5 rounded-lg font-bold text-sm flex items-center justify-center gap-2 transition-all active:scale-95 ${isFollowing ? 'bg-slate-100 dark:bg-white/10 text-slate-700 dark:text-slate-200' : 'bg-modtale-accent text-white shadow-lg shadow-modtale-accent/20'}`}
                        >
                            {isLoggedIn ? (
                                isFollowing ? <><UserCheck className="w-4 h-4" /> Following</> : <><UserPlus className="w-4 h-4" /> Follow</>
                            ) : (
                                "Sign in to follow"
                            )}
                        </button>
                    )}
                    <button onClick={copyId} className="px-4 py-2.5 rounded-lg bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 text-slate-500 hover:text-slate-900 dark:hover:text-white transition-colors">
                        {copied ? <Check className="w-5 h-5 text-green-500" /> : <Copy className="w-5 h-5" />}
                    </button>
                </div>
            </div>
        </div>
    );

    const DesktopView = () => (
        <div className="hidden md:block relative mb-16">
            <div className="relative w-full aspect-[3/1] overflow-hidden bg-slate-900">
                {creator.bannerUrl ? (
                    <img src={creator.bannerUrl} alt="" className="w-full h-full object-cover opacity-80" />
                ) : (
                    <div className="w-full h-full bg-gradient-to-br from-modtale-accent/20 via-slate-900 to-black" />
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-slate-50 dark:from-modtale-dark via-transparent to-black/30" />

                <div className="max-w-7xl min-[1600px]:max-w-[100rem] mx-auto px-6 relative z-40 pt-6 transition-[max-width] duration-300">
                    <button onClick={onBack} className="flex items-center text-white/90 font-bold transition-all bg-black/30 hover:bg-black/50 backdrop-blur-md border border-white/10 px-4 py-2 rounded-xl w-fit shadow-lg group">
                        <ChevronLeft className="w-4 h-4 mr-1 group-hover:-translate-x-1 transition-transform" /> Back
                    </button>
                </div>
            </div>

            <div className="max-w-7xl min-[1600px]:max-w-[100rem] mx-auto px-4 relative z-50 -mt-32 transition-[max-width] duration-300">
                <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-3xl p-10 shadow-2xl flex flex-row gap-8 items-start">
                    <div className="flex-shrink-0 -mt-20">
                        <div className="w-48 h-48 rounded-[2rem] border-[6px] border-white dark:border-slate-800 shadow-xl overflow-hidden bg-slate-100 dark:bg-slate-800">
                            {isOrg ? (
                                creator.avatarUrl ? (
                                    <img src={creator.avatarUrl} alt={creator.username} className="w-full h-full object-cover" />
                                ) : (
                                    <div className="w-full h-full bg-slate-200 dark:bg-slate-700 flex items-center justify-center">
                                        <Building2 className="w-20 h-20 text-slate-400" />
                                    </div>
                                )
                            ) : (
                                <img src={creator.avatarUrl || `https://api.dicebear.com/7.x/identicon/svg?seed=${username}`} alt={creator.username} className="w-full h-full object-cover" />
                            )}
                        </div>
                    </div>

                    <div className="flex-1 w-full">
                        <div className="flex flex-row justify-between items-center gap-4 mb-4">
                            <div>
                                <div className="flex items-center justify-start gap-3">
                                    <h1 className="text-5xl font-black text-slate-900 dark:text-white tracking-tighter">{displayTitle}</h1>
                                    <div className="flex gap-1.5">
                                        {isOrg && <span className="bg-purple-500 text-white text-xs font-bold px-3 py-1 rounded-md shadow-md uppercase tracking-wide flex items-center gap-1"><Building2 className="w-3 h-3" /> Organization</span>}
                                        {creator.badges && creator.badges.map(b => <Badge key={b} type={b} />)}
                                    </div>
                                </div>
                                <div className="flex items-center gap-2 mt-1">
                                    <p className="text-sm font-bold text-slate-400 uppercase tracking-widest">
                                        {isOrg ? 'Organization' : 'Creator'}
                                    </p>
                                </div>
                            </div>

                            <div className="flex items-center gap-3">
                                <button onClick={copyId} className="p-3 rounded-xl border border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-white/5 text-slate-400 hover:text-modtale-accent transition-all" title="Copy ID">
                                    {copied ? <Check className="w-5 h-5 text-green-500" /> : <Copy className="w-5 h-5" />}
                                </button>
                                {!isSelf && (
                                    <button onClick={onToggleFollow} className={`px-8 py-3 rounded-xl font-black flex items-center gap-2 transition-all shadow-lg active:scale-95 ${isFollowing ? 'bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-300 hover:text-red-500' : 'bg-modtale-accent text-white hover:bg-modtale-accentHover'}`}>
                                        {isLoggedIn ? (
                                            isFollowing ? <><UserCheck className="w-5 h-5" /> Following</> : <><UserPlus className="w-5 h-5" /> Follow</>
                                        ) : (
                                            "Sign in to follow"
                                        )}
                                    </button>
                                )}
                            </div>
                        </div>

                        {creator.bio && <p className="text-slate-600 dark:text-slate-300 leading-relaxed text-lg mb-6 text-left">{creator.bio}</p>}

                        <div className="flex flex-row items-center justify-between gap-6 border-t border-slate-200 dark:border-white/5 pt-6">
                            <div className="flex gap-8 text-left">
                                <div><div className="text-2xl font-black text-slate-900 dark:text-white">{totalDownloads.toLocaleString()}</div><div className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Downloads</div></div>
                                <div><div className="text-2xl font-black text-slate-900 dark:text-white">{totalFavorites.toLocaleString()}</div><div className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Favorites</div></div>
                                <div><div className="text-2xl font-black text-slate-900 dark:text-white">{followerCount.toLocaleString()}</div><div className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Followers</div></div>
                                <div><div className="text-2xl font-black text-slate-900 dark:text-white">{totalItems}</div><div className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Projects</div></div>
                            </div>

                            {linkedAccounts.length > 0 && (
                                <div className="flex gap-2" ref={btnContainerRef}>
                                    {linkedAccounts.map(acc => <SocialButton key={acc.provider} account={acc} />)}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );

    return (
        <>
            <MobileView />
            <DesktopView />
        </>
    );
};