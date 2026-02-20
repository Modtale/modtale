import React, { useState } from 'react';
import { X, Plus, Trash2, Trophy } from 'lucide-react';
import { api } from '@/utils/api';
import type { Modjam } from '@/types';
import { Spinner } from '@/components/ui/Spinner';

interface CreateJamModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: (newJam: Modjam) => void;
}

export const CreateJamModal: React.FC<CreateJamModalProps> = ({ isOpen, onClose, onSuccess }) => {
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [votingEndDate, setVotingEndDate] = useState('');
    const [allowPublicVoting, setAllowPublicVoting] = useState(true);
    const [categories, setCategories] = useState<{ name: string; description: string; maxScore: number }[]>([]);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    if (!isOpen) return null;

    const handleAddCategory = () => {
        setCategories([...categories, { name: '', description: '', maxScore: 5 }]);
    };

    const handleUpdateCategory = (index: number, field: string, value: string | number) => {
        const newCats = [...categories];
        newCats[index] = { ...newCats[index], [field]: value };
        setCategories(newCats);
    };

    const handleRemoveCategory = (index: number) => {
        setCategories(categories.filter((_, i) => i !== index));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);

        try {
            const payload = {
                title,
                description,
                startDate: new Date(startDate).toISOString(),
                endDate: new Date(endDate).toISOString(),
                votingEndDate: new Date(votingEndDate).toISOString(),
                allowPublicVoting,
                categories
            };

            const res = await api.post('/modjams', payload);
            onSuccess(res.data);
            onClose();
        } catch (err: any) {
            setError(err.response?.data?.message || 'Failed to create jam.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200">
            <div className="bg-white dark:bg-modtale-card rounded-3xl w-full max-w-3xl max-h-[90vh] flex flex-col shadow-2xl border border-slate-200 dark:border-white/10 overflow-hidden">
                <div className="flex items-center justify-between p-6 md:p-8 border-b border-slate-200 dark:border-white/5 bg-slate-50 dark:bg-slate-900/50">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-modtale-accent/10 text-modtale-accent flex items-center justify-center">
                            <Trophy className="w-5 h-5" />
                        </div>
                        <h2 className="text-2xl font-black text-slate-900 dark:text-white">Host a New Jam</h2>
                    </div>
                    <button onClick={onClose} className="p-2 text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 rounded-full transition-colors">
                        <X className="w-6 h-6" />
                    </button>
                </div>

                <div className="overflow-y-auto p-6 md:p-8 custom-scrollbar">
                    <form id="create-jam-form" onSubmit={handleSubmit} className="space-y-8">
                        {error && (
                            <div className="bg-red-50 text-red-600 dark:bg-red-900/20 dark:text-red-400 p-4 rounded-xl font-bold text-sm border border-red-500/20">
                                {error}
                            </div>
                        )}

                        <div className="space-y-6">
                            <div>
                                <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">Jam Title</label>
                                <input
                                    type="text"
                                    required
                                    value={title}
                                    onChange={(e) => setTitle(e.target.value)}
                                    className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 font-bold text-lg text-slate-900 dark:text-white outline-none focus:ring-2 focus:ring-modtale-accent transition-all"
                                    placeholder="e.g., Summer Modding Spree 2026"
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">Description</label>
                                <textarea
                                    required
                                    value={description}
                                    onChange={(e) => setDescription(e.target.value)}
                                    className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 text-slate-900 dark:text-white outline-none focus:ring-2 focus:ring-modtale-accent min-h-[120px] transition-all"
                                    placeholder="What is the theme and goal of this jam?"
                                />
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                <div>
                                    <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">Start Date</label>
                                    <input
                                        type="datetime-local"
                                        required
                                        value={startDate}
                                        onChange={(e) => setStartDate(e.target.value)}
                                        className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 text-sm font-medium text-slate-900 dark:text-white outline-none focus:ring-2 focus:ring-modtale-accent transition-all color-scheme-dark"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">Submissions Close</label>
                                    <input
                                        type="datetime-local"
                                        required
                                        value={endDate}
                                        onChange={(e) => setEndDate(e.target.value)}
                                        className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 text-sm font-medium text-slate-900 dark:text-white outline-none focus:ring-2 focus:ring-modtale-accent transition-all color-scheme-dark"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">Voting Ends</label>
                                    <input
                                        type="datetime-local"
                                        required
                                        value={votingEndDate}
                                        onChange={(e) => setVotingEndDate(e.target.value)}
                                        className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 text-sm font-medium text-slate-900 dark:text-white outline-none focus:ring-2 focus:ring-modtale-accent transition-all color-scheme-dark"
                                    />
                                </div>
                            </div>

                            <div className="flex items-center gap-3 p-4 rounded-xl border border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-white/5 cursor-pointer" onClick={() => setAllowPublicVoting(!allowPublicVoting)}>
                                <div className={`w-6 h-6 rounded-md border-2 flex items-center justify-center transition-colors ${allowPublicVoting ? 'bg-modtale-accent border-modtale-accent' : 'border-slate-300 dark:border-slate-600'}`}>
                                    {allowPublicVoting && <div className="w-2 h-2 bg-white rounded-sm" />}
                                </div>
                                <div>
                                    <div className="font-bold text-slate-900 dark:text-white">Allow Public Voting</div>
                                    <div className="text-xs text-slate-500 font-medium">If unchecked, only the host can vote on submissions.</div>
                                </div>
                            </div>
                        </div>

                        <div className="pt-6 border-t border-slate-200 dark:border-white/5">
                            <div className="flex items-center justify-between mb-6">
                                <div>
                                    <h3 className="text-lg font-black text-slate-900 dark:text-white">Judging Categories</h3>
                                    <p className="text-sm text-slate-500 font-medium">Define how submissions will be scored.</p>
                                </div>
                                <button
                                    type="button"
                                    onClick={handleAddCategory}
                                    className="px-4 py-2 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-slate-900 dark:text-white text-sm font-bold rounded-xl flex items-center gap-2 transition-colors"
                                >
                                    <Plus className="w-4 h-4" /> Add Category
                                </button>
                            </div>

                            {categories.length === 0 ? (
                                <div className="text-center p-8 border-2 border-dashed border-slate-200 dark:border-white/10 rounded-2xl">
                                    <p className="text-sm text-slate-500 font-bold mb-2">No categories defined.</p>
                                    <p className="text-xs text-slate-400">The jam will rely on a single default overall score.</p>
                                </div>
                            ) : (
                                <div className="space-y-4">
                                    {categories.map((cat, i) => (
                                        <div key={i} className="flex flex-col sm:flex-row gap-4 items-start bg-slate-50 dark:bg-black/20 p-5 rounded-2xl border border-slate-200 dark:border-white/10 group transition-all hover:border-slate-300 dark:hover:border-white/20">
                                            <div className="flex-1 w-full space-y-4">
                                                <input
                                                    type="text"
                                                    required
                                                    value={cat.name}
                                                    onChange={(e) => handleUpdateCategory(i, 'name', e.target.value)}
                                                    className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 text-sm font-bold outline-none focus:ring-2 focus:ring-modtale-accent"
                                                    placeholder="Category Name (e.g. Visuals, Creativity)"
                                                />
                                                <input
                                                    type="text"
                                                    value={cat.description}
                                                    onChange={(e) => handleUpdateCategory(i, 'description', e.target.value)}
                                                    className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                                    placeholder="Short description (Optional)"
                                                />
                                            </div>
                                            <div className="w-full sm:w-28 flex flex-row sm:flex-col items-center sm:items-start gap-4 sm:gap-1">
                                                <div className="flex-1 sm:w-full">
                                                    <label className="block text-[10px] font-bold uppercase tracking-widest mb-1 text-slate-500">Max Score</label>
                                                    <input
                                                        type="number"
                                                        min="1" max="100"
                                                        required
                                                        value={cat.maxScore}
                                                        onChange={(e) => handleUpdateCategory(i, 'maxScore', parseInt(e.target.value) || 5)}
                                                        className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 text-sm font-bold outline-none focus:ring-2 focus:ring-modtale-accent text-center sm:text-left"
                                                    />
                                                </div>
                                                <button
                                                    type="button"
                                                    onClick={() => handleRemoveCategory(i)}
                                                    className="p-3 bg-red-50 dark:bg-red-500/10 text-red-500 hover:bg-red-100 dark:hover:bg-red-500/20 rounded-xl transition-colors mt-0 sm:mt-5"
                                                    aria-label="Remove category"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </form>
                </div>

                <div className="p-6 border-t border-slate-200 dark:border-white/5 bg-slate-50 dark:bg-slate-900/50 flex justify-end gap-3 mt-auto">
                    <button
                        type="button"
                        onClick={onClose}
                        className="px-6 py-3 rounded-xl font-bold text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors"
                    >
                        Cancel
                    </button>
                    <button
                        type="submit"
                        form="create-jam-form"
                        disabled={loading || !title || !description}
                        className="bg-modtale-accent text-white px-8 py-3 rounded-xl font-black disabled:opacity-50 hover:bg-modtale-accentHover transition-all flex items-center gap-2 shadow-lg shadow-modtale-accent/20 active:scale-95"
                    >
                        {loading ? <Spinner className="w-5 h-5 text-white" fullScreen={false} /> : 'Create Jam'}
                    </button>
                </div>
            </div>
        </div>
    );
};