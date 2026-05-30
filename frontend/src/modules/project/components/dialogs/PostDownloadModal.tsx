import React, { useState, useEffect } from 'react';
import { Download, X, AlertCircle, Check, Copy } from 'lucide-react';
import { theme } from '@/styles/theme';
import { useScrollLock } from '@/hooks/useScrollLock';

interface PostDownloadModalProps {
    isOpen: boolean;
    onClose: () => void;
    classification: string;
    title: string;
    channel?: 'RELEASE' | 'BETA' | 'ALPHA';
    isBundle?: boolean;
}

export const PostDownloadModal: React.FC<PostDownloadModalProps> = ({
                                                                        isOpen, onClose, classification, title, channel = 'RELEASE', isBundle = false
                                                                    }) => {
    useScrollLock(isOpen);
    const [os, setOs] = useState<'windows' | 'macos' | 'linux'>('windows');
    const [copied, setCopied] = useState(false);
    const [dontShow, setDontShow] = useState(false);

    useEffect(() => {
        if (isOpen) {
            const userAgent = window.navigator.userAgent.toLowerCase();
            if (userAgent.includes('mac')) setOs('macos');
            else if (userAgent.includes('linux')) setOs('linux');
            else setOs('windows');
        }
    }, [isOpen]);

    if (!isOpen) return null;

    const isWorld = classification === 'SAVE';
    const folderName = isWorld ? 'Saves' : 'Mods';

    const paths = {
        windows: `C:\\Program Files\\Hypixel Studios\\Hytale Launcher\\UserData\\${folderName}`,
        macos: `/Applications/Hytale Launcher.app/Contents/MacOS/UserData/${folderName}`,
        linux: `~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/${folderName}`
    };

    const handleCopy = () => {
        navigator.clipboard.writeText(paths[os]);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    const handleClose = () => {
        if (dontShow) localStorage.setItem('hideInstallInstructions', 'true');
        onClose();
    };

    const themeColors = channel === 'ALPHA' ? {
        text: 'text-red-600 dark:text-red-400',
        bg: 'bg-red-600',
        bgAlpha: 'bg-red-500/10 dark:bg-red-500/20',
        border: 'border-red-500/20',
        iconGlow: 'bg-red-50 dark:bg-red-500/10 border-red-200 dark:border-red-500/20',
        checkHover: 'group-hover:border-red-500/30'
    } : channel === 'BETA' ? {
        text: 'text-purple-600 dark:text-purple-400',
        bg: 'bg-purple-600',
        bgAlpha: 'bg-purple-500/10 dark:bg-purple-500/20',
        border: 'border-purple-500/20',
        iconGlow: 'bg-purple-50 dark:bg-purple-500/10 border-purple-200 dark:border-purple-500/20',
        checkHover: 'group-hover:border-purple-500/30'
    } : {
        text: theme.colors.accent,
        bg: theme.colors.accentBg,
        bgAlpha: theme.colors.accentAlpha,
        border: theme.colors.border,
        iconGlow: 'bg-blue-50 dark:bg-blue-500/10 border-blue-200 dark:border-blue-500/20 text-blue-600 dark:text-blue-400',
        checkHover: 'group-hover:border-blue-300 dark:group-hover:border-blue-500/40'
    };

    const unzipInstructions = {
        windows: (
            <p className={theme.colors.textSecondary}>
                Right-click the downloaded bundle and select <strong>Extract All</strong>.
            </p>
        ),
        macos: (
            <p className={theme.colors.textSecondary}>
                Double-click the downloaded bundle, or right-click and choose <strong>Open With → Archive Utility</strong>.
            </p>
        ),
        linux: (
            <p className={theme.colors.textSecondary}>
                In your file manager, use <strong>Extract Here</strong> or <strong>Extract To…</strong> (wording varies by desktop environment). If needed, use terminal: <code className={`px-1.5 py-0.5 rounded-md font-mono text-xs shadow-inner ${theme.colors.bgSurface} ${theme.colors.border} ${themeColors.text}`}>unzip your-file.zip</code>.
            </p>
        )
    };

    return (
        <div className={theme.components.modalOverlay} onClick={handleClose}>
            <div className={`${theme.components.modalContent} max-w-xl ring-1 ring-slate-900/5 dark:ring-white/10`} onClick={e => e.stopPropagation()}>
                <div className={theme.components.modalHeader}>
                    <div className="flex items-center gap-4">
                        <div className={`w-12 h-12 rounded-2xl border flex items-center justify-center shadow-inner ${themeColors.iconGlow}`}>
                            <Download className={`w-6 h-6 ${channel === 'RELEASE' ? 'text-blue-500 dark:text-blue-400' : themeColors.text}`} />
                        </div>
                        <div>
                            <h3 className={`text-xl font-black ${theme.colors.textPrimary} tracking-tight`}>Download Started</h3>
                            <p className={`text-xs ${theme.colors.textSecondary} font-medium mt-1`}>Installation instructions for {title}</p>
                        </div>
                    </div>
                    <button type="button" onClick={handleClose} className={`p-2 rounded-full ${theme.colors.bgSurfaceHover} ${theme.colors.textMuted} hover:${theme.colors.textPrimary} transition-colors`}><X className="w-5 h-5" /></button>
                </div>

                <div className={`${theme.components.modalBody} !p-0 ${theme.colors.bgSurface}`}>
                    {channel === 'ALPHA' && (
                        <div className="mx-6 mt-6 p-4 rounded-xl bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 flex gap-3 text-red-700 dark:text-red-400">
                            <AlertCircle className="w-5 h-5 shrink-0" />
                            <div>
                                <p className="font-bold text-sm text-red-800 dark:text-red-300">Alpha Version</p>
                                <p className="text-xs text-red-600 dark:text-red-200 mt-0.5 leading-relaxed">This is an early testing version. It may contain severe bugs, lack features, or cause data corruption. Use with caution.</p>
                            </div>
                        </div>
                    )}

                    {channel === 'BETA' && (
                        <div className="mx-6 mt-6 p-4 rounded-xl bg-purple-50 dark:bg-purple-500/10 border border-purple-200 dark:border-purple-500/20 flex gap-3 text-purple-700 dark:text-purple-400">
                            <AlertCircle className="w-5 h-5 shrink-0" />
                            <div>
                                <p className="font-bold text-sm text-purple-800 dark:text-purple-300">Beta Version</p>
                                <p className="text-xs text-purple-600 dark:text-purple-200 mt-0.5 leading-relaxed">This version is in testing and may contain bugs. Please report any issues you find to the developer.</p>
                            </div>
                        </div>
                    )}

                    <div className="p-6">
                        <div className={`flex rounded-xl p-1.5 border ${theme.colors.border} mb-6 bg-slate-200/60 dark:bg-slate-800/70`}>
                            {(['windows', 'macos', 'linux'] as const).map(platform => (
                                <button
                                    key={platform}
                                    type="button"
                                    onClick={() => setOs(platform)}
                                    className={`flex-1 py-2 text-xs font-bold rounded-lg capitalize transition-all ${os === platform ? `${themeColors.bg} text-white shadow-md` : `${theme.colors.textSecondary} hover:${theme.colors.textPrimary} hover:bg-white dark:hover:bg-white/5`}`}
                                >
                                    {platform}
                                </button>
                            ))}
                        </div>

                        <div className={`space-y-3 text-sm ${theme.colors.textPrimary} font-medium`}>
                            <div className={`flex gap-4 p-4 rounded-2xl border shadow-sm ${theme.colors.bgBase} ${theme.colors.border}`}>
                                <div className={`w-8 h-8 rounded-full ${themeColors.bgAlpha} ${themeColors.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>1</div>
                                <div className="pt-1.5">
                                    Locate the downloaded {isWorld || isBundle ? 'file' : 'zip'} in your <code className={`px-1.5 py-0.5 rounded-md font-mono text-xs shadow-inner ${theme.colors.bgSurface} ${theme.colors.border} ${themeColors.text}`}>Downloads</code> folder.
                                </div>
                            </div>

                            {isBundle && (
                                <div className={`flex gap-4 p-4 rounded-2xl border shadow-sm ${theme.colors.bgBase} ${theme.colors.border}`}>
                                    <div className={`w-8 h-8 rounded-full ${themeColors.bgAlpha} ${themeColors.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>2</div>
                                    <div className="w-full min-w-0 pt-1.5">
                                        <p className={`font-bold ${theme.colors.accent} mb-1 flex items-center gap-1.5`}><AlertCircle className="w-4 h-4"/> Important: Unzip the file!</p>
                                        {unzipInstructions[os]}
                                    </div>
                                </div>
                            )}

                            <div className={`flex gap-4 p-4 rounded-2xl border shadow-sm ${theme.colors.bgBase} ${theme.colors.border}`}>
                                <div className={`w-8 h-8 rounded-full ${themeColors.bgAlpha} ${themeColors.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>{isBundle ? '3' : '2'}</div>
                                <div className="w-full min-w-0 pt-1.5">
                                    <p className="mb-3">{isWorld ? 'Extract and move the world folder into your Hytale Saves directory:' : `Move ${isBundle ? 'ALL extracted files' : 'the downloaded file'} to your Hytale Mods directory:`}</p>
                                    <div className={`flex items-center gap-3 border rounded-xl p-2 pl-3 ${theme.colors.bgSurface} ${theme.colors.border}`}>
                                        <code className={`flex-1 font-mono text-[11px] ${theme.colors.textSecondary} break-all select-all leading-relaxed`}>{paths[os]}</code>
                                        <button type="button" onClick={handleCopy} className={`p-2 rounded-lg border transition-colors shadow-sm shrink-0 self-start ${theme.colors.bgBase} ${theme.colors.border} ${theme.colors.textMuted} hover:${theme.colors.textPrimary} ${theme.colors.bgSurfaceHover}`} title="Copy Path">
                                            {copied ? <Check className={`w-4 h-4 ${theme.colors.successText}`} /> : <Copy className="w-4 h-4" />}
                                        </button>
                                    </div>
                                </div>
                            </div>

                            <div className={`flex gap-4 p-4 rounded-2xl border shadow-sm ${theme.colors.bgBase} ${theme.colors.border}`}>
                                <div className={`w-8 h-8 rounded-full ${themeColors.bgAlpha} ${themeColors.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>{isBundle ? '4' : '3'}</div>
                                <div className="pt-1.5">
                                    {isWorld ? 'Launch Hytale and select the world from the Singleplayer menu.' : `Restart your Hytale Launcher to load the new project${isBundle ? 's' : ''}.`}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div className={theme.components.modalFooter}>
                    <button type="button" onClick={() => setDontShow(!dontShow)} className="flex items-center gap-2.5 cursor-pointer group">
                        <div className={`w-5 h-5 rounded-md border flex items-center justify-center transition-colors shadow-inner ${dontShow ? `${themeColors.bg} ${themeColors.border}` : `${theme.colors.bgSurfaceAlt} ${theme.colors.border} ${themeColors.checkHover}`}`}>
                            {dontShow && <Check className="w-3.5 h-3.5 text-white" strokeWidth={3} />}
                        </div>
                        <span className={`text-xs font-bold ${theme.colors.textSecondary} group-hover:${theme.colors.textPrimary} transition-colors select-none uppercase tracking-wider`}>Don't show again</span>
                    </button>
                    <button type="button" onClick={handleClose} className={`px-8 py-2.5 rounded-xl font-black ${theme.colors.accentBg} hover:bg-modtale-accentHover text-white transition-colors shadow-lg active:scale-95 text-sm`}>
                        Got it
                    </button>
                </div>
            </div>
        </div>
    );
};
