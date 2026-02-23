import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { api, BACKEND_URL } from '@/utils/api';
import type { Modjam, ModjamSubmission, User, Mod } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { Trophy, Users, Upload, CheckCircle2, LayoutGrid, AlertCircle, Scale, Star, ChevronRight, Edit3, Trash2, Clock } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';
import { JamBuilder } from '@/components/resources/upload/JamBuilder';
import { JamSubmissionWizard } from '@/react-pages/jams/JamSubmissionWizard';

const CompactCountdown: React.FC<{ jam: Modjam, now: number }> = ({ jam, now }) => {
    const start = new Date(jam.startDate).getTime();
    const end = new Date(jam.endDate).getTime();
    const voting = new Date(jam.votingEndDate).getTime();

    let target = start;
    let label = 'Starts In';

    if (now < start) {
        target = start;
        label = 'Starts In';
    } else if (now < end) {
        target = end;
        label = 'Submissions Close In';
    } else if (now < voting) {
        target = voting;
        label = 'Voting Ends In';
    } else {
        return (
            <div className="inline-flex items-center gap-2 px-4 py-2 bg-slate-100 dark:bg-slate-800/50 rounded-xl text-xs font-black text-slate-500 uppercase tracking-widest border border-slate-200/50 dark:border-white/5 h-12">
                <Clock className="w-3.5 h-3.5"/> Jam Completed
            </div>
        );
    }

    const diff = target - now;
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);
    const pad = (n: number) => n.toString().padStart(2, '0');
    const timeStr = days > 0 ? `${days}d ${pad(hours)}h ${pad(minutes)}m` : `${pad(hours)}h ${pad(minutes)}m ${pad(seconds)}s`;

    return (
        <div className="inline-flex items-center gap-3 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 px-4 py-2.5 rounded-2xl shadow-sm h-12">
            <div className="w-8 h-8 rounded-xl bg-modtale-accent/10 flex items-center justify-center shrink-0">
                <Clock className="w-4 h-4 text-modtale-accent" />
            </div>
            <div className="flex flex-col pr-2 justify-center">
                <span className="text-[9px] font-black uppercase tracking-widest text-slate-500 leading-none mb-1">{label}</span>
                <span className="text-base font-black font-mono text-slate-900 dark:text-white leading-none">{timeStr}</span>
            </div>
        </div>
    );
};

const EventTimeline: React.FC<{ jam: Modjam, now: number }> = ({ jam, now }) => {
    const start = new Date(jam.startDate).getTime();
    const end = new Date(jam.endDate).getTime();
    const voting = new Date(jam.votingEndDate).getTime();

    const phases = [
        { label: 'Start', time: start, dateStr: new Date(jam.startDate).toLocaleDateString() },
        { label: 'Submissions', time: end, dateStr: new Date(jam.endDate).toLocaleDateString() },
        { label: 'Voting', time: voting, dateStr: new Date(jam.votingEndDate).toLocaleDateString() }
    ];

    let target = start;
    let label = 'Jam Starts In';
    let progress = 0;

    if (now < start) {
        target = start;
        label = 'Jam Starts In';
        progress = 0;
    } else if (now < end) {
        target = end;
        label = 'Submissions Close In';
        progress = ((now - start) / (end - start)) * 50;
    } else if (now < voting) {
        target = voting;
        label = 'Voting Ends In';
        progress = 50 + ((now - end) / (voting - end)) * 50;
    } else {
        target = 0;
        label = 'Jam Completed';
        progress = 100;
    }

    const diff = target - now;
    let timeStr = '--';

    if (diff > 0) {
        const days = Math.floor(diff / (1000 * 60 * 60 * 24));
        const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
        const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
        const seconds = Math.floor((diff % (1000 * 60)) / 1000);
        const pad = (n: number) => n.toString().padStart(2, '0');
        timeStr = days > 0 ? `${days}d ${pad(hours)}h ${pad(minutes)}m` : `${pad(hours)}h ${pad(minutes)}m ${pad(seconds)}s`;
    } else {
        timeStr = 'Finished';
    }

    return (
        <div className="flex flex-col md:flex-row items-center gap-8 md:gap-12 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-[2rem] p-8 shadow-sm w-full">
            <div className="flex flex-col shrink-0 min-w-[220px] text-center md:text-left">
                <span className="text-[11px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400 mb-2 flex items-center justify-center md:justify-start gap-2">
                    <Clock className="w-4 h-4" /> {label}
                </span>
                <span className="text-3xl md:text-4xl font-black font-mono text-modtale-accent drop-shadow-sm leading-none">{timeStr}</span>
            </div>

            <div className="flex-1 w-full pt-6 md:pt-0 pl-0 md:pl-8">
                <div className="relative flex items-start justify-between w-full">
                    <div className="absolute top-3 left-14 right-14 h-2 bg-slate-200/50 dark:bg-slate-800 rounded-full overflow-hidden">
                        <div className="h-full bg-modtale-accent transition-all duration-1000 rounded-full" style={{ width: `${progress}%` }} />
                    </div>
                    {phases.map((phase, idx) => {
                        const isPast = now >= phase.time;
                        const isCurrent = (idx === 0 && now >= start && now < end) ||
                            (idx === 1 && now >= end && now < voting) ||
                            (idx === 2 && now >= voting && now < voting + 1000 * 60 * 60 * 24);

                        return (
                            <div key={phase.label} className="flex flex-col items-center w-28 relative z-10">
                                <div className={`w-8 h-8 rounded-full border-[5px] transition-colors bg-white dark:bg-slate-900 mb-3.5 flex items-center justify-center ${isPast ? 'border-modtale-accent' : 'border-slate-300 dark:border-slate-700'} ${isCurrent ? 'ring-4 ring-modtale-accent/20 scale-110' : ''}`} />
                                <span className={`text-[11px] font-black uppercase tracking-widest transition-colors ${isPast || isCurrent ? 'text-slate-700 dark:text-slate-200' : 'text-slate-400'}`}>{phase.label}</span>
                                <span className="text-[10px] font-bold text-slate-400 mt-1">{phase.dateStr}</span>
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};

export const JamDetail: React.FC<{ currentUser: User | null }> = ({ currentUser }) => {
    const { slug } = useParams<{ slug: string }>();
    const navigate = useNavigate();

    const [jam, setJam] = useState<Modjam | null>(null);
    const [submissions, setSubmissions] = useState<ModjamSubmission[]>([]);
    const [loading, setLoading] = useState(true);
    const [myProjects, setMyProjects] = useState<Mod[]>([]);

    const [activeTab, setActiveTab] = useState<'overview' | 'entries'>('overview');

    const [isSubmittingModalOpen, setIsSubmittingModalOpen] = useState(false);
    const [votingSubmissionId, setVotingSubmissionId] = useState<string | null>(null);
    const [now, setNow] = useState(new Date().getTime());

    const [statusModal, setStatusModal] = useState<{
        type: 'success' | 'error' | 'warning',
        title: string,
        message: string,
        onAction?: () => void,
        actionLabel?: string,
        secondaryLabel?: string
    } | null>(null);

    const [isEditing, setIsEditing] = useState(false);
    const [metaData, setMetaData] = useState<any>(null);
    const [builderTab, setBuilderTab] = useState<'details' | 'categories' | 'settings'>('details');
    const [isSavingJam, setIsSavingJam] = useState(false);

    const resolveUrl = (url?: string | null) => {
        if (!url) return '';
        if (url.startsWith('/api') || url.startsWith('/uploads')) {
            return `${BACKEND_URL}${url}`;
        }
        return url;
    };

    useEffect(() => {
        const interval = setInterval(() => setNow(new Date().getTime()), 1000);
        return () => clearInterval(interval);
    }, []);

    useEffect(() => {
        const fetchJamData = async () => {
            try {
                const res = await api.get(`/modjams/${slug}`);
                setJam(res.data);

                const subRes = await api.get(`/modjams/${res.data.id}/submissions`);
                setSubmissions(subRes.data);

                if (['VOTING', 'COMPLETED'].includes(res.data.status)) {
                    setActiveTab('entries');
                }
            } catch (err) {
                console.error(err);
                setStatusModal({ type: 'error', title: 'Not Found', message: 'Failed to load jam details.' });
            } finally {
                setLoading(false);
            }

            if (currentUser) {
                try {
                    const projRes = await api.get(`/creators/${currentUser.username}/projects?size=100`);
                    const allProjects: Mod[] = projRes.data.content || [];
                    setMyProjects(allProjects.filter(p => p.status === 'PUBLISHED'));
                } catch (e) {
                    console.error("Failed to fetch user projects for jam submission", e);
                }
            }
        };
        fetchJamData();
    }, [slug, currentUser]);

    const handleJoin = async () => {
        if (!jam || !currentUser) return;
        try {
            await api.post(`/modjams/${jam.id}/participate`, {});
            setStatusModal({ type: 'success', title: 'Joined!', message: 'Successfully joined the jam.' });
            setJam({ ...jam, participantIds: [...(jam.participantIds || []), currentUser.id] });
        } catch (err) {
            setStatusModal({ type: 'error', title: 'Error', message: 'Failed to join the jam.' });
        }
    };

    const handleVote = async (submissionId: string, categoryId: string, score: number) => {
        if (!jam) return;
        try {
            const res = await api.post(`/modjams/${jam.id}/vote`, { submissionId, categoryId, score });
            setSubmissions(submissions.map(s => s.id === submissionId ? res.data : s));
        } catch (err: any) {
            setStatusModal({ type: 'error', title: 'Vote Failed', message: err.response?.data?.message || 'Failed to record vote.' });
        }
    };

    const startEditing = () => {
        if (!jam) return;
        setMetaData({
            id: jam.id,
            slug: jam.slug,
            title: jam.title,
            description: jam.description,
            imageUrl: (jam as any).imageUrl,
            bannerUrl: jam.bannerUrl,
            startDate: jam.startDate,
            endDate: jam.endDate,
            votingEndDate: jam.votingEndDate,
            allowPublicVoting: jam.allowPublicVoting,
            allowConcurrentVoting: jam.allowConcurrentVoting,
            showResultsBeforeVotingEnds: jam.showResultsBeforeVotingEnds,
            categories: jam.categories,
            status: jam.status
        });
        setIsEditing(true);
    };

    const handleSaveJam = async () => {
        setIsSavingJam(true);
        try {
            let res = await api.put(`/modjams/${metaData.id}`, metaData);
            let filesUploaded = false;

            if (metaData.iconFile) {
                const fd = new FormData();
                fd.append('file', metaData.iconFile);
                await api.put(`/modjams/${metaData.id}/icon`, fd, { headers: { 'Content-Type': 'multipart/form-data' }});
                filesUploaded = true;
            }

            if (metaData.bannerFile) {
                const fd = new FormData();
                fd.append('file', metaData.bannerFile);
                await api.put(`/modjams/${metaData.id}/banner`, fd, { headers: { 'Content-Type': 'multipart/form-data' }});
                filesUploaded = true;
            }

            if (filesUploaded) {
                res = await api.get(`/modjams/${metaData.slug}`);
            }

            setJam(res.data);
            setIsSavingJam(false);
            return true;
        } catch (e) {
            setIsSavingJam(false);
            return false;
        }
    };

    const handleDelete = () => {
        if (!jam) return;

        setStatusModal({
            type: 'warning',
            title: 'Delete Event?',
            message: 'Are you sure you want to delete this jam? This action cannot be undone and will permanently delete all submissions.',
            actionLabel: 'Delete Jam',
            secondaryLabel: 'Cancel',
            onAction: async () => {
                try {
                    await api.delete(`/modjams/${jam.id}`);
                    navigate('/jams');
                } catch (err: any) {
                    setStatusModal({ type: 'error', title: 'Error', message: 'Failed to delete jam.' });
                }
            }
        });
    };

    if (loading) return <div className="min-h-screen bg-slate-950 flex items-center justify-center"><Spinner fullScreen={false} className="w-8 h-8" /></div>;
    if (!jam) return <div className="p-20 text-center font-bold text-slate-500">Jam not found.</div>;

    if (isEditing && metaData) {
        return (
            <JamBuilder
                metaData={metaData}
                setMetaData={setMetaData}
                handleSave={handleSaveJam}
                onPublish={async () => { await handleSaveJam(); setIsEditing(false); }}
                isLoading={isSavingJam}
                activeTab={builderTab}
                setActiveTab={setBuilderTab}
                onBack={() => setIsEditing(false)}
            />
        );
    }

    const isHost = currentUser?.id === jam.hostId;
    const isParticipating = currentUser?.id && (jam.participantIds || []).includes(currentUser.id);
    const hasSubmitted = submissions.some(s => s.submitterId === currentUser?.id);

    const canVote = (jam.status === 'VOTING' || (jam.status === 'ACTIVE' && jam.allowConcurrentVoting)) && (jam.allowPublicVoting || isHost);
    const canSeeResults = jam.status === 'COMPLETED' || jam.showResultsBeforeVotingEnds || isHost;

    const sortedSubmissions = [...submissions].sort((a, b) => (b.totalScore || 0) - (a.totalScore || 0));

    const activeVotingSub = submissions.find(s => s.id === votingSubmissionId);

    return (
        <>
            {statusModal && (
                <StatusModal
                    type={statusModal.type}
                    title={statusModal.title}
                    message={statusModal.message}
                    onClose={() => setStatusModal(null)}
                    onAction={statusModal.onAction}
                    actionLabel={statusModal.actionLabel}
                    secondaryLabel={statusModal.secondaryLabel}
                />
            )}

            {isSubmittingModalOpen && jam && (
                <JamSubmissionWizard
                    jam={jam}
                    myProjects={myProjects}
                    onSuccess={(sub) => {
                        setSubmissions([...submissions, sub]);
                        setIsSubmittingModalOpen(false);
                        setStatusModal({ type: 'success', title: 'Submitted!', message: 'Project submitted successfully.' });
                    }}
                    onCancel={() => setIsSubmittingModalOpen(false)}
                    onError={(msg) => setStatusModal({ type: 'error', title: 'Submission Failed', message: msg })}
                />
            )}

            {activeVotingSub && (
                <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-200">
                    <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl w-full max-w-md rounded-[2.5rem] shadow-2xl border border-white/20 dark:border-white/10 overflow-hidden flex flex-col animate-in zoom-in-95">
                        <div className="p-10 border-b border-slate-200/50 dark:border-white/5 text-center">
                            <div className="w-24 h-24 rounded-[1.25rem] bg-white dark:bg-slate-800 shadow-lg overflow-hidden mx-auto mb-6 border-[3px] border-white dark:border-slate-700">
                                {activeVotingSub.projectImageUrl ? (
                                    <img src={resolveUrl(activeVotingSub.projectImageUrl)} className="w-full h-full object-cover" alt="" />
                                ) : (
                                    <LayoutGrid className="w-10 h-10 m-auto mt-6 text-slate-400 opacity-20" />
                                )}
                            </div>
                            <h3 className="text-3xl font-black text-slate-900 dark:text-white drop-shadow-sm">Cast Your Vote</h3>
                            <p className="text-base font-medium text-slate-500 mt-2">Voting on <span className="text-modtale-accent font-bold">{activeVotingSub.projectTitle}</span></p>
                        </div>
                        <div className="p-10 overflow-y-auto max-h-[50vh] space-y-8 custom-scrollbar">
                            {jam.categories.map(cat => {
                                const myVote = activeVotingSub.votes?.find(v => v.voterId === currentUser?.id && v.categoryId === cat.id);
                                return (
                                    <div key={cat.id} className="space-y-4">
                                        <div className="flex items-center justify-between">
                                            <span className="text-base font-black text-slate-900 dark:text-white">{cat.name}</span>
                                            <span className="text-[11px] font-black uppercase tracking-widest text-slate-400 bg-slate-100 dark:bg-white/5 px-3 py-1 rounded-lg">Max {cat.maxScore}</span>
                                        </div>
                                        <div className="flex flex-wrap items-center gap-3">
                                            {[...Array(cat.maxScore)].map((_, i) => (
                                                <button
                                                    key={i}
                                                    onClick={() => handleVote(activeVotingSub.id, cat.id, i + 1)}
                                                    className={`flex-1 h-14 rounded-[1.25rem] flex items-center justify-center font-black text-base transition-all shadow-sm ${myVote?.score === i + 1 ? 'bg-modtale-accent text-white scale-105 ring-4 ring-modtale-accent/30' : 'bg-white dark:bg-slate-800 text-slate-500 hover:bg-slate-50 dark:hover:bg-slate-700 hover:text-slate-900 dark:hover:text-white border border-slate-200 dark:border-white/5'}`}
                                                >
                                                    {i + 1}
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                )
                            })}
                        </div>
                        <div className="p-8 border-t border-slate-200/50 dark:border-white/5 bg-slate-50/50 dark:bg-black/20">
                            <button onClick={() => setVotingSubmissionId(null)} className="w-full h-16 bg-slate-900 dark:bg-white text-white dark:text-slate-900 rounded-[1.5rem] font-black text-lg transition-all hover:scale-[1.02] active:scale-95 shadow-lg">
                                Done Voting
                            </button>
                        </div>
                    </div>
                </div>
            )}

            <JamLayout
                bannerUrl={jam.bannerUrl}
                iconUrl={(jam as any).imageUrl}
                onBack={() => navigate('/jams')}
                titleContent={
                    <h1 className="text-3xl md:text-4xl lg:text-5xl font-black text-slate-900 dark:text-white tracking-tighter drop-shadow-xl leading-tight">
                        {jam.title}
                    </h1>
                }
                hostContent={
                    <div className="flex items-center gap-2.5 text-slate-600 dark:text-slate-300 font-bold text-lg mt-2">
                        <span>Hosted by</span>
                        <Link to={`/creator/${jam.hostName}`} className="text-slate-900 dark:text-white hover:text-modtale-accent transition-colors flex items-center gap-1.5 bg-white/50 dark:bg-black/30 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/20 dark:border-white/10 shadow-sm">
                            {jam.hostName} <ChevronRight className="w-4 h-4 opacity-50" />
                        </Link>
                    </div>
                }
                statsContent={
                    <div className="flex flex-wrap items-center gap-4">
                        <div className="flex items-center gap-4 bg-white/50 dark:bg-slate-900/50 backdrop-blur-xl border border-white/40 dark:border-white/10 h-14 md:h-16 px-5 md:px-6 rounded-[1rem] md:rounded-[1.25rem] shadow-lg">
                            <div className="w-8 h-8 md:w-10 md:h-10 rounded-[10px] md:rounded-xl bg-modtale-accent/10 flex items-center justify-center shrink-0">
                                <Users className="w-4 h-4 md:w-5 md:h-5 text-modtale-accent" />
                            </div>
                            <div className="flex flex-col justify-center">
                                <span className="text-base md:text-lg font-black text-slate-900 dark:text-white leading-none">{(jam.participantIds || []).length}</span>
                                <span className="text-[9px] md:text-[10px] font-black uppercase tracking-widest text-slate-500 mt-1">Participants</span>
                            </div>
                        </div>
                        <div className="flex items-center gap-4 bg-white/50 dark:bg-slate-900/50 backdrop-blur-xl border border-white/40 dark:border-white/10 h-14 md:h-16 px-5 md:px-6 rounded-[1rem] md:rounded-[1.25rem] shadow-lg">
                            <div className="w-8 h-8 md:w-10 md:h-10 rounded-[10px] md:rounded-xl bg-modtale-accent/10 flex items-center justify-center shrink-0">
                                <Upload className="w-4 h-4 md:w-5 md:h-5 text-modtale-accent" />
                            </div>
                            <div className="flex flex-col justify-center">
                                <span className="text-base md:text-lg font-black text-slate-900 dark:text-white leading-none">{submissions.length}</span>
                                <span className="text-[9px] md:text-[10px] font-black uppercase tracking-widest text-slate-500 mt-1">Entries</span>
                            </div>
                        </div>
                    </div>
                }
                actionContent={
                    <>
                        {isHost && (
                            <div className="flex gap-3 shrink-0">
                                <button onClick={startEditing} className="h-14 w-14 md:h-16 md:w-16 rounded-[1rem] md:rounded-[1.25rem] bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 hover:border-modtale-accent text-slate-900 dark:text-white shadow-lg flex items-center justify-center transition-all hover:scale-105 active:scale-95 group" title="Edit Jam">
                                    <Edit3 className="w-6 h-6 group-hover:text-modtale-accent transition-colors" />
                                </button>
                                <button onClick={handleDelete} className="h-14 w-14 md:h-16 md:w-16 rounded-[1rem] md:rounded-[1.25rem] bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 hover:border-red-500 text-slate-900 dark:text-white shadow-lg flex items-center justify-center transition-all hover:scale-105 active:scale-95 group" title="Delete Jam">
                                    <Trash2 className="w-6 h-6 group-hover:text-red-500 transition-colors" />
                                </button>
                            </div>
                        )}

                        {!currentUser ? (
                            <div className="h-14 md:h-16 px-6 md:px-8 rounded-[1rem] md:rounded-[1.25rem] bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 shadow-lg flex items-center gap-3 text-base font-bold text-slate-600 dark:text-slate-300">
                                <AlertCircle className="w-5 h-5" /> Sign in to join
                            </div>
                        ) : !isParticipating ? (
                            <button onClick={handleJoin} className="h-14 md:h-16 px-8 md:px-10 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-[1rem] md:rounded-[1.25rem] font-black text-lg md:text-xl shadow-xl shadow-modtale-accent/20 transition-all hover:scale-105 active:scale-95 flex items-center gap-2">
                                <Users className="w-6 h-6" /> Join Jam
                            </button>
                        ) : jam.status === 'ACTIVE' && !hasSubmitted ? (
                            <button onClick={() => setIsSubmittingModalOpen(true)} className="h-14 md:h-16 px-8 md:px-10 bg-slate-900 dark:bg-white text-white dark:text-slate-900 rounded-[1rem] md:rounded-[1.25rem] font-black text-lg md:text-xl shadow-xl transition-all hover:scale-105 active:scale-95 flex items-center gap-2 border border-slate-700 dark:border-white/20">
                                <Upload className="w-6 h-6" /> Submit Project
                            </button>
                        ) : (
                            <div className="h-14 md:h-16 px-6 md:px-8 rounded-[1rem] md:rounded-[1.25rem] bg-green-500/20 backdrop-blur-xl border border-green-500/30 shadow-lg flex items-center gap-3 text-base font-bold text-green-700 dark:text-green-400">
                                <CheckCircle2 className="w-6 h-6" /> {hasSubmitted ? 'Submitted!' : 'Joined'}
                            </div>
                        )}
                    </>
                }
                tabsAndTimers={
                    <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-6 border-b-2 border-slate-200/50 dark:border-white/5 pb-0 min-h-[4rem]">
                        <div className="flex items-center gap-8 md:gap-10">
                            <button
                                onClick={() => setActiveTab('overview')}
                                className={`pb-4 text-base font-black uppercase tracking-widest transition-colors ${activeTab === 'overview' ? 'border-modtale-accent text-modtale-accent border-b-4 -mb-[2px]' : 'border-transparent text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'}`}
                            >
                                Overview
                            </button>
                            <button
                                onClick={() => setActiveTab('entries')}
                                className={`pb-4 text-base font-black uppercase tracking-widest transition-all flex items-center gap-2.5 ${activeTab === 'entries' ? 'border-modtale-accent text-modtale-accent border-b-4 -mb-[2px]' : 'border-transparent text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'}`}
                            >
                                Entries <span className={`px-2.5 py-0.5 rounded-full text-[11px] ml-1 transition-colors ${activeTab === 'entries' ? 'bg-modtale-accent/20 text-modtale-accent' : 'bg-slate-200 dark:bg-white/10'}`}>{submissions.length}</span>
                            </button>
                        </div>

                        <div className={`pb-4 hidden md:block transition-opacity duration-200 ${activeTab === 'overview' ? 'opacity-100' : 'opacity-0 pointer-events-none select-none'}`}>
                            <CompactCountdown jam={jam} now={now} />
                        </div>
                    </div>
                }
                mainContent={
                    <div className="animate-in fade-in slide-in-from-bottom-2 mt-10">
                        {activeTab === 'overview' ? (
                            <div className="space-y-10">
                                <div className="block md:hidden">
                                    <CompactCountdown jam={jam} now={now} />
                                </div>

                                <div className="prose dark:prose-invert prose-lg max-w-none bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-[2.5rem] p-8 md:p-10 shadow-sm">
                                    <p className="whitespace-pre-wrap leading-relaxed">{jam.description || "No description provided."}</p>
                                </div>

                                {jam.categories.length > 0 && (
                                    <div className="mt-12">
                                        <h3 className="text-xl md:text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-3 px-2">
                                            <Scale className="w-6 h-6 text-modtale-accent" /> Judging Criteria
                                        </h3>
                                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                                            {jam.categories.map(cat => (
                                                <div key={cat.id} className="p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 shadow-sm transition-colors hover:border-modtale-accent/30 group">
                                                    <div className="flex justify-between items-start mb-4">
                                                        <h4 className="text-lg font-bold text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors">{cat.name}</h4>
                                                        <span className="text-[11px] font-black bg-modtale-accent/10 text-modtale-accent px-3 py-1.5 rounded-xl uppercase tracking-widest shrink-0 ml-4">Max {cat.maxScore}</span>
                                                    </div>
                                                    <p className="text-base text-slate-500 font-medium leading-relaxed">{cat.description || "No description provided."}</p>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div className="space-y-10">
                                <EventTimeline jam={jam} now={now} />

                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-8">
                                    {sortedSubmissions.map(sub => {
                                        const resolvedProjectImage = resolveUrl(sub.projectImageUrl);
                                        const resolvedProjectBanner = resolveUrl(sub.projectBannerUrl);
                                        const isMySubmission = sub.submitterId === currentUser?.id;

                                        return (
                                            <div
                                                key={sub.id}
                                                onClick={() => navigate(`/mod/${sub.projectId}`)}
                                                className="bg-white/60 dark:bg-slate-900/40 backdrop-blur-2xl border border-white/40 dark:border-white/10 rounded-[2.5rem] overflow-hidden flex flex-col group shadow-xl shadow-slate-200/20 dark:shadow-none hover:-translate-y-1.5 hover:border-modtale-accent/50 dark:hover:border-modtale-accent/50 transition-all duration-300 relative cursor-pointer"
                                            >
                                                <div className="block relative w-full h-36 bg-slate-200 dark:bg-slate-800 shrink-0 overflow-hidden">
                                                    {resolvedProjectBanner ? (
                                                        <img src={resolvedProjectBanner} alt="" className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105 opacity-90" />
                                                    ) : (
                                                        <div className="w-full h-full bg-gradient-to-br from-indigo-500/20 to-purple-500/20" />
                                                    )}
                                                    <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/10 to-transparent" />
                                                </div>

                                                <div className="px-6 flex-1 flex flex-col relative z-10 items-center text-center -mt-10">
                                                    <div className="block w-20 h-20 rounded-[1.25rem] bg-white dark:bg-slate-900 shadow-xl border-[4px] border-white/90 dark:border-slate-800 overflow-hidden relative group-hover:scale-105 transition-transform mb-4 shrink-0">
                                                        {resolvedProjectImage ? (
                                                            <img src={resolvedProjectImage} alt={sub.projectTitle} className="w-full h-full object-cover" />
                                                        ) : (
                                                            <div className="w-full h-full flex items-center justify-center text-slate-400 bg-slate-100 dark:bg-slate-800">
                                                                <LayoutGrid className="w-8 h-8 opacity-20" />
                                                            </div>
                                                        )}
                                                    </div>

                                                    <h3 className="text-2xl font-black text-slate-900 dark:text-white truncate w-full group-hover:text-modtale-accent transition-colors">
                                                        {sub.projectTitle}
                                                    </h3>

                                                    <div className="flex items-center justify-center gap-1 mt-2 mb-4">
                                                        <Link
                                                            to={`/creator/${sub.projectAuthor}`}
                                                            onClick={(e) => e.stopPropagation()}
                                                            className="text-xs font-bold text-slate-600 dark:text-slate-400 hover:text-modtale-accent hover:underline transition-colors bg-white/50 dark:bg-white/5 px-2.5 py-1 rounded-xl border border-slate-200/50 dark:border-white/5 relative z-20"
                                                        >
                                                            by {sub.projectAuthor || 'Unknown'}
                                                        </Link>
                                                    </div>

                                                    <p className="text-base text-slate-600 dark:text-slate-400 line-clamp-2 leading-relaxed mb-6">
                                                        {sub.projectDescription || 'No description provided.'}
                                                    </p>
                                                </div>

                                                <div className="mt-auto px-6 py-5 bg-white/40 dark:bg-black/20 border-t border-slate-200/50 dark:border-white/5 flex items-center justify-between relative z-10 backdrop-blur-md">
                                                    <div className="flex items-center gap-2">
                                                        {canSeeResults && sub.totalScore !== undefined ? (
                                                            <div className="flex flex-col text-left">
                                                                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">{jam.status === 'COMPLETED' ? 'Final Score' : 'Score'}</span>
                                                                <span className="text-xl font-black text-slate-900 dark:text-white leading-none mt-1 drop-shadow-sm">{sub.totalScore.toFixed(2)}</span>
                                                            </div>
                                                        ) : (
                                                            <div className="flex flex-col text-left opacity-50">
                                                                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Score</span>
                                                                <span className="text-xl font-black text-slate-500 leading-none mt-1">---</span>
                                                            </div>
                                                        )}
                                                    </div>

                                                    <div className="flex items-center gap-3">
                                                        {canSeeResults && sub.rank && (
                                                            <div className="flex items-center gap-1.5 text-modtale-accent font-black text-xl drop-shadow-sm">
                                                                <Trophy className="w-5 h-5"/> #{sub.rank}
                                                            </div>
                                                        )}

                                                        {canVote && !isMySubmission && (
                                                            <button
                                                                onClick={(e) => { e.preventDefault(); e.stopPropagation(); setVotingSubmissionId(sub.id); }}
                                                                className="px-5 py-2.5 bg-modtale-accent hover:bg-modtale-accentHover text-white text-sm font-black rounded-xl shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 flex items-center gap-2 ml-2 relative z-20"
                                                            >
                                                                <Star className="w-4 h-4" /> Vote
                                                            </button>
                                                        )}
                                                        {isMySubmission && (
                                                            <div className="px-4 py-2 bg-green-500/10 text-green-600 dark:text-green-400 text-[11px] font-black uppercase tracking-widest rounded-xl flex items-center gap-2 ml-2 border border-green-500/20 shadow-sm relative z-20">
                                                                <CheckCircle2 className="w-4 h-4" /> Yours
                                                            </div>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        );
                                    })}
                                    {submissions.length === 0 && (
                                        <div className="col-span-full py-24 text-center text-slate-500 bg-white/40 dark:bg-slate-900/40 backdrop-blur-xl border-2 border-dashed border-white/60 dark:border-white/10 rounded-[2.5rem]">
                                            <LayoutGrid className="w-16 h-16 mx-auto mb-6 opacity-20" />
                                            <p className="text-xl font-black text-slate-700 dark:text-slate-300">No submissions yet.</p>
                                            <p className="text-base font-medium mt-2">Be the first to submit a project!</p>
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>
                }
            />
        </>
    );
};