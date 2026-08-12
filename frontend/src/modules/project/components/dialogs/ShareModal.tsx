import React, { useState } from 'react';
import { Share2, X, Copy, Check, MessageCircle, Send, Link as LinkIcon } from 'lucide-react';
import { theme } from '@/styles/theme';
import { useScrollLock } from '@/hooks/useScrollLock';
import { ModalPortal } from '@/components/ui/ModalPortal';

interface ShareModalProps {
    isOpen: boolean;
    onClose: () => void;
    url: string;
    title: string;
    author: string;
}

export const ShareModal: React.FC<ShareModalProps> = ({ isOpen, onClose, url, title, author }) => {
    const [copied, setCopied] = useState(false);
    useScrollLock(isOpen);

    if (!isOpen) return null;

    const handleCopy = () => {
        navigator.clipboard.writeText(url);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    const shareText = `Check out ${title} by ${author} on Modtale!`;

    const shareLinks = [
        {
            name: 'Twitter',
            icon: MessageCircle,
            url: `https://twitter.com/intent/tweet?text=${encodeURIComponent(shareText)}&url=${encodeURIComponent(url)}`,
            color: 'bg-[#1DA1F2] hover:bg-[#1a91da]'
        },
        {
            name: 'Facebook',
            icon: Send,
            url: `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}`,
            color: 'bg-[#4267B2] hover:bg-[#3b5ca0]'
        }
    ];

    return (
        <ModalPortal>
        <div className={theme.components.modalOverlay} onClick={onClose}>
            <div className={`${theme.components.modalContent} max-w-md`} onClick={e => e.stopPropagation()}>
                <div className={theme.components.modalHeader}>
                    <h3 className={`font-bold ${theme.colors.textPrimary} flex items-center gap-2`}>
                        <Share2 className="w-5 h-5 text-modtale-accent" />
                        Share Project
                    </h3>
                    <button type="button" onClick={onClose} className={`p-2 ${theme.colors.bgSurfaceHover} rounded-full transition-colors`}>
                        <X className="w-4 h-4" />
                    </button>
                </div>

                <div className="p-6 space-y-6">
                    <div>
                        <label className={`block text-xs font-bold ${theme.colors.textSecondary} uppercase tracking-wider mb-2`}>Direct Link</label>
                        <div className={`flex items-center gap-2 p-2 ${theme.colors.bgSurface} border ${theme.colors.border} rounded-xl`}>
                            <div className={`flex-1 min-w-0 px-2 py-1 ${theme.colors.bgSurfaceAlt} rounded-lg flex items-center gap-2`}>
                                <LinkIcon className={`w-4 h-4 ${theme.colors.textMuted} shrink-0`} />
                                <span className={`text-sm ${theme.colors.textPrimary} truncate font-mono`}>{url}</span>
                            </div>
                            <button
                                type="button"
                                onClick={handleCopy}
                                className={`px-4 py-2 ${theme.colors.accentBg} hover:bg-modtale-accentHover text-white rounded-lg font-bold text-sm transition-colors flex items-center gap-2 shrink-0 shadow-sm`}
                            >
                                {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                                {copied ? 'Copied!' : 'Copy'}
                            </button>
                        </div>
                    </div>

                    <div>
                        <label className={`block text-xs font-bold ${theme.colors.textSecondary} uppercase tracking-wider mb-2`}>Share via</label>
                        <div className="grid grid-cols-2 gap-3">
                            {shareLinks.map((link) => (
                                <a
                                    key={link.name}
                                    href={link.url}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className={`flex items-center justify-center gap-2 p-3 rounded-xl text-white font-bold text-sm transition-colors ${link.color} shadow-sm`}
                                >
                                    <link.icon className="w-4 h-4" /> {link.name}
                                </a>
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        </div>
        </ModalPortal>
    );
};
