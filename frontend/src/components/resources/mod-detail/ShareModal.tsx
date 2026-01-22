import React, { useState, useEffect } from 'react';
import { X, Check, Copy, Smartphone, Link as LinkIcon } from 'lucide-react';

interface ShareModalProps {
    isOpen: boolean;
    onClose: () => void;
    url: string;
    title: string;
    author: string;
}

const RedditIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">
        <path d="M6.167 8a.83.83 0 0 0-.83.83c0 .459.372.84.83.831a.831.831 0 0 0 0-1.661m1.843 3.647c.315 0 1.403-.038 1.976-.611a.23.23 0 0 0 0-.306.213.213 0 0 0-.306 0c-.353.363-1.126.487-1.67.487-.545 0-1.308-.124-1.671-.487a.213.213 0 0 0-.306 0 .213.213 0 0 0 0 .306c.564.563 1.652.61 1.977.61zm.992-2.807c0 .458.373.83.831.83s.83-.381.83-.83a.831.831 0 0 0-1.66 0z"/>
        <path d="M16 8A8 8 0 1 1 0 8a8 8 0 0 1 16 0m-3.828-1.165c-.315 0-.602.124-.812.325-.801-.573-1.9-.945-3.121-.993l.534-2.501 1.738.372a.83.83 0 1 0 .83-.869.83.83 0 0 0-.744.468l-1.938-.41a.2.2 0 0 0-.153.028.2.2 0 0 0-.086.134l-.592 2.788c-1.24.038-2.358.41-3.17.992-.21-.2-.496-.324-.81-.324a1.163 1.163 0 0 0-.478 2.224q-.03.17-.029.353c0 1.795 2.091 3.256 4.669 3.256s4.668-1.451 4.668-3.256c0-.114-.01-.238-.029-.353.401-.181.688-.592.688-1.069 0-.65-.525-1.165-1.165-1.165"/>
    </svg>
);

const BlueskyIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M12 10.8c-1.087-2.114-4.046-6.053-6.798-7.995C2.566 .944 1.561 1.266.902 1.565.139 1.908 0 3.08 0 3.768c0 .69.378 5.65.624 6.479.815 2.736 3.713 3.66 6.383 3.364.136-.02.275-.039.415-.056-.138.022-.276.04-.415.056-3.912.58-7.387 2.005-2.83 7.078 5.013 5.58 7.424-4.788 7.823-6.589.06-.243.408-.255.4 0 .397 1.8 2.807 12.169 7.821 6.589 4.557-5.073 1.082-6.498-2.829-7.078-.139-.016-.277-.034-.415-.056.14.017.279.036.415.056 2.67.297 5.568-.628 6.383-3.364.246-.828.624-5.79.624-6.478 0-.69-.139-1.861-.902-2.206-.659-.298-1.664-.62-4.3 1.24C16.046 4.748 13.087 8.686 12 10.8z"/></svg>
);

const XIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M18.901 1.153h3.68l-8.04 9.19L24 22.846h-7.406l-5.8-7.584-6.638 7.584H.474l8.6-9.83L0 1.154h7.594l5.243 6.932ZM17.61 20.644h2.039L6.486 3.24H4.298Z"/></svg>
);

export const ShareModal: React.FC<ShareModalProps> = ({ isOpen, onClose, url, title, author }) => {
    const [copiedType, setCopiedType] = useState<string | null>(null);
    const [canNativeShare, setCanNativeShare] = useState(false);

    useEffect(() => {
        if (typeof navigator !== 'undefined' && 'share' in navigator) {
            setCanNativeShare(true);
        }
    }, []);

    if (!isOpen) return null;

    const handleCopy = (text: string, type: string) => {
        navigator.clipboard.writeText(text);
        setCopiedType(type);
        setTimeout(() => setCopiedType(null), 2000);
    };

    const handleNativeShare = async () => {
        try {
            await navigator.share({
                title: `Check out ${title} on Modtale`,
                text: `I found this awesome Hytale project by ${author}!`,
                url: url
            });
            onClose();
        } catch (err) {}
    };

    const encodedUrl = encodeURIComponent(url);
    const encodedText = encodeURIComponent(`Check out ${title} by ${author} on Modtale!`);

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200" onClick={onClose}>
            <div className="bg-slate-900 border border-white/10 rounded-2xl w-full max-w-md shadow-2xl overflow-hidden relative" onClick={e => e.stopPropagation()}>

                <div className="p-6 border-b border-white/5 flex justify-between items-center bg-black/20">
                    <h3 className="text-xl font-black text-white flex items-center gap-2">
                        Share Project
                    </h3>
                    <button onClick={onClose} className="text-slate-400 hover:text-red-500 transition-colors">
                        <X className="w-6 h-6" />
                    </button>
                </div>

                <div className="p-6 space-y-6">

                    {canNativeShare && (
                        <button
                            onClick={handleNativeShare}
                            className="w-full py-3 bg-white/5 hover:bg-white/10 rounded-xl font-bold text-slate-200 flex items-center justify-center gap-2 transition-colors border border-white/5"
                        >
                            <Smartphone className="w-5 h-5" /> Share via System
                        </button>
                    )}

                    <div className="grid grid-cols-2 gap-3">
                        <a
                            href={`https://www.reddit.com/submit?url=${encodedUrl}&title=${encodedText}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex flex-col items-center justify-center p-3 rounded-xl border border-white/5 bg-white/5 transition-colors hover:bg-[#FF4500]/10 hover:border-[#FF4500]"
                        >
                            <RedditIcon className="w-8 h-8 text-[#FF4500]" />
                            <span className="text-xs font-bold mt-2 text-slate-400">Reddit</span>
                        </a>

                        <a
                            href={`https://twitter.com/intent/tweet?text=${encodedText}&url=${encodedUrl}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex flex-col items-center justify-center p-3 rounded-xl border border-white/5 bg-white/5 transition-colors hover:bg-white/10 hover:border-white"
                        >
                            <XIcon className="w-7 h-7 text-white" />
                            <span className="text-xs font-bold mt-2 text-slate-400">X</span>
                        </a>

                        <a
                            href={`https://bsky.app/intent/compose?text=${encodedText}%20${encodedUrl}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex flex-col items-center justify-center p-3 rounded-xl border border-white/5 bg-white/5 transition-colors hover:bg-[#0085ff]/10 hover:border-[#0085ff]"
                        >
                            <BlueskyIcon className="w-8 h-8 text-[#0085ff]" />
                            <span className="text-xs font-bold mt-2 text-slate-400">Bluesky</span>
                        </a>

                        <button
                            onClick={() => handleCopy(url, 'url')}
                            className={`flex flex-col items-center justify-center p-3 rounded-xl border transition-colors ${copiedType === 'url' ? 'bg-green-500/10 border-green-500' : 'bg-white/5 border-white/5 hover:bg-white/10 hover:border-white/20'}`}
                        >
                            {copiedType === 'url' ? <Check className="w-8 h-8 text-green-500" /> : <LinkIcon className="w-8 h-8 text-slate-500" />}
                            <span className="text-xs font-bold mt-2 text-slate-400">{copiedType === 'url' ? 'Copied!' : 'Copy Link'}</span>
                        </button>
                    </div>

                    <div className="pt-4 border-t border-white/5">
                        <label className="block text-xs font-bold text-slate-500 uppercase mb-2 tracking-widest">Embed Codes</label>
                        <div className="grid grid-cols-1 gap-3">
                            <button
                                onClick={() => handleCopy(`[${title}](${url})`, 'md')}
                                className="flex items-center justify-between w-full p-3 bg-black/20 border border-white/5 rounded-lg group hover:border-modtale-accent transition-colors text-left"
                            >
                                <div>
                                    <div className="text-xs font-bold text-slate-300">Markdown</div>
                                    <div className="text-[10px] text-slate-500">For Reddit, Discord, GitHub</div>
                                </div>
                                <div className={`text-modtale-accent ${copiedType === 'md' ? 'text-green-500' : 'opacity-0 group-hover:opacity-100'}`}>
                                    {copiedType === 'md' ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                                </div>
                            </button>

                            <button
                                onClick={() => handleCopy(`<a href="${url}">${title}</a>`, 'html')}
                                className="flex items-center justify-between w-full p-3 bg-black/20 border border-white/5 rounded-lg group hover:border-modtale-accent transition-colors text-left"
                            >
                                <div>
                                    <div className="text-xs font-bold text-slate-300">HTML</div>
                                    <div className="text-[10px] text-slate-500">For Websites, Blogs</div>
                                </div>
                                <div className={`text-modtale-accent ${copiedType === 'html' ? 'text-green-500' : 'opacity-0 group-hover:opacity-100'}`}>
                                    {copiedType === 'html' ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                                </div>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};