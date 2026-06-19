import React, { useState, useEffect, useRef } from 'react';
import { Flag, X, AlertTriangle, ChevronDown, Check, ShieldCheck } from 'lucide-react';
import { projectClient } from '../../api/projectClient';
import { theme } from '@/styles/theme';
import { extractApiErrorMessage } from '@/utils/api';
import { useScrollLock } from '@/hooks/useScrollLock';
import { ModalPortal } from '@/components/ui/ModalPortal';

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
    const [reportId, setReportId] = useState<string | null>(null);
    const [error, setError] = useState('');
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);

    useScrollLock(isOpen);

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

    useEffect(() => {
        if (!isOpen) {
            setSubmitted(false);
            setReportId(null);
            setDescription('');
            setReason(REPORT_REASONS[0].id);
        }
    }, [isOpen]);

    if (!isOpen) return null;

    const effectiveTargetId = targetId || '';
    const effectiveTitle = targetTitle || (targetType === 'COMMENT' ? 'Comment' : (targetType === 'USER' ? 'User Profile' : 'Content'));

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!effectiveTargetId) {
            setError('We could not determine what you were trying to report. Please close this window and try again.');
            return;
        }
        setLoading(true);
        setError('');
        try {
            const data = await projectClient.submitReport(effectiveTargetId, targetType, reason, description);
            setReportId(data.id);
            setSubmitted(true);
        } catch (e: unknown) {
            setError(extractApiErrorMessage(e, 'We could not submit your report.'));
        } finally {
            setLoading(false);
        }
    };

    const selectedLabel = REPORT_REASONS.find(r => r.id === reason)?.label;

    return (
        <ModalPortal>
        <div className={theme.components.modalOverlay} onClick={onClose}>
            <div className={`${theme.components.modalContent} max-w-2xl w-full overflow-visible`} onClick={e => e.stopPropagation()}>
                <div className={theme.components.modalHeader}>
                    <h3 className={`font-bold ${theme.colors.textPrimary} flex items-center gap-2`}>
                        <Flag className="w-5 h-5 text-modtale-accent" />
                        Report {targetType === 'USER' ? 'User' : (targetType === 'COMMENT' ? 'Comment' : 'Project')}
                    </h3>
                    <button onClick={onClose} className={`p-2 ${theme.colors.bgSurfaceHover} rounded-full transition-colors`}><X className="w-4 h-4" /></button>
                </div>

                <div className="p-6">
                    {submitted ? (
                        <div className="text-center py-6">
                            <div className="w-16 h-16 bg-green-500/10 text-green-500 rounded-full flex items-center justify-center mx-auto mb-4">
                                <ShieldCheck className="w-8 h-8" />
                            </div>
                            <h4 className={`text-xl font-bold ${theme.colors.textPrimary}`}>Report Submitted</h4>
                            <p className={`${theme.colors.textMuted} mt-2 text-sm`}>Thank you for helping keep the community safe. A moderator will review this shortly.</p>
                            {reportId && (
                                <div className={`mt-6 p-4 ${theme.colors.bgSurface} border ${theme.colors.border} rounded-xl`}>
                                    <p className={`text-xs ${theme.colors.textMuted} uppercase font-bold tracking-wider mb-1`}>Report ID</p>
                                    <p className={`font-mono ${theme.colors.textPrimary} text-sm select-all`}>{reportId}</p>
                                </div>
                            )}
                            <button onClick={onClose} className={`mt-8 ${theme.components.buttonSecondary}`}>Close</button>
                        </div>
                    ) : (
                        <form onSubmit={handleSubmit} className="space-y-4">
                            <div className={`p-3 bg-blue-50/60 dark:bg-blue-500/10 border border-blue-200 dark:border-blue-500/20 rounded-lg text-sm ${theme.colors.textSecondary}`}>
                                Reporting <span className={`font-bold ${theme.colors.textPrimary}`}>{effectiveTitle}</span>
                            </div>

                            {error && (
                                <div className={`p-3 ${theme.colors.dangerBg} border ${theme.colors.dangerBorder} text-red-500 text-sm rounded-lg flex items-center gap-2`}>
                                    <AlertTriangle className="w-4 h-4" /> {error}
                                </div>
                            )}

                            <div className="relative" ref={dropdownRef}>
                                <label className={`block text-xs font-bold ${theme.colors.textSecondary} uppercase tracking-wider mb-2`}>Reason</label>
                                <button
                                    type="button"
                                    onClick={() => setDropdownOpen(!dropdownOpen)}
                                    className={`w-full p-3 rounded-xl ${theme.colors.bgSurface} border ${theme.colors.border} ${theme.colors.textPrimary} font-medium outline-none focus:ring-2 focus:ring-modtale-accent flex items-center justify-between transition-all`}
                                >
                                    <span>{selectedLabel}</span>
                                    <ChevronDown className={`w-4 h-4 ${theme.colors.textMuted} transition-transform duration-200 ${dropdownOpen ? 'rotate-180' : ''}`} />
                                </button>

                                {dropdownOpen && (
                                    <div className={`absolute top-full left-0 right-0 mt-2 ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-xl overflow-hidden z-50 animate-in fade-in slide-in-from-top-2 duration-200`}>
                                        {REPORT_REASONS.map(r => (
                                            <button
                                                key={r.id}
                                                type="button"
                                                onClick={() => { setReason(r.id); setDropdownOpen(false); }}
                                                className={`w-full text-left px-4 py-3 ${theme.colors.bgSurfaceHover} flex items-center justify-between transition-colors border-b ${theme.colors.borderFaint} last:border-0`}
                                            >
                                                <span className={`text-sm ${reason === r.id ? `font-bold ${theme.colors.textPrimary}` : theme.colors.textSecondary}`}>{r.label}</span>
                                                {reason === r.id && <Check className="w-4 h-4 text-modtale-accent" />}
                                            </button>
                                        ))}
                                    </div>
                                )}
                            </div>

                            <div>
                                <label className={`block text-xs font-bold ${theme.colors.textSecondary} uppercase tracking-wider mb-2`}>Description</label>
                                <textarea
                                    value={description}
                                    onChange={e => setDescription(e.target.value)}
                                    placeholder="Please provide details about the issue..."
                                    className={`w-full p-3 rounded-xl ${theme.colors.bgSurface} border ${theme.colors.border} ${theme.colors.textPrimary} font-medium outline-none focus:ring-2 focus:ring-modtale-accent min-h-[100px] resize-none`}
                                    required
                                />
                            </div>

                            <div className="pt-2">
                                <button type="submit" disabled={loading} className={`w-full py-3 ${theme.components.buttonPrimary} font-bold rounded-xl shadow-lg flex items-center justify-center gap-2`}>
                                    {loading ? 'Submitting...' : 'Submit Report'}
                                </button>
                            </div>
                        </form>
                    )}
                </div>
            </div>
        </div>
        </ModalPortal>
    );
};
