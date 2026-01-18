import React, { useState } from 'react';
import { User, Camera, Check, Sparkles } from 'lucide-react';
import { api } from '../../utils/api';

interface OnboardingModalProps {
    isOpen: boolean;
    onClose: () => void;
    currentUsername: string;
    currentAvatar: string;
    suggestedUsername?: string;
    suggestedAvatar?: string;
}

export const OnboardingModal: React.FC<OnboardingModalProps> = ({
                                                                    isOpen, onClose, currentUsername, currentAvatar, suggestedUsername, suggestedAvatar
                                                                }) => {
    const [username, setUsername] = useState(currentUsername);
    const [avatar, setAvatar] = useState(currentAvatar);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    if (!isOpen) return null;

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError('');

        try {
            if (username !== currentUsername) {
                await api.put('/user/profile', { username });
            }

            if (avatar !== currentAvatar && avatar === suggestedAvatar) {
                await api.post('/user/profile/avatar/url', { url: avatar });
            }

            window.location.reload();
        } catch (err: any) {
            setError(err.response?.data || "Failed to update profile.");
            setLoading(false);
        }
    };

    return (
        <div className="fixed inset-0 z-[200] bg-slate-950/90 backdrop-blur-md flex items-center justify-center p-4">
            <div className="bg-white dark:bg-slate-900 w-full max-w-md p-8 rounded-3xl shadow-2xl border border-slate-200 dark:border-white/10 relative overflow-hidden">

                <div className="absolute top-0 right-0 w-64 h-64 bg-modtale-accent/10 rounded-full blur-3xl -translate-y-1/2 translate-x-1/2 pointer-events-none" />

                <div className="relative z-10">
                    <div className="text-center mb-8">
                        <div className="w-16 h-16 bg-modtale-accent/10 rounded-2xl flex items-center justify-center mx-auto mb-4 text-modtale-accent">
                            <Sparkles className="w-8 h-8" />
                        </div>
                        <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Welcome to Modtale!</h2>
                        <p className="text-slate-500 dark:text-slate-400">
                            We've created an anonymous profile for you. <br/>You can customize it now, or do it later.
                        </p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-6">
                        <div className="flex flex-col items-center gap-4">
                            <div className="relative group">
                                <div className="w-24 h-24 rounded-full overflow-hidden border-4 border-white dark:border-slate-800 shadow-xl">
                                    <img src={avatar} alt="Profile" className="w-full h-full object-cover" />
                                </div>
                                {suggestedAvatar && avatar !== suggestedAvatar && (
                                    <button
                                        type="button"
                                        onClick={() => setAvatar(suggestedAvatar)}
                                        className="absolute bottom-0 right-0 bg-blue-500 text-white p-2 rounded-full shadow-lg hover:bg-blue-600 transition-colors"
                                        title="Use Google Photo"
                                    >
                                        <Camera className="w-4 h-4" />
                                    </button>
                                )}
                            </div>

                            {suggestedAvatar && avatar !== suggestedAvatar && (
                                <button type="button" onClick={() => setAvatar(suggestedAvatar)} className="text-xs text-blue-500 font-bold hover:underline">
                                    Use Google Profile Picture
                                </button>
                            )}
                        </div>

                        <div>
                            <label className="block text-xs font-bold text-slate-400 uppercase mb-2">Username</label>
                            <input
                                type="text"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                className="w-full p-3 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl font-bold dark:text-white focus:ring-2 focus:ring-modtale-accent outline-none"
                            />
                            {suggestedUsername && username !== suggestedUsername && (
                                <button
                                    type="button"
                                    onClick={() => setUsername(suggestedUsername)}
                                    className="text-xs text-blue-500 font-bold mt-2 hover:underline flex items-center gap-1"
                                >
                                    Use "{suggestedUsername}" instead?
                                </button>
                            )}
                        </div>

                        {error && <div className="text-red-500 text-sm font-bold text-center">{error}</div>}

                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full py-4 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-xl font-black text-lg shadow-lg shadow-modtale-accent/20 transition-all flex items-center justify-center gap-2"
                        >
                            {loading ? 'Setting up...' : 'Continue to Modtale'} <div className="bg-white/20 p-1 rounded-full"><Check className="w-4 h-4" /></div>
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
};