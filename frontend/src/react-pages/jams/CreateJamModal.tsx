import React, { useState } from 'react';
import { X, Plus, Trash2 } from 'lucide-react';
import { api } from '@/utils/api';
import type { Modjam } from '@/types';

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
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
            <div className="bg-white dark:bg-slate-900 rounded-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto shadow-2xl">
                <div className="flex items-center justify-between p-6 border-b border-slate-200 dark:border-white/10 sticky top-0 bg-white/90 dark:bg-slate-900/90 backdrop-blur-md z-10">
                    <h2 className="text-xl font-black">Host a New Jam</h2>
                    <button onClick={onClose} className="p-2 hover:bg-slate-100 dark:hover:bg-white/10 rounded-full transition-colors">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="p-6 space-y-6">
                    {error && (
                        <div className="bg-red-50 text-red-600 dark:bg-red-900/20 dark:text-red-400 p-4 rounded-xl font-bold text-sm">
                            {error}
                        </div>
                    )}

                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-bold mb-2">Jam Title</label>
                            <input
                                type="text"
                                required
                                value={title}
                                onChange={(e) => setTitle(e.target.value)}
                                className="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent"
                                placeholder="e.g., Summer Modding Spree 2026"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-bold mb-2">Description</label>
                            <textarea
                                required
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                className="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent min-h-[100px]"
                                placeholder="What is this jam about?"
                            />
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                            <div>
                                <label className="block text-sm font-bold mb-2">Start Date</label>
                                <input
                                    type="datetime-local"
                                    required
                                    value={startDate}
                                    onChange={(e) => setStartDate(e.target.value)}
                                    className="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-bold mb-2">End Date (Submissions Close)</label>
                                <input
                                    type="datetime-local"
                                    required
                                    value={endDate}
                                    onChange={(e) => setEndDate(e.target.value)}
                                    className="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-bold mb-2">Voting Ends</label>
                                <input
                                    type="datetime-local"
                                    required
                                    value={votingEndDate}
                                    onChange={(e) => setVotingEndDate(e.target.value)}
                                    className="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent"
                                />
                            </div>
                        </div>

                        <label className="flex items-center gap-3 cursor-pointer mt-2">
                            <input
                                type="checkbox"
                                checked={allowPublicVoting}
                                onChange={(e) => setAllowPublicVoting(e.target.checked)}
                                className="w-5 h-5 rounded text-modtale-accent focus:ring-modtale-accent"
                            />
                            <span className="font-bold text-sm">Allow Public Voting</span>
                        </label>
                    </div>

                    <div className="pt-4 border-t border-slate-200 dark:border-white/10">
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="font-bold">Judging Categories</h3>
                            <button
                                type="button"
                                onClick={handleAddCategory}
                                className="text-sm font-bold text-modtale-accent flex items-center gap-1 hover:opacity-80"
                            >
                                <Plus className="w-4 h-4" /> Add Category
                            </button>
                        </div>

                        {categories.length === 0 ? (
                            <p className="text-sm text-slate-500 italic">No categories added. Jam will rely on a single overall score if empty.</p>
                        ) : (
                            <div className="space-y-4">
                                {categories.map((cat, i) => (
                                    <div key={i} className="flex gap-4 items-start bg-slate-50 dark:bg-slate-800 p-4 rounded-xl border border-slate-200 dark:border-white/10">
                                        <div className="flex-1 space-y-3">
                                            <input
                                                type="text"
                                                required
                                                value={cat.name}
                                                onChange={(e) => handleUpdateCategory(i, 'name', e.target.value)}
                                                className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                                placeholder="Category Name (e.g. Visuals)"
                                            />
                                            <input
                                                type="text"
                                                value={cat.description}
                                                onChange={(e) => handleUpdateCategory(i, 'description', e.target.value)}
                                                className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                                placeholder="Description (Optional)"
                                            />
                                        </div>
                                        <div className="w-24">
                                            <label className="block text-xs font-bold mb-1 text-slate-500">Max Score</label>
                                            <input
                                                type="number"
                                                min="1" max="100"
                                                required
                                                value={cat.maxScore}
                                                onChange={(e) => handleUpdateCategory(i, 'maxScore', parseInt(e.target.value) || 5)}
                                                className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                            />
                                        </div>
                                        <button
                                            type="button"
                                            onClick={() => handleRemoveCategory(i)}
                                            className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg mt-5"
                                        >
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    <div className="pt-6 flex justify-end gap-3">
                        <button
                            type="button"
                            onClick={onClose}
                            className="px-6 py-3 rounded-xl font-bold hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={loading}
                            className="bg-modtale-accent text-white px-6 py-3 rounded-xl font-bold disabled:opacity-50 hover:bg-modtale-accent/90 transition-colors"
                        >
                            {loading ? 'Creating...' : 'Create Jam'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};