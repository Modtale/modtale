import React, { useState, useRef, useEffect } from 'react';
import { Flag, X, AlertTriangle, ChevronDown, Check } from 'lucide-react';
import { api } from '../../../utils/api';

interface ReportModalProps {
    isOpen: boolean;
    onClose: () => void;
    targetId?: string;
    targetType?: 'PROJECT' | 'COMMENT' | 'USER';
    targetTitle?: string;
}

const REPORT_REASONS = [
    { id: 'MALWARE', label: 'Malware / Virus' },
    { id: 'SPAM', label: 'Spam / Misleading' },
    { id: 'INAPPROPRIATE', label: 'Inappropriate Content' },
    { id: 'IP_INFRINGEMENT', label: 'Intellectual Property Violation' },
    { id: 'HARASSMENT', label: 'Harassment / Hate Speech' },
    { id: 'OTHER', label: 'Other' }
];

export const ReportModal: React.FC<ReportModalProps> = ({ isOpen, onClose, targetId, targetType = 'PROJECT', targetTitle }) => {
    const [reason, setReason] = useState(REPORT_REASONS[0].id);
    const [description, setDescription] = useState('');
    const [loading, setLoading] = useState(false);
    const [submitted, setSubmitted] = useState(false);
    const [error, setError] = useState('');

    const [dropdownOpen, setDropdownOpen] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);

    const effectiveTargetId = targetId || '';
    const effectiveTitle = targetTitle || (targetType === 'COMMENT' ? 'Comment' : (targetType === 'USER' ? 'User Profile' : 'Content'));

    useEffect(() => {
        if (isOpen) {
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = '';
        }
        return () => {
            document.body.style.overflow = '';
        };
    }, [isOpen]);

    useEffect(() => {
        if (dropdownOpen) {
            const preventScroll = (e: Event) => e.preventDefault();
            window.addEventListener('wheel', preventScroll, { passive: false });
            window.addEventListener('touchmove', preventScroll, { passive: false });
            return () => {
                window.removeEventListener('wheel', preventScroll);
                window.removeEventListener('touchmove', preventScroll);
            };
        }
    }, [dropdownOpen]);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
                setDropdownOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    if (!isOpen) return null;

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError('');

        try {
            await api.post('/reports', {
                targetId: effectiveTargetId,
                targetType,
                reason,
                description
            });
            setSubmitted(true);
            setTimeout(() => {
                onClose();
                setSubmitted(false);
                setDescription('');
                setReason(REPORT_REASONS[0].id);
            }, 2000);
        } catch (e) {
            setError('Failed to submit report. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    const selectedLabel = REPORT_REASONS.find(r => r.id === reason)?.label;

    return (
        <div className="fixed inset-0 z-[200] bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
            <div className="bg-white dark:bg-slate-900 w-full max-w-md rounded-2xl shadow-2xl border border-slate-200 dark:border-white/10 flex flex-col">
                <div className="p-4 border-b border-slate-200 dark:border-white/5 flex justify-between items-center bg-slate-50 dark:bg-white/5 rounded-t-2xl">
                    <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2">
                        <Flag className="w-5 h-5 text-red-500" />
                        Report {targetType === 'USER' ? 'User' : (targetType === 'COMMENT' ? 'Comment' : 'Project')}
                    </h3>
                    <button onClick={onClose} className="p-2 hover:bg-black/5 dark:hover:bg-white/10 rounded-full transition-colors">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                <div className="p-6">
                    {submitted ? (
                        <div className="text-center py-8">
                            <div className="w-16 h-16 bg-green-500/10 text-green-500 rounded-full flex items-center justify-center mx-auto mb-4">
                                <Flag className="w-8 h-8" />
                            </div>
                            <h4 className="text-xl font-bold text-slate-900 dark:text-white">Report Submitted</h4>
                            <p className="text-slate-500 mt-2">Thank you for helping keep the community safe.</p>
                        </div>
                    ) : (
                        <form onSubmit={handleSubmit} className="space-y-4">
                            <div className="p-3 bg-slate-100 dark:bg-white/5 rounded-lg text-sm text-slate-600 dark:text-slate-400">
                                Reporting <span className="font-bold text-slate-900 dark:text-white">{effectiveTitle}</span>
                            </div>

                            {error && (
                                <div className="p-3 bg-red-500/10 border border-red-500/20 text-red-500 text-sm rounded-lg flex items-center gap-2">
                                    <AlertTriangle className="w-4 h-4" /> {error}
                                </div>
                            )}

                            <div className="relative" ref={dropdownRef}>
                                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Reason</label>
                                <button
                                    type="button"
                                    onClick={() => setDropdownOpen(!dropdownOpen)}
                                    className="w-full p-3 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 text-slate-900 dark:text-white font-medium outline-none focus:ring-2 focus:ring-red-500 flex items-center justify-between transition-all"
                                >
                                    <span>{selectedLabel}</span>
                                    <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform duration-200 ${dropdownOpen ? 'rotate-180' : ''}`} />
                                </button>

                                {dropdownOpen && (
                                    <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl overflow-hidden z-50 animate-in fade-in slide-in-from-top-2 duration-200">
                                        {REPORT_REASONS.map(r => (
                                            <button
                                                key={r.id}
                                                type="button"
                                                onClick={() => {
                                                    setReason(r.id);
                                                    setDropdownOpen(false);
                                                }}
                                                className="w-full text-left px-4 py-3 hover:bg-slate-50 dark:hover:bg-white/5 flex items-center justify-between transition-colors border-b border-slate-100 dark:border-white/5 last:border-0"
                                            >
                                                <span className={`text-sm ${reason === r.id ? 'font-bold text-slate-900 dark:text-white' : 'text-slate-600 dark:text-slate-300'}`}>
                                                    {r.label}
                                                </span>
                                                {reason === r.id && <Check className="w-4 h-4 text-modtale-accent" />}
                                            </button>
                                        ))}
                                    </div>
                                )}
                            </div>

                            <div>
                                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Description</label>
                                <textarea
                                    value={description}
                                    onChange={e => setDescription(e.target.value)}
                                    placeholder="Please provide details about the issue..."
                                    className="w-full p-3 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 text-slate-900 dark:text-white font-medium outline-none focus:ring-2 focus:ring-red-500 min-h-[100px] resize-none"
                                    required
                                />
                            </div>

                            <div className="pt-2">
                                <button
                                    type="submit"
                                    disabled={loading}
                                    className="w-full py-3 bg-red-500 hover:bg-red-600 text-white rounded-xl font-bold transition-colors shadow-lg shadow-red-500/20 disabled:opacity-50 flex items-center justify-center gap-2"
                                >
                                    {loading ? 'Submitting...' : 'Submit Report'}
                                </button>
                            </div>
                        </form>
                    )}
                </div>
            </div>
        </div>
    );
};