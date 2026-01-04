import React, { useState, useEffect, useMemo } from 'react';
import { api, API_BASE_URL, BACKEND_URL } from '../../utils/api';
import { StatusModal } from '../../components/ui/StatusModal';
import {
    Shield, Search, User as UserIcon, Check, X,
    FileText, ExternalLink, ArrowRight, ArrowLeft, Image as ImageIcon,
    Clock, Download, Box, List, Copy, LayoutGrid, AlertTriangle, Zap, Terminal, FileCode, ChevronRight, Folder
} from 'lucide-react';
import type { Mod } from '../../types';

interface AdminPanelProps {
    currentUser: any;
}

interface WizardStep {
    id: string;
    title: string;
    icon: React.ReactNode;
    rejectReasons: string[];
}

const WIZARD_STEPS: WizardStep[] = [
    {
        id: 'meta',
        title: 'Metadata',
        icon: <List className="w-4 h-4" />,
        rejectReasons: ["Title violates naming conventions", "Incorrect classification selected", "Tags are irrelevant or spam", "Slug/URL is invalid"]
    },
    {
        id: 'content',
        title: 'Content',
        icon: <FileText className="w-4 h-4" />,
        rejectReasons: ["Inappropriate imagery", "Description contains spam/links", "Low quality assets", "Insufficient description"]
    },
    {
        id: 'files',
        title: 'Files',
        icon: <Box className="w-4 h-4" />,
        rejectReasons: ["Malicious code detected", "Invalid file structure", "Broken or missing dependencies", "Version number mismatch"]
    },
    {
        id: 'author',
        title: 'Author',
        icon: <UserIcon className="w-4 h-4" />,
        rejectReasons: ["Suspicious account activity", "Impersonating another creator", "Bot-like behavior"]
    },
    {
        id: 'decision',
        title: 'Decision',
        icon: <Shield className="w-4 h-4" />,
        rejectReasons: ["General quality standards", "Duplicate project"]
    }
];

const CodeViewer: React.FC<{ content: any; filename: string }> = ({ content, filename }) => {
    let ext = filename.split('.').pop()?.toLowerCase();
    if (ext === 'class') ext = 'java';

    const scrollbarStyles = `
        .custom-scrollbar::-webkit-scrollbar { width: 8px; height: 8px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: #0f1117; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background: #334155; border-radius: 4px; border: 2px solid #0f1117; }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #475569; }
    `;

    const safeContent = useMemo(() => {
        if (content === null || content === undefined) return '';
        if (typeof content === 'object') {
            try { return JSON.stringify(content, null, 2); } catch (e) { return '[Object]'; }
        }
        return String(content);
    }, [content]);

    const highlightedCode = useMemo(() => {
        if (!safeContent) return '';

        const entityMap: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };

        let src = safeContent.replace(/[&<>"']/g, (m) => entityMap[m] || m);

        if (src.length > 50000) return src;

        const tokens: string[] = [];
        const saveToken = (text: string, type: string) => {
            tokens.push(`<span class="${type}">${text}</span>`);
            return `___TOKEN${tokens.length - 1}___`;
        };

        if (ext === 'java') {
            src = src.replace(/(&quot;(\\.|[^&"\\])*&quot;)/g, (m) => saveToken(m, 'text-emerald-400'));
            src = src.replace(/('(\\.|[^'\\])*')/g, (m) => saveToken(m, 'text-emerald-400'));
            src = src.replace(/(\/\/.*)/g, (m) => saveToken(m, 'text-slate-500 italic'));
            src = src.replace(/(\/\*[\s\S]*?\*\/)/g, (m) => saveToken(m, 'text-slate-500 italic'));

            src = src.replace(/(@\w+)/g, (m) => saveToken(m, 'text-yellow-400'));

            src = src.replace(/\b(0x[0-9a-fA-F]+|\d+L?|\d*\.\d+f?)\b/g, (m) => saveToken(m, 'text-blue-400'));

            const kwControl = "if|else|switch|case|default|break|continue|return|do|while|for|throw|try|catch|finally";
            const kwModifiers = "public|private|protected|static|final|abstract|synchronized|volatile|transient|native|strictfp";
            const kwTypes = "int|long|short|byte|float|double|char|boolean|void|class|interface|enum|var";
            const kwContext = "this|super|new|instanceof|extends|implements|import|package|throws";
            const kwConst = "null|true|false";

            const replaceKw = (text: string, pattern: string, style: string) => {
                return text.replace(new RegExp(`\\b(${pattern})\\b`, 'g'), (m) => saveToken(m, style));
            };

            src = replaceKw(src, kwControl, 'text-purple-400 font-bold');
            src = replaceKw(src, kwModifiers, 'text-blue-400 italic');
            src = replaceKw(src, kwTypes, 'text-orange-400');
            src = replaceKw(src, kwContext, 'text-cyan-400');
            src = replaceKw(src, kwConst, 'text-red-400 font-bold');

            src = src.replace(/\b([A-Z]\w*)\b/g, (m) => saveToken(m, 'text-yellow-200'));
        }
        else if (ext === 'json') {
            src = src.replace(/: (&quot;[^&]*&quot;)/g, (match, val) => `: ${saveToken(val, 'text-emerald-400')}`);
            src = src.replace(/(&quot;[^&]+&quot;)\s*:/g, (match, key) => `${saveToken(key, 'text-indigo-400')}:`);
            src = src.replace(/\b(true|false|null)\b/g, (m) => saveToken(m, 'text-red-400 font-bold'));
            src = src.replace(/\b(\d+(\.\d+)?)\b/g, (m) => saveToken(m, 'text-blue-400'));
        }
        else if (ext === 'yaml' || ext === 'yml') {
            src = src.replace(/(#.*)/g, (m) => saveToken(m, 'text-slate-500 italic'));
            src = src.replace(/^(\s*)([\w.-]+):/gm, (match, space, key) => `${space}${saveToken(key, 'text-indigo-400')}:`);
            src = src.replace(/: (.+)/g, (match, val) => `: ${saveToken(val, 'text-emerald-400')}`);
        }

        tokens.forEach((html, i) => {
            src = src.split(`___TOKEN${i}___`).join(html);
        });

        return src;
    }, [safeContent, ext]);

    return (
        <>
            <style>{scrollbarStyles}</style>
            <pre className="font-mono text-xs text-slate-300 leading-relaxed whitespace-pre tab-4 p-4 overflow-auto h-full custom-scrollbar">
                <code dangerouslySetInnerHTML={{ __html: highlightedCode || safeContent }} />
            </pre>
        </>
    );
};

export const AdminPanel: React.FC<AdminPanelProps> = ({ currentUser }) => {
    const [activeTab, setActiveTab] = useState<'users' | 'verification'>('verification');
    const [status, setStatus] = useState<any>(null);

    const [username, setUsername] = useState('');
    const [loading, setLoading] = useState(false);
    const [foundUser, setFoundUser] = useState<any>(null);

    const [pendingProjects, setPendingProjects] = useState<Mod[]>([]);
    const [loadingQueue, setLoadingQueue] = useState(false);

    const [reviewingProject, setReviewingProject] = useState<any>(null);
    const [loadingReview, setLoadingReview] = useState(false);
    const [currentStep, setCurrentStep] = useState(0);
    const [checklist, setChecklist] = useState<Record<string, boolean>>({});

    const [rejectReason, setRejectReason] = useState('');
    const [showRejectPanel, setShowRejectPanel] = useState(false);

    const [depMeta, setDepMeta] = useState<Record<string, { icon: string, title: string }>>({});

    const [inspectorData, setInspectorData] = useState<{ version: string, structure: string[] } | null>(null);
    const [inspectorFile, setInspectorFile] = useState<string | null>(null);
    const [inspectorContent, setInspectorContent] = useState<any>('');
    const [loadingInspector, setLoadingInspector] = useState(false);
    const [loadingFile, setLoadingFile] = useState(false);
    const [fileSearch, setFileSearch] = useState('');

    const isAdmin = currentUser?.roles?.includes('ADMIN') || currentUser?.username === 'Villagers654';
    const isSuperAdmin = currentUser?.username === 'Villagers654';

    useEffect(() => {
        if (activeTab === 'verification' && isAdmin) {
            fetchQueue();
        }
    }, [activeTab, isAdmin]);

    useEffect(() => {
        if (!reviewingProject?.mod?.versions?.[0]?.dependencies) return;

        const deps = reviewingProject.mod.versions[0].dependencies;
        const fetchMeta = async () => {
            const newMeta = { ...depMeta };
            await Promise.all(deps.map(async (d: any) => {
                if (newMeta[d.modId]) return;
                try {
                    const res = await api.get(`/projects/${d.modId}/meta`);
                    newMeta[d.modId] = {
                        icon: res.data.icon,
                        title: res.data.title
                    };
                } catch (e) {
                    newMeta[d.modId] = { icon: '', title: d.modTitle || d.modId };
                }
            }));
            setDepMeta(newMeta);
        };
        fetchMeta();
    }, [reviewingProject]);

    const fetchQueue = async () => {
        setLoadingQueue(true);
        try {
            const res = await api.get('/admin/verification/queue');
            setPendingProjects(res.data);
        } catch (e) {
            console.error(e);
        } finally {
            setLoadingQueue(false);
        }
    };

    const fetchProjectDetails = async (id: string) => {
        setLoadingReview(true);
        setCurrentStep(0);
        setChecklist({});
        setRejectReason('');
        setShowRejectPanel(false);
        setInspectorData(null);
        try {
            const res = await api.get(`/admin/projects/${id}/review-details`);
            setReviewingProject(res.data);
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Could not load project details' });
        } finally {
            setLoadingReview(false);
        }
    };

    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setFoundUser(null);
        try {
            const res = await api.get(`/user/profile/${username}`);
            setFoundUser(res.data);
        } catch (e) {
            setStatus({ type: 'error', title: 'User Not Found', msg: `Could not find user "${username}"` });
        } finally {
            setLoading(false);
        }
    };

    const handleUpdateTier = async (newTier: 'USER' | 'ENTERPRISE') => {
        if (!foundUser) return;
        setLoading(true);
        try {
            const formData = new FormData();
            formData.append('tier', newTier);
            await api.post(`/admin/users/${foundUser.username}/tier`, formData);
            setStatus({ type: 'success', title: 'Tier Updated', msg: `Successfully changed ${foundUser.username} to ${newTier}.` });
            setFoundUser({ ...foundUser, tier: newTier });
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Update Failed', msg: e.response?.data?.message || 'Server error occurred.' });
        } finally {
            setLoading(false);
        }
    };

    const handleToggleAdmin = async () => {
        if (!foundUser) return;
        setLoading(true);
        const hasAdmin = foundUser.roles && foundUser.roles.includes('ADMIN');

        try {
            if (hasAdmin) {
                await api.delete(`/admin/users/${foundUser.username}/role`, { params: { role: 'ADMIN' } });
                const roles = foundUser.roles.filter((r: string) => r !== 'ADMIN');
                setFoundUser({...foundUser, roles});
                setStatus({ type: 'info', title: 'Role Updated', msg: `Admin role revoked from ${foundUser.username}.` });
            } else {
                await api.post(`/admin/users/${foundUser.username}/role`, null, { params: { role: 'ADMIN' } });
                const roles = foundUser.roles || [];
                roles.push('ADMIN');
                setFoundUser({...foundUser, roles});
                setStatus({ type: 'success', title: 'Role Updated', msg: `Admin role granted to ${foundUser.username}.` });
            }
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Update Failed', msg: e.response?.data || 'Server error occurred.' });
        } finally {
            setLoading(false);
        }
    };

    const handleApprove = async () => {
        if (!reviewingProject) return;
        try {
            await api.post(`/projects/${reviewingProject.mod.id}/publish`);
            setStatus({ type: 'success', title: 'Approved', msg: 'Project published successfully.' });
            setReviewingProject(null);
            fetchQueue();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data || 'Failed to approve.' });
        }
    };

    const handleReject = async () => {
        if (!reviewingProject) return;
        try {
            await api.post(`/admin/projects/${reviewingProject.mod.id}/reject`, { reason: rejectReason });
            setStatus({ type: 'info', title: 'Rejected', msg: 'Project returned to drafts.' });
            setReviewingProject(null);
            fetchQueue();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data || 'Failed to reject.' });
        }
    };

    const openInspector = async (version: string) => {
        if (!reviewingProject) return;
        setLoadingInspector(true);
        setInspectorFile(null);
        setInspectorContent('');
        setFileSearch('');
        try {
            const res = await api.get(`/admin/projects/${reviewingProject.mod.id}/versions/${version}/structure`);
            setInspectorData({ version, structure: res.data });
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to inspect JAR structure.' });
        } finally {
            setLoadingInspector(false);
        }
    };

    const loadInspectorFile = async (path: string) => {
        if (!inspectorData || !reviewingProject) return;
        setInspectorFile(path);
        setLoadingFile(true);
        try {
            const res = await api.get(`/admin/projects/${reviewingProject.mod.id}/versions/${inspectorData.version}/file`, { params: { path } });
            setInspectorContent(res.data);
        } catch (e) {
            setInspectorContent('// Error loading file content.');
        } finally {
            setLoadingFile(false);
        }
    };

    const toggleCheck = (id: string) => {
        setChecklist(prev => ({ ...prev, [id]: !prev[id] }));
    };

    const canProceed = () => {
        switch(currentStep) {
            case 0: return checklist['title'] && checklist['tags'] && checklist['class'];
            case 1: return checklist['desc'] && checklist['images'];
            case 2: return checklist['download'] && checklist['manual_check'];
            case 3: return checklist['author'];
            default: return true;
        }
    };

    const getIconUrl = (path?: string) => {
        if (!path) return null;
        return path.startsWith('http') ? path : `${BACKEND_URL}${path}`;
    };

    const filteredFiles = inspectorData?.structure.filter(f => f.toLowerCase().includes(fileSearch.toLowerCase())) || [];

    if (!currentUser || !isAdmin) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-modtale-dark">
                <div className="text-center p-8 bg-white dark:bg-slate-900 rounded-3xl border border-slate-200 dark:border-white/5 shadow-xl">
                    <Shield className="w-12 h-12 text-red-500 mx-auto mb-4" />
                    <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Access Denied</h1>
                    <p className="text-slate-500">You do not have permission to view this page.</p>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark pb-20 font-sans">
            {status && <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />}

            {inspectorData && (
                <div className="fixed inset-0 z-[160] bg-slate-950/90 backdrop-blur-md flex flex-col animate-in fade-in duration-200">
                    <div className="h-14 border-b border-white/10 bg-slate-900 flex items-center justify-between px-4 shrink-0">
                        <div className="flex items-center gap-4">
                            <FileCode className="w-5 h-5 text-indigo-400" />
                            <div>
                                <h3 className="text-sm font-bold text-white">Source Inspector</h3>
                                <p className="text-[10px] text-slate-400 font-mono">{reviewingProject?.mod?.id} @ {inspectorData.version}</p>
                            </div>
                        </div>
                        <button onClick={() => setInspectorData(null)} className="p-2 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white">
                            <X className="w-5 h-5" />
                        </button>
                    </div>
                    <div className="flex-1 flex overflow-hidden">
                        <div className="w-80 bg-slate-950 border-r border-white/10 flex flex-col">
                            <div className="p-3 border-b border-white/10">
                                <div className="relative">
                                    <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-500" />
                                    <input
                                        type="text"
                                        placeholder="Search files..."
                                        className="w-full pl-9 pr-4 py-2 bg-white/5 border border-white/10 rounded-lg text-sm text-white placeholder:text-slate-500 focus:ring-1 focus:ring-indigo-500 outline-none"
                                        value={fileSearch}
                                        onChange={e => setFileSearch(e.target.value)}
                                    />
                                </div>
                            </div>
                            <div className="flex-1 overflow-y-auto p-2 custom-scrollbar">
                                {filteredFiles.map((file, idx) => (
                                    <button
                                        key={idx}
                                        onClick={() => loadInspectorFile(file)}
                                        className={`w-full text-left px-3 py-2 rounded-lg text-xs font-mono truncate flex items-center gap-2 transition-colors ${inspectorFile === file ? 'bg-indigo-500/20 text-indigo-300' : 'text-slate-400 hover:bg-white/5 hover:text-white'}`}
                                    >
                                        {file.endsWith('.class') ? <FileCode className="w-3 h-3 shrink-0" /> : <FileText className="w-3 h-3 shrink-0" />}
                                        {file}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="flex-1 bg-[#0d1117] overflow-hidden flex flex-col">
                            {loadingFile ? (
                                <div className="flex h-full items-center justify-center text-slate-500 gap-2">
                                    <div className="animate-spin w-4 h-4 border-2 border-indigo-500 border-t-transparent rounded-full"></div>
                                    Decompiling...
                                </div>
                            ) : inspectorFile ? (
                                <CodeViewer content={inspectorContent} filename={inspectorFile} />
                            ) : (
                                <div className="flex h-full items-center justify-center text-slate-600 flex-col gap-4">
                                    <Terminal className="w-12 h-12 opacity-50" />
                                    <p>Select a file to inspect its contents</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {reviewingProject && (
                <div className="fixed inset-0 z-[100] bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 md:p-8 animate-in fade-in duration-200">
                    <div className="bg-slate-50 dark:bg-slate-900 w-full max-w-7xl h-[85vh] rounded-3xl shadow-2xl border border-slate-200 dark:border-white/5 flex overflow-hidden ring-1 ring-white/10 relative">

                        {showRejectPanel && (
                            <div className="absolute inset-0 z-[150] bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
                                <div className="bg-white dark:bg-slate-900 w-full max-w-lg p-6 rounded-3xl shadow-2xl border border-slate-200 dark:border-white/10">
                                    <div className="flex justify-between items-center mb-6">
                                        <h3 className="text-xl font-black text-slate-900 dark:text-white flex items-center gap-2">
                                            <Shield className="w-6 h-6 text-red-500" />
                                            Confirm Rejection
                                        </h3>
                                        <button onClick={() => setShowRejectPanel(false)} className="p-2 hover:bg-slate-100 dark:hover:bg-white/10 rounded-full transition-colors">
                                            <X className="w-5 h-5 text-slate-500" />
                                        </button>
                                    </div>

                                    <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">Quick Reasons (Step: {WIZARD_STEPS[currentStep].title})</h4>
                                    <div className="flex flex-wrap gap-2 mb-6">
                                        {WIZARD_STEPS[currentStep].rejectReasons.map(reason => (
                                            <button
                                                key={reason}
                                                onClick={() => setRejectReason(reason)}
                                                className="text-xs bg-slate-100 dark:bg-white/5 hover:bg-red-500 hover:text-white px-3 py-2 rounded-xl transition-colors text-left font-medium border border-slate-200 dark:border-white/5"
                                            >
                                                {reason}
                                            </button>
                                        ))}
                                    </div>

                                    <textarea
                                        value={rejectReason}
                                        onChange={e => setRejectReason(e.target.value)}
                                        placeholder="Enter specific reason for rejection..."
                                        className="w-full h-32 p-4 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 text-sm focus:ring-2 focus:ring-red-500 outline-none font-medium mb-6 resize-none"
                                    />

                                    <div className="flex gap-3">
                                        <button
                                            onClick={() => setShowRejectPanel(false)}
                                            className="flex-1 py-3 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-slate-600 dark:text-slate-300 rounded-xl font-bold transition-colors"
                                        >
                                            Cancel
                                        </button>
                                        <button
                                            onClick={handleReject}
                                            disabled={!rejectReason}
                                            className="flex-1 py-3 bg-red-500 hover:bg-red-600 disabled:opacity-50 text-white rounded-xl font-bold transition-colors shadow-lg shadow-red-500/20"
                                        >
                                            Reject Project
                                        </button>
                                    </div>
                                </div>
                            </div>
                        )}

                        <div className="w-64 bg-slate-100 dark:bg-slate-950/50 border-r border-slate-200 dark:border-white/5 p-6 flex flex-col">
                            <h2 className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-6">Review Process</h2>
                            <div className="space-y-2 flex-1">
                                {WIZARD_STEPS.map((step, idx) => (
                                    <div
                                        key={step.id}
                                        className={`flex items-center gap-3 p-3 rounded-xl text-sm font-bold transition-all ${
                                            idx === currentStep
                                                ? 'bg-modtale-accent text-white shadow-lg shadow-modtale-accent/20'
                                                : idx < currentStep
                                                    ? 'text-emerald-500 bg-emerald-500/10'
                                                    : 'text-slate-500 dark:text-slate-400'
                                        }`}
                                    >
                                        {idx < currentStep ? <Check className="w-4 h-4" /> : step.icon}
                                        {step.title}
                                    </div>
                                ))}
                            </div>
                            <button onClick={() => setReviewingProject(null)} className="mt-auto flex items-center gap-2 text-slate-500 hover:text-slate-900 dark:hover:text-white px-3 py-2 text-sm font-bold transition-colors">
                                <ArrowLeft className="w-4 h-4" /> Exit Review
                            </button>
                        </div>

                        <div className="flex-1 flex flex-col overflow-hidden relative bg-white dark:bg-transparent">
                            <div className="p-6 border-b border-slate-200 dark:border-white/5 flex justify-between items-center bg-slate-50/50 dark:bg-slate-900/50 backdrop-blur-md">
                                <div>
                                    <h1 className="text-2xl font-black text-slate-900 dark:text-white tracking-tight">{reviewingProject.mod.title}</h1>
                                    <div className="flex items-center gap-2 mt-1">
                                        <span className="text-xs font-mono text-slate-400">{reviewingProject.mod.id}</span>
                                        <button onClick={() => navigator.clipboard.writeText(reviewingProject.mod.id)} className="text-slate-400 hover:text-modtale-accent"><Copy className="w-3 h-3" /></button>
                                    </div>
                                </div>
                                <div className="flex gap-3">
                                    <a href={`/mod/${reviewingProject.mod.id}`} target="_blank" rel="noreferrer" className="px-4 py-2 bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-300 rounded-xl text-sm font-bold flex items-center gap-2 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors border border-slate-200 dark:border-white/5">
                                        <ExternalLink className="w-4 h-4" /> View Live
                                    </a>
                                </div>
                            </div>

                            <div className="flex-1 overflow-y-auto p-8 custom-scrollbar">
                                {currentStep === 0 && (
                                    <div className="max-w-3xl mx-auto space-y-8 animate-in slide-in-from-right-4 duration-300">
                                        <div className="grid grid-cols-2 gap-6">
                                            <div className="p-5 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Title</label>
                                                <p className="font-bold text-lg dark:text-white mt-1">{reviewingProject.mod.title}</p>
                                            </div>
                                            <div className="p-5 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Classification</label>
                                                <p className="font-bold text-lg dark:text-white mt-1">{reviewingProject.mod.classification}</p>
                                            </div>
                                        </div>

                                        <div className="p-5 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                            <label className="text-xs font-bold text-slate-400 uppercase block mb-3 tracking-wider">Tags</label>
                                            <div className="flex flex-wrap gap-2">
                                                {reviewingProject.mod.tags?.map((t: string) => (
                                                    <span key={t} className="px-3 py-1.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-lg text-sm font-bold text-slate-600 dark:text-slate-300 shadow-sm">
                                                        {t}
                                                    </span>
                                                ))}
                                            </div>
                                        </div>

                                        <div className="space-y-3 pt-6 border-t border-slate-200 dark:border-white/5">
                                            <label className="flex items-center gap-4 p-4 rounded-xl border border-slate-200 dark:border-white/10 cursor-pointer hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group">
                                                <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${checklist['title'] ? 'bg-modtale-accent border-modtale-accent' : 'border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                                    {checklist['title'] && <Check className="w-4 h-4 text-white" />}
                                                </div>
                                                <input type="checkbox" checked={!!checklist['title']} onChange={() => toggleCheck('title')} className="hidden" />
                                                <span className="font-bold text-slate-700 dark:text-slate-200">Title is appropriate</span>
                                            </label>
                                            <label className="flex items-center gap-4 p-4 rounded-xl border border-slate-200 dark:border-white/10 cursor-pointer hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group">
                                                <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${checklist['class'] ? 'bg-modtale-accent border-modtale-accent' : 'border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                                    {checklist['class'] && <Check className="w-4 h-4 text-white" />}
                                                </div>
                                                <input type="checkbox" checked={!!checklist['class']} onChange={() => toggleCheck('class')} className="hidden" />
                                                <span className="font-bold text-slate-700 dark:text-slate-200">Classification is correct</span>
                                            </label>
                                            <label className="flex items-center gap-4 p-4 rounded-xl border border-slate-200 dark:border-white/10 cursor-pointer hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group">
                                                <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${checklist['tags'] ? 'bg-modtale-accent border-modtale-accent' : 'border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                                    {checklist['tags'] && <Check className="w-4 h-4 text-white" />}
                                                </div>
                                                <input type="checkbox" checked={!!checklist['tags']} onChange={() => toggleCheck('tags')} className="hidden" />
                                                <span className="font-bold text-slate-700 dark:text-slate-200">Tags are relevant</span>
                                            </label>
                                        </div>
                                    </div>
                                )}

                                {currentStep === 1 && (
                                    <div className="max-w-3xl mx-auto space-y-8 animate-in slide-in-from-right-4 duration-300">
                                        <div className="flex gap-6 p-5 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                            <img src={reviewingProject.mod.imageUrl} className="w-24 h-24 rounded-xl object-cover bg-slate-200 dark:bg-white/10" />
                                            <div className="flex-1">
                                                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Short Description</label>
                                                <p className="text-lg text-slate-700 dark:text-slate-200 font-medium leading-relaxed mt-1">{reviewingProject.mod.description}</p>
                                            </div>
                                        </div>

                                        <div className="bg-slate-50 dark:bg-white/5 p-6 rounded-2xl border border-slate-200 dark:border-white/5">
                                            <label className="text-xs font-bold text-slate-400 uppercase block mb-4 tracking-wider">Long Description Preview</label>
                                            <div className="prose dark:prose-invert max-w-none text-sm prose-p:text-slate-600 dark:prose-p:text-slate-300">
                                                <div className="whitespace-pre-wrap">{reviewingProject.mod.about}</div>
                                            </div>
                                        </div>

                                        {reviewingProject.mod.gallery?.length > 0 && (
                                            <div className="grid grid-cols-3 gap-4">
                                                {reviewingProject.mod.gallery.map((url: string, i: number) => (
                                                    <div key={i} className="aspect-video rounded-xl overflow-hidden border border-slate-200 dark:border-white/10">
                                                        <img src={url} className="w-full h-full object-cover" />
                                                    </div>
                                                ))}
                                            </div>
                                        )}

                                        <div className="space-y-3 pt-6 border-t border-slate-200 dark:border-white/5">
                                            <label className="flex items-center gap-4 p-4 rounded-xl border border-slate-200 dark:border-white/10 cursor-pointer hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group">
                                                <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${checklist['images'] ? 'bg-modtale-accent border-modtale-accent' : 'border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                                    {checklist['images'] && <Check className="w-4 h-4 text-white" />}
                                                </div>
                                                <input type="checkbox" checked={!!checklist['images']} onChange={() => toggleCheck('images')} className="hidden" />
                                                <span className="font-bold text-slate-700 dark:text-slate-200">Images are appropriate</span>
                                            </label>
                                            <label className="flex items-center gap-4 p-4 rounded-xl border border-slate-200 dark:border-white/10 cursor-pointer hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group">
                                                <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${checklist['desc'] ? 'bg-modtale-accent border-modtale-accent' : 'border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                                    {checklist['desc'] && <Check className="w-4 h-4 text-white" />}
                                                </div>
                                                <input type="checkbox" checked={!!checklist['desc']} onChange={() => toggleCheck('desc')} className="hidden" />
                                                <span className="font-bold text-slate-700 dark:text-slate-200">Description is clean</span>
                                            </label>
                                        </div>
                                    </div>
                                )}

                                {currentStep === 2 && (
                                    <div className="max-w-3xl mx-auto space-y-8 animate-in slide-in-from-right-4 duration-300">
                                        <div className="p-5 bg-amber-500/10 border border-amber-500/20 rounded-2xl flex items-start gap-4">
                                            <AlertTriangle className="w-6 h-6 text-amber-500 shrink-0 mt-0.5" />
                                            <div>
                                                <h4 className="font-bold text-amber-500 mb-1">Manual Verification Required</h4>
                                                <p className="text-sm text-amber-600/80 dark:text-amber-500/70 font-medium">
                                                    You must manually verify the file structure. No automated scanning is performed.
                                                    Dependencies are external projects and are assumed safe, but you should verify they exist.
                                                </p>
                                            </div>
                                        </div>

                                        <div className="space-y-3">
                                            {reviewingProject.mod.versions?.map((v: any) => (
                                                <div key={v.id} className="p-4 bg-white dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/10 flex items-center justify-between group hover:border-modtale-accent/30 transition-colors">
                                                    <div>
                                                        <div className="flex items-center gap-3">
                                                            <span className="font-black text-lg text-slate-900 dark:text-white">{v.versionNumber}</span>
                                                            <span className={`text-[10px] uppercase font-bold px-2 py-0.5 rounded ${v.channel === 'RELEASE' ? 'bg-emerald-500/10 text-emerald-500' : 'bg-amber-500/10 text-amber-500'}`}>{v.channel}</span>
                                                        </div>
                                                        <p className="text-xs text-slate-400 mt-1 font-bold">Game Versions: <span className="text-slate-600 dark:text-slate-300">{v.gameVersions?.join(', ')}</span></p>
                                                    </div>
                                                    <div className="flex gap-2">
                                                        {reviewingProject.mod.classification !== 'MODPACK' && (
                                                            <button
                                                                onClick={() => openInspector(v.versionNumber)}
                                                                disabled={loadingInspector}
                                                                className="px-5 py-2.5 bg-indigo-500/10 hover:bg-indigo-500 hover:text-white text-indigo-500 rounded-xl text-sm font-bold flex items-center gap-2 transition-all border border-indigo-500/20"
                                                            >
                                                                {loadingInspector && inspectorData?.version === v.versionNumber ? 'Loading...' : <><Terminal className="w-4 h-4" /> Inspect Source</>}
                                                            </button>
                                                        )}
                                                        <a
                                                            href={`${API_BASE_URL}/projects/${reviewingProject.mod.id}/versions/${v.versionNumber}/download`}
                                                            className="px-5 py-2.5 bg-slate-100 dark:bg-white/10 hover:bg-modtale-accent hover:text-white text-slate-600 dark:text-slate-300 rounded-xl text-sm font-bold flex items-center gap-2 transition-all"
                                                            target="_blank" rel="noreferrer"
                                                        >
                                                            <Download className="w-4 h-4" /> Download
                                                        </a>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>

                                        <div className="p-6 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                            <label className="text-xs font-bold text-slate-400 uppercase block mb-4 tracking-wider">Dependencies</label>
                                            {reviewingProject.mod.versions?.[0]?.dependencies?.length > 0 ? (
                                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                                    {reviewingProject.mod.versions[0].dependencies.map((d: any) => {
                                                        const meta = depMeta[d.modId];
                                                        return (
                                                            <a
                                                                key={d.modId}
                                                                href={`/mod/${d.modId}`}
                                                                target="_blank"
                                                                rel="noreferrer"
                                                                className="flex items-center gap-3 p-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl hover:border-modtale-accent/50 transition-colors group"
                                                            >
                                                                <div className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-white/10 flex items-center justify-center shrink-0">
                                                                    {getIconUrl(meta?.icon) ? (
                                                                        <img src={getIconUrl(meta.icon)!} className="w-full h-full rounded-lg object-cover" />
                                                                    ) : (
                                                                        <Box className="w-4 h-4 text-slate-400" />
                                                                    )}
                                                                </div>
                                                                <div className="min-w-0">
                                                                    <div className="text-sm font-bold text-slate-700 dark:text-slate-300 truncate group-hover:text-modtale-accent">
                                                                        {meta?.title || d.modId}
                                                                    </div>
                                                                    <div className="text-[10px] text-slate-400 font-mono">{d.modId}</div>
                                                                </div>
                                                                <ExternalLink className="w-3 h-3 text-slate-400 ml-auto opacity-0 group-hover:opacity-100 transition-opacity" />
                                                            </a>
                                                        );
                                                    })}
                                                </div>
                                            ) : (
                                                <p className="text-sm text-slate-400 italic font-medium">No dependencies listed.</p>
                                            )}
                                        </div>

                                        <div className="space-y-3 pt-6 border-t border-slate-200 dark:border-white/5">
                                            <label className="flex items-center gap-4 p-4 rounded-xl border border-slate-200 dark:border-white/10 cursor-pointer hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group">
                                                <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${checklist['download'] ? 'bg-modtale-accent border-modtale-accent' : 'border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                                    {checklist['download'] && <Check className="w-4 h-4 text-white" />}
                                                </div>
                                                <input type="checkbox" checked={!!checklist['download']} onChange={() => toggleCheck('download')} className="hidden" />
                                                <span className="font-bold text-slate-700 dark:text-slate-200">File structure verified manually</span>
                                            </label>
                                            <label className="flex items-center gap-4 p-4 rounded-xl border border-slate-200 dark:border-white/10 cursor-pointer hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group">
                                                <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${checklist['manual_check'] ? 'bg-modtale-accent border-modtale-accent' : 'border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                                    {checklist['manual_check'] && <Check className="w-4 h-4 text-white" />}
                                                </div>
                                                <input type="checkbox" checked={!!checklist['manual_check']} onChange={() => toggleCheck('manual_check')} className="hidden" />
                                                <span className="font-bold text-slate-700 dark:text-slate-200">No malicious code found</span>
                                            </label>
                                        </div>
                                    </div>
                                )}

                                {currentStep === 3 && (
                                    <div className="max-w-3xl mx-auto space-y-8 animate-in slide-in-from-right-4 duration-300">
                                        <div className="p-8 bg-white dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 flex items-center gap-8">
                                            <div className="w-24 h-24 bg-slate-100 dark:bg-white/10 rounded-2xl flex items-center justify-center overflow-hidden shrink-0">
                                                {reviewingProject.authorStats?.avatarUrl ? (
                                                    <img src={reviewingProject.authorStats.avatarUrl} className="w-full h-full object-cover" />
                                                ) : (
                                                    <UserIcon className="w-10 h-10 text-slate-400" />
                                                )}
                                            </div>
                                            <div className="flex-1 grid grid-cols-2 gap-y-6 gap-x-8">
                                                <div>
                                                    <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Username</label>
                                                    <a href={`/creator/${reviewingProject.mod.author}`} target="_blank" rel="noreferrer" className="flex items-center gap-2 group">
                                                        <p className="font-black text-2xl dark:text-white mt-1 group-hover:underline">{reviewingProject.mod.author}</p>
                                                        <ExternalLink className="w-4 h-4 text-slate-400" />
                                                    </a>
                                                </div>
                                                <div>
                                                    <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Joined</label>
                                                    <p className="font-mono text-sm dark:text-slate-300 mt-2">{reviewingProject.authorStats?.accountAge}</p>
                                                </div>
                                                <div className="col-span-2">
                                                    <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Total Projects</label>
                                                    <p className="font-black text-2xl text-modtale-accent mt-1">{reviewingProject.authorStats?.totalProjects}</p>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="space-y-3 pt-6 border-t border-slate-200 dark:border-white/5">
                                            <label className="flex items-center gap-4 p-4 rounded-xl border border-slate-200 dark:border-white/10 cursor-pointer hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group">
                                                <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${checklist['author'] ? 'bg-modtale-accent border-modtale-accent' : 'border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                                    {checklist['author'] && <Check className="w-4 h-4 text-white" />}
                                                </div>
                                                <input type="checkbox" checked={!!checklist['author']} onChange={() => toggleCheck('author')} className="hidden" />
                                                <span className="font-bold text-slate-700 dark:text-slate-200">Author appears legitimate (not a bot/impersonator)</span>
                                            </label>
                                        </div>
                                    </div>
                                )}

                                {currentStep === 4 && (
                                    <div className="max-w-xl mx-auto text-center animate-in slide-in-from-bottom-8 duration-500">
                                        <div className="w-24 h-24 bg-emerald-500/10 rounded-full flex items-center justify-center mx-auto mb-8 shadow-xl shadow-emerald-500/10">
                                            <Shield className="w-12 h-12 text-emerald-500" />
                                        </div>
                                        <h2 className="text-4xl font-black text-slate-900 dark:text-white mb-4 tracking-tight">Ready to Publish?</h2>
                                        <p className="text-slate-500 dark:text-slate-400 font-medium mb-10 text-lg leading-relaxed">
                                            You have manually verified the files, content, and author details.
                                            Approving will make this project publicly visible immediately.
                                        </p>

                                        <div className="flex flex-col gap-4">
                                            <button
                                                onClick={handleApprove}
                                                className="w-full py-4 bg-emerald-500 hover:bg-emerald-600 text-white rounded-2xl font-black text-lg shadow-xl shadow-emerald-500/20 transition-all transform hover:scale-[1.02] active:scale-95"
                                            >
                                                Approve & Publish Project
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </div>

                            <div className="p-6 border-t border-slate-200 dark:border-white/5 bg-slate-50 dark:bg-slate-900 flex justify-between items-center relative z-20">
                                <button
                                    onClick={() => setCurrentStep(Math.max(0, currentStep - 1))}
                                    disabled={currentStep === 0}
                                    className="px-6 py-3 rounded-xl font-bold text-slate-500 hover:bg-slate-200 dark:hover:bg-white/5 disabled:opacity-30 transition-colors"
                                >
                                    Back
                                </button>

                                <div className="flex gap-4">
                                    <button
                                        onClick={() => setShowRejectPanel(true)}
                                        className="px-6 py-3 bg-red-500/10 hover:bg-red-500 text-red-500 hover:text-white rounded-xl font-bold transition-colors"
                                    >
                                        Reject...
                                    </button>

                                    {currentStep < 4 && (
                                        <button
                                            onClick={() => setCurrentStep(Math.min(4, currentStep + 1))}
                                            disabled={!canProceed()}
                                            className="px-8 py-3 bg-modtale-accent text-white rounded-xl font-bold disabled:opacity-50 disabled:cursor-not-allowed hover:bg-modtale-accentHover transition-colors flex items-center gap-2 shadow-lg shadow-modtale-accent/20"
                                        >
                                            Next Step <ArrowRight className="w-4 h-4" />
                                        </button>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            <div className="max-w-7xl mx-auto px-4 py-12">
                <div className="flex items-center gap-6 mb-12">
                    <div className="bg-gradient-to-br from-modtale-accent to-purple-600 p-5 rounded-3xl shadow-2xl shadow-modtale-accent/20">
                        <Shield className="w-10 h-10 text-white" />
                    </div>
                    <div>
                        <h1 className="text-5xl font-black text-slate-900 dark:text-white tracking-tighter">Admin Console</h1>
                        <p className="text-slate-500 dark:text-slate-400 font-bold text-lg mt-2">Platform management & verification</p>
                    </div>
                </div>

                <div className="flex gap-2 mb-8 border-b border-slate-200 dark:border-white/5">
                    <button
                        onClick={() => setActiveTab('verification')}
                        className={`pb-4 px-6 font-black text-sm tracking-wide border-b-2 transition-all ${activeTab === 'verification' ? 'border-modtale-accent text-modtale-accent' : 'border-transparent text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'}`}
                    >
                        VERIFICATION QUEUE {pendingProjects.length > 0 && <span className="ml-2 bg-red-500 text-white text-[10px] px-2 py-0.5 rounded-full shadow-lg shadow-red-500/30">{pendingProjects.length}</span>}
                    </button>
                    {isSuperAdmin && (
                        <button
                            onClick={() => setActiveTab('users')}
                            className={`pb-4 px-6 font-black text-sm tracking-wide border-b-2 transition-all ${activeTab === 'users' ? 'border-modtale-accent text-modtale-accent' : 'border-transparent text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'}`}
                        >
                            USER MANAGEMENT
                        </button>
                    )}
                </div>

                {activeTab === 'verification' && (
                    <div className="space-y-4">
                        {loadingQueue ? (
                            <div className="text-center py-24 text-slate-400 font-bold animate-pulse">Loading queue...</div>
                        ) : pendingProjects.length === 0 ? (
                            <div className="text-center py-32 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-200 dark:border-white/5 shadow-sm">
                                <div className="w-20 h-20 bg-emerald-500/10 rounded-full flex items-center justify-center mx-auto mb-6">
                                    <Check className="w-10 h-10 text-emerald-500" />
                                </div>
                                <h3 className="text-2xl font-black text-slate-900 dark:text-white mb-2">All Caught Up!</h3>
                                <p className="text-slate-500 font-medium">No projects pending verification.</p>
                            </div>
                        ) : (
                            <div className="grid gap-4">
                                {pendingProjects.map(p => (
                                    <div key={p.id} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-3xl p-6 flex flex-col md:flex-row gap-8 hover:shadow-xl transition-all duration-300 group hover:border-modtale-accent/20">
                                        <div className="w-full md:w-32 h-32 rounded-2xl overflow-hidden bg-slate-100 dark:bg-white/5 relative shrink-0 shadow-inner">
                                            <img src={p.imageUrl} className="w-full h-full object-cover" alt="" onError={(e) => e.currentTarget.src = '/assets/favicon.svg'} />
                                        </div>
                                        <div className="flex-1 min-w-0 py-1 flex flex-col justify-center">
                                            <div className="flex items-start justify-between mb-3">
                                                <div>
                                                    <h3 className="text-2xl font-black text-slate-900 dark:text-white flex items-center gap-3 truncate tracking-tight">
                                                        {p.title}
                                                        <span className="text-[10px] uppercase font-bold px-2.5 py-1 bg-modtale-accent/10 text-modtale-accent rounded-lg tracking-wider">{p.classification}</span>
                                                    </h3>
                                                    <p className="text-sm text-slate-500 font-bold mb-1">by <span className="text-slate-700 dark:text-slate-300">{p.author}</span></p>
                                                </div>
                                                <div className="flex items-center gap-2 text-xs font-bold text-slate-400 bg-slate-100 dark:bg-white/5 px-3 py-1.5 rounded-lg uppercase tracking-wider">
                                                    <Clock className="w-3 h-3" />
                                                    {p.updatedAt}
                                                </div>
                                            </div>
                                            <p className="text-slate-600 dark:text-slate-400 text-sm mb-6 line-clamp-2 leading-relaxed font-medium">{p.description}</p>

                                            <div className="flex items-center gap-3">
                                                <button
                                                    onClick={() => fetchProjectDetails(p.id)}
                                                    className="px-6 py-3 bg-slate-900 dark:bg-white text-white dark:text-black rounded-xl font-black text-sm flex items-center gap-2 hover:bg-slate-800 dark:hover:bg-slate-200 transition-all shadow-lg shadow-black/10 dark:shadow-white/5 hover:scale-105 active:scale-95"
                                                >
                                                    {loadingReview && reviewingProject?.mod?.id === p.id ? 'Loading...' : <><Shield className="w-4 h-4" /> Verify Project</>}
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {activeTab === 'users' && isSuperAdmin && (
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-[2rem] p-10 shadow-2xl shadow-black/5">
                        <form onSubmit={handleSearch} className="flex gap-4 mb-10">
                            <div className="relative flex-1 group">
                                <UserIcon className="absolute left-5 top-4 w-5 h-5 text-slate-400 group-focus-within:text-modtale-accent transition-colors" />
                                <input
                                    type="text"
                                    placeholder="Enter Username to manage..."
                                    className="w-full pl-14 px-6 py-4 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-2xl focus:ring-2 focus:ring-modtale-accent outline-none dark:text-white font-bold transition-all placeholder:font-medium"
                                    value={username}
                                    onChange={e => setUsername(e.target.value)}
                                />
                            </div>
                            <button
                                type="submit"
                                disabled={loading || !username}
                                className="bg-modtale-accent text-white px-10 rounded-2xl font-black hover:bg-modtale-accentHover transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 shadow-xl shadow-modtale-accent/20"
                            >
                                {loading ? '...' : <><Search className="w-5 h-5" /> Search</>}
                            </button>
                        </form>

                        {foundUser && (
                            <div className="border border-slate-200 dark:border-white/10 rounded-3xl p-8 bg-slate-50/50 dark:bg-white/[0.02] animate-in fade-in slide-in-from-bottom-4 duration-500">
                                <div className="flex items-center gap-8 mb-10">
                                    <div className="p-1 bg-white dark:bg-white/10 rounded-3xl shadow-lg">
                                        <img src={foundUser.avatarUrl} alt={foundUser.username} className="w-24 h-24 rounded-2xl object-cover" />
                                    </div>
                                    <div>
                                        <h3 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">{foundUser.username}</h3>
                                        <div className="flex items-center gap-3 mt-3">
                                            <span className="text-xs font-bold text-slate-400 uppercase tracking-widest">Active Roles</span>
                                            <div className="flex gap-2">
                                                {foundUser.roles && foundUser.roles.length > 0 ? foundUser.roles.map((r: string) => (
                                                    <span key={r} className="px-3 py-1 bg-blue-500/10 text-blue-600 dark:text-blue-400 text-[10px] font-black rounded-lg border border-blue-500/20">{r}</span>
                                                )) : <span className="text-xs text-slate-400 italic font-medium">No special roles</span>}
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                    <button
                                        onClick={handleToggleAdmin}
                                        disabled={loading}
                                        className={`relative p-8 rounded-3xl border-2 text-left transition-all duration-300 group overflow-hidden ${foundUser.roles?.includes('ADMIN') ? 'border-red-500/30 bg-red-500/5 hover:bg-red-500/10' : 'border-slate-200 dark:border-white/5 hover:border-red-500 hover:bg-white dark:hover:bg-white/5 shadow-sm hover:shadow-xl'}`}
                                    >
                                        <div className="relative z-10">
                                            <div className="flex justify-between items-start mb-4">
                                                <span className={`font-black text-xl flex items-center gap-3 ${foundUser.roles?.includes('ADMIN') ? 'text-red-600 dark:text-red-400' : 'text-slate-900 dark:text-white group-hover:text-red-600 dark:group-hover:text-red-400'}`}>
                                                    <Shield className="w-6 h-6" /> Admin Privileges
                                                </span>
                                                {foundUser.roles?.includes('ADMIN') && <Check className="w-8 h-8 text-red-500 bg-red-100 dark:bg-red-900/30 p-1.5 rounded-full" />}
                                            </div>
                                            <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                                                {foundUser.roles?.includes('ADMIN')
                                                    ? 'User currently has full administrative access. Click to revoke immediately.'
                                                    : 'Granting Admin access will allow this user to approve projects, manage users, and modify content.'}
                                            </p>
                                        </div>
                                    </button>

                                    <button
                                        onClick={() => handleUpdateTier(foundUser.tier === 'ENTERPRISE' ? 'USER' : 'ENTERPRISE')}
                                        disabled={loading}
                                        className={`relative p-8 rounded-3xl border-2 text-left transition-all duration-300 group overflow-hidden ${foundUser.tier === 'ENTERPRISE' ? 'border-purple-500/30 bg-purple-500/5 hover:bg-purple-500/10' : 'border-slate-200 dark:border-white/5 hover:border-purple-500 hover:bg-white dark:hover:bg-white/5 shadow-sm hover:shadow-xl'}`}
                                    >
                                        <div className="relative z-10">
                                            <div className="flex justify-between items-start mb-4">
                                                <span className={`font-black text-xl flex items-center gap-3 ${foundUser.tier === 'ENTERPRISE' ? 'text-purple-600 dark:text-purple-400' : 'text-slate-900 dark:text-white group-hover:text-purple-600 dark:group-hover:text-purple-400'}`}>
                                                    <Zap className="w-6 h-6" /> Enterprise Tier
                                                </span>
                                                {foundUser.tier === 'ENTERPRISE' && <Check className="w-8 h-8 text-purple-500 bg-purple-100 dark:bg-purple-900/30 p-1.5 rounded-full" />}
                                            </div>
                                            <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                                                {foundUser.tier === 'ENTERPRISE'
                                                    ? 'User is on the Enterprise Tier. Click to downgrade to Standard User.'
                                                    : 'Granting Enterprise status allows higher API rate limits (1000 req/min) for CI/CD.'}
                                            </p>
                                        </div>
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
};