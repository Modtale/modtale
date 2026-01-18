import React, { useState, useEffect } from 'react';
import {
    Shield, List, FileText, Box, User as UserIcon, Check, ArrowLeft, Copy, ExternalLink,
    AlertTriangle, Terminal, Download, ArrowRight, X, ImageIcon, ChevronDown, ChevronUp, ShieldAlert, Eye, RefreshCw
} from 'lucide-react';
import { api, API_BASE_URL, BACKEND_URL } from '../../utils/api';
import { SourceInspector } from './SourceInspector';
import type { ScanIssue, ProjectVersion } from '../../types';

interface ReviewInterfaceProps {
    reviewingProject: any;
    onClose: () => void;
    onApprove: () => void;
    onReject: (reason: string) => void;
    setStatus: (s: any) => void;
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

export const ReviewInterface: React.FC<ReviewInterfaceProps> = ({ reviewingProject, onClose, onApprove, onReject, setStatus }) => {
    const [currentStep, setCurrentStep] = useState(0);
    const [checklist, setChecklist] = useState<Record<string, boolean>>({});
    const [rejectReason, setRejectReason] = useState('');
    const [showRejectPanel, setShowRejectPanel] = useState(false);
    const [depMeta, setDepMeta] = useState<Record<string, { icon: string, title: string }>>({});
    const [showScanDetails, setShowScanDetails] = useState(false);
    const [rescanning, setRescanning] = useState(false);

    const [inspectorData, setInspectorData] = useState<{ version: string, structure: string[], issues: ScanIssue[], initialFile?: string, initialLine?: number } | null>(null);
    const [loadingInspector, setLoadingInspector] = useState(false);

    const mod = reviewingProject.mod;
    const isNewProject = mod.status === 'PENDING';

    const pendingVersion = mod.versions.find((v: ProjectVersion) => v.reviewStatus === 'PENDING') || mod.versions[0];
    const scanResult = pendingVersion?.scanResult;
    const hasScanIssues = scanResult && scanResult.status !== 'CLEAN';

    useEffect(() => {
        if (!pendingVersion?.dependencies) return;

        const deps = pendingVersion.dependencies;
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

    const openInspector = async (version: string, issues: ScanIssue[] = [], file?: string, line?: number) => {
        setLoadingInspector(true);
        try {
            const res = await api.get(`/admin/projects/${mod.id}/versions/${version}/structure`);
            setInspectorData({ version, structure: res.data, issues, initialFile: file, initialLine: line });
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to inspect JAR structure.' });
        } finally {
            setLoadingInspector(false);
        }
    };

    const toggleCheck = (id: string) => {
        setChecklist(prev => ({ ...prev, [id]: !prev[id] }));
    };

    const canProceed = () => {
        switch (currentStep) {
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

    const handleVersionApprove = async () => {
        try {
            if (isNewProject) {
                await api.post(`/admin/projects/${mod.id}/publish`);
            } else {
                await api.post(`/admin/projects/${mod.id}/versions/${pendingVersion.id}/approve`);
            }
            onApprove();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data || 'Failed to approve.' });
        }
    };

    const handleVersionReject = async (reason: string) => {
        try {
            if (isNewProject) {
                await api.post(`/admin/projects/${mod.id}/reject`, { reason });
            } else {
                await api.post(`/admin/projects/${mod.id}/versions/${pendingVersion.id}/reject`, { reason });
            }
            onReject(reason);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data || 'Failed to reject.' });
        }
    };

    const handleRescan = async () => {
        if (!pendingVersion) return;
        setRescanning(true);
        try {
            await api.post(`/admin/projects/${mod.id}/versions/${pendingVersion.id}/scan`);
            setStatus({ type: 'success', title: 'Scan Initiated', msg: 'The malware scanner has been queued for this version.' });
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Scan Failed', msg: e.response?.data || 'Could not start rescan.' });
        } finally {
            setRescanning(false);
        }
    };

    return (
        <div className="fixed inset-0 z-[100] bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 md:p-8 animate-in fade-in duration-200">
            {inspectorData && (
                <SourceInspector
                    modId={mod.id}
                    versionId={pendingVersion.id}
                    version={inspectorData.version}
                    structure={inspectorData.structure}
                    issues={inspectorData.issues}
                    initialFile={inspectorData.initialFile}
                    initialLine={inspectorData.initialLine}
                    onClose={() => setInspectorData(null)}
                />
            )}

            <div className="bg-slate-50 dark:bg-slate-900 w-full max-w-7xl h-[85vh] rounded-3xl shadow-2xl border border-slate-200 dark:border-white/5 flex overflow-hidden ring-1 ring-white/10 relative">

                {showRejectPanel && (
                    <div className="absolute inset-0 z-[150] bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
                        <div className="bg-white dark:bg-slate-900 w-full max-w-lg p-6 rounded-3xl shadow-2xl border border-slate-200 dark:border-white/10">
                            <div className="flex justify-between items-center mb-6">
                                <h3 className="text-xl font-black text-slate-900 dark:text-white flex items-center gap-2">
                                    <Shield className="w-6 h-6 text-red-500" />
                                    Reject {isNewProject ? "Project" : "Version " + pendingVersion.versionNumber}
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
                                    onClick={() => handleVersionReject(rejectReason)}
                                    disabled={!rejectReason}
                                    className="flex-1 py-3 bg-red-500 hover:bg-red-600 disabled:opacity-50 text-white rounded-xl font-bold transition-colors shadow-lg shadow-red-500/20"
                                >
                                    Confirm Reject
                                </button>
                            </div>
                        </div>
                    </div>
                )}

                <div className="w-64 bg-slate-100 dark:bg-slate-950/50 border-r border-slate-200 dark:border-white/5 p-6 flex flex-col">
                    <h2 className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-6">Reviewing: {isNewProject ? "New Project" : `v${pendingVersion.versionNumber}`}</h2>
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
                    <button onClick={onClose} className="mt-auto flex items-center gap-2 text-slate-500 hover:text-slate-900 dark:hover:text-white px-3 py-2 text-sm font-bold transition-colors">
                        <ArrowLeft className="w-4 h-4" /> Exit Review
                    </button>
                </div>

                <div className="flex-1 flex flex-col overflow-hidden relative bg-white dark:bg-transparent">
                    <div className="p-6 border-b border-slate-200 dark:border-white/5 flex justify-between items-center bg-slate-50/50 dark:bg-slate-900/50 backdrop-blur-md">
                        <div>
                            <h1 className="text-2xl font-black text-slate-900 dark:text-white tracking-tight">{mod.title}</h1>
                            <div className="flex items-center gap-2 mt-1">
                                <span className="text-xs font-mono text-slate-400">{mod.id}</span>
                                <button onClick={() => navigator.clipboard.writeText(mod.id)} className="text-slate-400 hover:text-modtale-accent"><Copy className="w-3 h-3" /></button>
                            </div>
                        </div>
                        <div className="flex gap-3">
                            <a href={`/mod/${mod.id}`} target="_blank" rel="noreferrer" className="px-4 py-2 bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-300 rounded-xl text-sm font-bold flex items-center gap-2 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors border border-slate-200 dark:border-white/5">
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
                                        <p className="font-bold text-lg dark:text-white mt-1">{mod.title}</p>
                                    </div>
                                    <div className="p-5 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                        <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Classification</label>
                                        <p className="font-bold text-lg dark:text-white mt-1">{mod.classification}</p>
                                    </div>
                                    {mod.slug && (
                                        <div className="p-5 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                            <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Slug</label>
                                            <div className="flex items-center gap-2 mt-1">
                                                <p className="font-mono text-sm dark:text-slate-300 bg-black/20 px-2 py-1 rounded">{mod.slug}</p>
                                                <span className="text-xs text-slate-500">(Custom)</span>
                                            </div>
                                        </div>
                                    )}
                                    {mod.license && (
                                        <div className="p-5 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                            <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">License</label>
                                            <p className="font-bold text-lg dark:text-white mt-1 flex items-center gap-2">
                                                <FileText className="w-4 h-4 text-slate-500"/>
                                                {mod.license}
                                                {mod.links?.license && (
                                                    <a href={mod.links.license} target="_blank" rel="noreferrer" className="ml-2 text-modtale-accent hover:underline text-xs flex items-center gap-1">
                                                        <ExternalLink className="w-3 h-3"/> View License
                                                    </a>
                                                )}
                                            </p>
                                        </div>
                                    )}
                                </div>

                                <div className="p-5 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                    <label className="text-xs font-bold text-slate-400 uppercase block mb-3 tracking-wider">Tags</label>
                                    <div className="flex flex-wrap gap-2">
                                        {mod.tags?.map((t: string) => (
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
                                        <span className="font-bold text-slate-700 dark:text-slate-200">Title and Slug are appropriate</span>
                                    </label>
                                    <label className="flex items-center gap-4 p-4 rounded-xl border border-slate-200 dark:border-white/10 cursor-pointer hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group">
                                        <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-colors ${checklist['class'] ? 'bg-modtale-accent border-modtale-accent' : 'border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                            {checklist['class'] && <Check className="w-4 h-4 text-white" />}
                                        </div>
                                        <input type="checkbox" checked={!!checklist['class']} onChange={() => toggleCheck('class')} className="hidden" />
                                        <span className="font-bold text-slate-700 dark:text-slate-200">Classification and License are correct</span>
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

                                {mod.bannerUrl && (
                                    <div className="bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5 overflow-hidden">
                                        <div className="px-5 py-3 border-b border-slate-200 dark:border-white/5">
                                            <label className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
                                                <ImageIcon className="w-3 h-3"/> Custom Banner
                                            </label>
                                        </div>
                                        <div className="aspect-[3/1] bg-slate-900 relative">
                                            <img src={mod.bannerUrl} className="w-full h-full object-cover" alt="Banner" />
                                        </div>
                                    </div>
                                )}

                                <div className="flex gap-6 p-5 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                    <img src={mod.imageUrl} className="w-24 h-24 rounded-xl object-cover bg-slate-200 dark:bg-white/10" />
                                    <div className="flex-1">
                                        <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Short Description</label>
                                        <p className="text-lg text-slate-700 dark:text-slate-200 font-medium leading-relaxed mt-1">{mod.description}</p>
                                    </div>
                                </div>

                                <div className="bg-slate-50 dark:bg-white/5 p-6 rounded-2xl border border-slate-200 dark:border-white/5">
                                    <label className="text-xs font-bold text-slate-400 uppercase block mb-4 tracking-wider">Long Description Preview</label>
                                    <div className="prose dark:prose-invert max-w-none text-sm prose-p:text-slate-600 dark:prose-p:text-slate-300">
                                        <div className="whitespace-pre-wrap">{mod.about}</div>
                                    </div>
                                </div>

                                {mod.gallery?.length > 0 && (
                                    <div className="grid grid-cols-3 gap-4">
                                        {mod.gallery.map((url: string, i: number) => (
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
                                        <span className="font-bold text-slate-700 dark:text-slate-200">Images/Banner are appropriate</span>
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

                                {hasScanIssues && (
                                    <div className="rounded-2xl border border-red-200 dark:border-red-900/50 overflow-hidden">
                                        <div className="flex items-center justify-between p-5 bg-red-50 dark:bg-red-900/10">
                                            <div className="flex items-center gap-3 text-red-700 dark:text-red-400">
                                                <ShieldAlert className="w-6 h-6" />
                                                <div>
                                                    <h4 className="font-bold text-lg">Malware Checks Failed</h4>
                                                    <p className="text-xs opacity-80 font-medium">Status: {scanResult.status} • Risk Score: {scanResult.riskScore}</p>
                                                </div>
                                            </div>
                                            <div className="flex gap-2">
                                                <button
                                                    onClick={handleRescan}
                                                    disabled={rescanning}
                                                    className="p-2 hover:bg-white/20 rounded-lg transition-colors text-red-600 dark:text-red-400"
                                                    title="Rescan"
                                                >
                                                    <RefreshCw className={`w-5 h-5 ${rescanning ? 'animate-spin' : ''}`} />
                                                </button>
                                                <button
                                                    onClick={() => setShowScanDetails(!showScanDetails)}
                                                    className="p-2 hover:bg-white/20 rounded-lg transition-colors text-red-600 dark:text-red-400"
                                                >
                                                    {showScanDetails ? <ChevronUp className="w-5 h-5"/> : <ChevronDown className="w-5 h-5"/>}
                                                </button>
                                            </div>
                                        </div>

                                        {showScanDetails && (
                                            <div className="p-4 bg-white dark:bg-black/20 space-y-2 border-t border-red-200 dark:border-red-900/50">
                                                {(scanResult.issues || []).map((issue: ScanIssue, idx: number) => (
                                                    <div key={idx} className="flex items-center justify-between text-sm bg-slate-50 dark:bg-white/5 p-3 rounded-xl border border-slate-200 dark:border-white/5">
                                                        <div className="flex-1 min-w-0 pr-4">
                                                            <div className="flex items-center gap-2 mb-1">
                                                                <span className={`font-black text-[10px] px-1.5 py-0.5 rounded uppercase tracking-wide
                                                                    ${issue.severity === 'CRITICAL' ? 'bg-red-600 text-white' :
                                                                    issue.severity === 'HIGH' ? 'bg-orange-500 text-white' :
                                                                        'bg-yellow-500 text-white'}`}>
                                                                    {issue.severity}
                                                                </span>
                                                                <span className="text-slate-900 dark:text-slate-200 font-bold truncate">
                                                                    {issue.type}
                                                                </span>
                                                            </div>
                                                            <div className="flex items-center gap-2 text-slate-500 font-mono text-xs truncate mb-1">
                                                                <span className="truncate">{issue.filePath}</span>
                                                                {issue.lineStart > -1 && <span className="bg-slate-200 dark:bg-slate-700 px-1.5 rounded text-slate-600 dark:text-slate-300">:{issue.lineStart}</span>}
                                                            </div>
                                                            <p className="text-xs text-slate-600 dark:text-slate-400 leading-snug">{issue.description}</p>
                                                        </div>
                                                        <button
                                                            onClick={() => openInspector(pendingVersion.versionNumber, scanResult.issues, issue.filePath, issue.lineStart)}
                                                            className="shrink-0 flex items-center gap-1.5 text-xs font-bold bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900/40 px-3 py-2 rounded-lg transition-colors"
                                                        >
                                                            <Eye className="w-3.5 h-3.5" /> Inspect
                                                        </button>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                )}

                                {!hasScanIssues && (
                                    <div className="p-5 bg-emerald-500/10 border border-emerald-500/20 rounded-2xl flex items-center justify-between">
                                        <div className="flex items-center gap-4">
                                            <Check className="w-6 h-6 text-emerald-500" />
                                            <div>
                                                <h4 className="font-bold text-emerald-500">Automated Checks Passed</h4>
                                                <p className="text-sm text-emerald-600/80 dark:text-emerald-500/70 font-medium">
                                                    Warden found no known malware signatures or suspicious patterns.
                                                </p>
                                            </div>
                                        </div>
                                        <button
                                            onClick={handleRescan}
                                            disabled={rescanning}
                                            className="p-2 hover:bg-emerald-500/10 rounded-lg transition-colors text-emerald-600 dark:text-emerald-400"
                                            title="Force Rescan"
                                        >
                                            <RefreshCw className={`w-5 h-5 ${rescanning ? 'animate-spin' : ''}`} />
                                        </button>
                                    </div>
                                )}

                                <div className="space-y-3">
                                    <div className="p-4 bg-white dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/10 flex items-center justify-between group hover:border-modtale-accent/30 transition-colors">
                                        <div>
                                            <div className="flex items-center gap-3">
                                                <span className="font-black text-lg text-slate-900 dark:text-white">{pendingVersion.versionNumber}</span>
                                                <span className={`text-[10px] uppercase font-bold px-2 py-0.5 rounded ${pendingVersion.channel === 'RELEASE' ? 'bg-emerald-500/10 text-emerald-500' : 'bg-amber-500/10 text-amber-500'}`}>{pendingVersion.channel}</span>
                                            </div>
                                            <p className="text-xs text-slate-400 mt-1 font-bold">Game Versions: <span className="text-slate-600 dark:text-slate-300">{pendingVersion.gameVersions?.join(', ')}</span></p>
                                        </div>
                                        <div className="flex gap-2">
                                            {mod.classification !== 'MODPACK' && (
                                                <button
                                                    onClick={() => openInspector(pendingVersion.versionNumber, scanResult?.issues || [])}
                                                    disabled={loadingInspector}
                                                    className="px-5 py-2.5 bg-indigo-500/10 hover:bg-indigo-500 hover:text-white text-indigo-500 rounded-xl text-sm font-bold flex items-center gap-2 transition-all border border-indigo-500/20"
                                                >
                                                    {loadingInspector && inspectorData?.version === pendingVersion.versionNumber ? 'Loading...' : <><Terminal className="w-4 h-4" /> Inspect Source</>}
                                                </button>
                                            )}
                                            <a
                                                href={`${API_BASE_URL}/projects/${mod.id}/versions/${pendingVersion.versionNumber}/download`}
                                                className="px-5 py-2.5 bg-slate-100 dark:bg-white/10 hover:bg-modtale-accent hover:text-white text-slate-600 dark:text-slate-300 rounded-xl text-sm font-bold flex items-center gap-2 transition-all"
                                                target="_blank" rel="noreferrer"
                                            >
                                                <Download className="w-4 h-4" /> Download
                                            </a>
                                        </div>
                                    </div>
                                </div>

                                <div className="p-6 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                                    <label className="text-xs font-bold text-slate-400 uppercase block mb-4 tracking-wider">Dependencies</label>
                                    {pendingVersion.dependencies?.length > 0 ? (
                                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                            {pendingVersion.dependencies.map((d: any) => {
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
                                            <a href={`/creator/${mod.author}`} target="_blank" rel="noreferrer" className="flex items-center gap-2 group">
                                                <p className="font-black text-2xl dark:text-white mt-1 group-hover:underline">{mod.author}</p>
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
                                <h2 className="text-4xl font-black text-slate-900 dark:text-white mb-4 tracking-tight">
                                    {isNewProject ? "Approve Project?" : "Approve Update?"}
                                </h2>
                                <p className="text-slate-500 dark:text-slate-400 font-medium mb-10 text-lg leading-relaxed">
                                    You are about to approve <strong>v{pendingVersion.versionNumber}</strong>.
                                    {isNewProject ? " This will make the project publicly visible." : " This update will be pushed to users immediately."}
                                </p>

                                <div className="flex flex-col gap-4">
                                    <button
                                        onClick={handleVersionApprove}
                                        className="w-full py-4 bg-emerald-500 hover:bg-emerald-600 text-white rounded-2xl font-black text-lg shadow-xl shadow-emerald-500/20 transition-all transform hover:scale-[1.02] active:scale-95"
                                    >
                                        Approve & Publish
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
    );
};