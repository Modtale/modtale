import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { api, BACKEND_URL } from '@/utils/api';
import type { Modjam, ModjamSubmission, User, Mod } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { Trophy, Calendar, Users, Upload, CheckCircle2, LayoutGrid, AlertCircle, Scale, Settings, Star } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';
import { JamBuilder } from '@/components/resources/upload/JamBuilder';
import { JamSubmissionWizard } from '@/react-pages/jams/JamSubmissionWizard';

export const JamDetail: React.FC<{ currentUser: User | null }> = ({ currentUser }) => {
    const { slug } = useParams<{ slug: string }>();
    const navigate = useNavigate();

    const [jam, setJam] = useState<Modjam | null>(null);
    const [submissions, setSubmissions] = useState<ModjamSubmission[]>([]);
    const [loading, setLoading] = useState(true);
    const [myProjects, setMyProjects] = useState<Mod[]>([]);

    const [isSubmittingModalOpen, setIsSubmittingModalOpen] = useState(false);
    const [votingSubmissionId, setVotingSubmissionId] = useState<string | null>(null);

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
    const [activeTab, setActiveTab] = useState<'details' | 'categories' | 'settings'>('details');
    const [isSavingJam, setIsSavingJam] = useState(false);

    const resolveUrl = (url?: string | null) => {
        if (!url) return '';
        if (url.startsWith('/api') || url.startsWith('/uploads')) {
            return `${BACKEND_URL}${url}`;
        }
        return url;
    };

    useEffect(() => {
        const fetchJamData = async () => {
            try {
                const res = await api.get(`/modjams/${slug}`);
                setJam(res.data);

                const subRes = await api.get(`/modjams/${res.data.id}/submissions`);
                setSubmissions(subRes.data);
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
                activeTab={activeTab}
                setActiveTab={setActiveTab}
                onBack={() => setIsEditing(false)}
            />
        );
    }

    const isHost = currentUser?.id === jam.hostId;
    const isParticipating = currentUser?.id && (jam.participantIds || []).includes(currentUser.id);
    const hasSubmitted = submissions.some(s => s.submitterId === currentUser?.id);

    const canVote = (jam.status === 'VOTING' || (jam.status === 'ACTIVE' && jam.allowConcurrentVoting)) && (jam.allowPublicVoting || isHost);
    const canSeeResults = jam.status === 'COMPLETED' || jam.showResultsBeforeVotingEnds || isHost;

    const isPast = (dateString: string) => new Date(dateString) < new Date();

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
                    <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl w-full max-w-md rounded-[2.5rem] shadow-2xl border border-slate-200 dark:border-white/10 overflow-hidden flex flex-col">
                        <div className="p-8 border-b border-slate-200 dark:border-white/10 text-center">
                            <div className="w-16 h-16 rounded-2xl bg-slate-100 dark:bg-slate-800 shadow-sm overflow-hidden mx-auto mb-4 border-2 border-white dark:border-slate-700">
                                {activeVotingSub.projectImageUrl ? (
                                    <img src={resolveUrl(activeVotingSub.projectImageUrl)} className="w-full h-full object-cover" alt="" />
                                ) : (
                                    <LayoutGrid className="w-8 h-8 m-auto mt-3.5 text-slate-400 opacity-20" />
                                )}
                            </div>
                            <h3 className="text-2xl font-black text-slate-900 dark:text-white">Cast Your Vote</h3>
                            <p className="text-sm font-medium text-slate-500 mt-1">Voting on <span className="text-modtale-accent font-bold">{activeVotingSub.projectTitle}</span></p>
                        </div>
                        <div className="p-8 overflow-y-auto max-h-[50vh] space-y-6 custom-scrollbar">
                            {jam.categories.map(cat => {
                                const myVote = activeVotingSub.votes?.find(v => v.voterId === currentUser?.id && v.categoryId === cat.id);
                                return (
                                    <div key={cat.id} className="space-y-3">
                                        <div className="flex items-center justify-between">
                                            <span className="text-sm font-black text-slate-900 dark:text-white">{cat.name}</span>
                                            <span className="text-[10px] font-black uppercase tracking-widest text-slate-400">Max {cat.maxScore}</span>
                                        </div>
                                        <div className="flex flex-wrap items-center gap-2">
                                            {[...Array(cat.maxScore)].map((_, i) => (
                                                <button
                                                    key={i}
                                                    onClick={() => handleVote(activeVotingSub.id, cat.id, i + 1)}
                                                    className={`flex-1 h-12 rounded-xl flex items-center justify-center font-black text-sm transition-all shadow-sm ${myVote?.score === i + 1 ? 'bg-modtale-accent text-white scale-105 ring-4 ring-modtale-accent/30' : 'bg-white dark:bg-slate-800 text-slate-500 hover:bg-slate-50 dark:hover:bg-slate-700 hover:text-slate-900 dark:hover:text-white border border-slate-200 dark:border-white/5'}`}
                                                >
                                                    {i + 1}
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                )
                            })}
                        </div>
                        <div className="p-6 border-t border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-black/20">
                            <button onClick={() => setVotingSubmissionId(null)} className="w-full h-14 bg-slate-900 dark:bg-white text-white dark:text-slate-900 rounded-2xl font-black transition-all hover:scale-[1.02] active:scale-95 shadow-lg">
                                Done Voting
                            </button>
                        </div>
                    </div>
                </div>
            )}

            <JamLayout
                bannerUrl={jam.bannerUrl}
                iconUrl={(jam as any).imageUrl || "https://modtale.net/assets/favicon.svg"}
                onBack={() => navigate('/jams')}
                headerContent={
                    <>
                        <div className="flex flex-wrap items-center gap-3 mb-3">
                            <h1 className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tighter drop-shadow-sm leading-tight break-words">{jam.title}</h1>
                            <span className="bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 px-4 py-1.5 rounded-full text-xs font-black text-modtale-accent tracking-widest uppercase flex items-center gap-2 shadow-sm whitespace-nowrap">
                                <Trophy className="w-4 h-4" /> Modjam
                            </span>
                        </div>

                        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm font-medium text-slate-600 dark:text-slate-400 mb-6">
                            <div className="flex items-center gap-2">
                                <span>Hosted by <button onClick={() => navigate(`/creator/${jam.hostName}`)} className="font-bold text-slate-800 dark:text-white hover:text-modtale-accent hover:underline decoration-2 underline-offset-4 transition-all">{jam.hostName}</button></span>
                            </div>
                        </div>

                        <p className="text-slate-700 dark:text-slate-300 text-lg leading-relaxed max-w-4xl font-medium">
                            {jam.description}
                        </p>
                    </>
                }
                actionBar={
                    <div className="flex flex-col md:flex-row items-center justify-between gap-6 w-full p-6 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/5 rounded-2xl">
                        {!currentUser ? (
                            <div className="flex items-center gap-3 text-slate-600 dark:text-slate-400 font-bold">
                                <AlertCircle className="w-5 h-5 text-modtale-accent" />
                                Sign in to participate in this jam.
                            </div>
                        ) : !isParticipating ? (
                            <button onClick={handleJoin} className="w-full md:w-auto bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-4 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 transition-all active:scale-95">
                                <Users className="w-5 h-5" /> Join Jam
                            </button>
                        ) : (
                            <div className="w-full flex flex-col md:flex-row items-center justify-between gap-4">
                                <div className="flex items-center gap-2 text-green-600 dark:text-green-500 font-black px-4 py-2 rounded-xl bg-green-50 dark:bg-green-500/10 border border-green-200 dark:border-green-500/20">
                                    <CheckCircle2 className="w-5 h-5" /> You are participating
                                </div>

                                {jam.status === 'ACTIVE' && !hasSubmitted && (
                                    <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 w-full md:w-auto">
                                        <button
                                            onClick={() => setIsSubmittingModalOpen(true)}
                                            className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-8 py-3 rounded-xl font-black flex items-center justify-center gap-2 hover:scale-105 active:scale-95 transition-all shadow-md"
                                        >
                                            <Upload className="w-4 h-4" /> Submit a Project
                                        </button>
                                    </div>
                                )}

                                {hasSubmitted && (
                                    <div className="text-sm font-bold text-slate-500 dark:text-slate-400">
                                        Your project is submitted!
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                }
                sidebarContent={
                    <div className="flex flex-col gap-6">
                        {isHost && (
                            <div className="flex flex-col gap-3 p-6 bg-modtale-accent/5 dark:bg-modtale-accent/10 rounded-[2rem] border border-modtale-accent/20">
                                <h3 className="text-sm font-black text-modtale-accent uppercase tracking-widest flex items-center gap-2 mb-2">
                                    <Settings className="w-4 h-4" /> Host Controls
                                </h3>
                                <button onClick={startEditing} className="w-full py-3 bg-white dark:bg-slate-900 rounded-xl font-bold text-sm hover:text-modtale-accent transition-colors shadow-sm">
                                    Edit Event Details
                                </button>
                                <button onClick={handleDelete} className="w-full py-3 bg-red-50 dark:bg-red-500/10 text-red-600 dark:text-red-400 rounded-xl font-bold text-sm hover:bg-red-100 dark:hover:bg-red-500/20 transition-colors shadow-sm">
                                    Delete Event
                                </button>
                            </div>
                        )}

                        <div className="relative overflow-hidden p-6 rounded-[2rem] border border-slate-200 dark:border-white/10 bg-gradient-to-br from-slate-100 to-slate-50 dark:from-slate-800/50 dark:to-slate-900/50 shadow-lg">
                            <div className="relative z-10 flex flex-col items-center justify-center text-center">
                                <span className="text-xs font-black text-slate-500 uppercase tracking-widest mb-2">Current Status</span>
                                <span className="text-3xl font-black text-modtale-accent uppercase tracking-widest drop-shadow-sm">{jam.status}</span>
                            </div>
                        </div>

                        <div className="p-6 bg-white/60 dark:bg-slate-900/40 backdrop-blur-xl rounded-[2rem] border border-slate-200 dark:border-white/10 shadow-xl">
                            <h3 className="text-sm font-black text-slate-900 dark:text-white uppercase tracking-widest mb-6 flex items-center gap-2">
                                <Calendar className="w-4 h-4 text-modtale-accent" /> Timeline
                            </h3>
                            <div className="space-y-6 relative before:absolute before:inset-0 before:ml-[11px] before:-translate-x-px before:h-full before:w-0.5 before:bg-gradient-to-b before:from-slate-200 before:via-slate-200 before:to-transparent dark:before:from-white/10 dark:before:via-white/10">
                                <div className="relative flex items-center gap-4">
                                    <div className={`w-6 h-6 rounded-full border-4 border-slate-50 dark:border-slate-950 z-10 shrink-0 ${isPast(jam.startDate) ? 'bg-modtale-accent' : 'bg-slate-300 dark:bg-slate-700'}`} />
                                    <div className="flex flex-col">
                                        <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">Starts</span>
                                        <span className="text-sm font-black text-slate-900 dark:text-white">{new Date(jam.startDate).toLocaleDateString()}</span>
                                    </div>
                                </div>
                                <div className="relative flex items-center gap-4">
                                    <div className={`w-6 h-6 rounded-full border-4 border-slate-50 dark:border-slate-950 z-10 shrink-0 ${isPast(jam.endDate) ? 'bg-modtale-accent' : 'bg-slate-300 dark:bg-slate-700'}`} />
                                    <div className="flex flex-col">
                                        <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">Submissions Close</span>
                                        <span className="text-sm font-black text-slate-900 dark:text-white">{new Date(jam.endDate).toLocaleDateString()}</span>
                                    </div>
                                </div>
                                <div className="relative flex items-center gap-4">
                                    <div className={`w-6 h-6 rounded-full border-4 border-slate-50 dark:border-slate-950 z-10 shrink-0 ${isPast(jam.votingEndDate) ? 'bg-modtale-accent' : 'bg-slate-300 dark:bg-slate-700'}`} />
                                    <div className="flex flex-col">
                                        <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">Voting Ends</span>
                                        <span className="text-sm font-black text-slate-900 dark:text-white">{new Date(jam.votingEndDate).toLocaleDateString()}</span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div className="flex flex-col items-center justify-center p-6 bg-white/60 dark:bg-slate-900/40 backdrop-blur-xl rounded-[2rem] border border-slate-200 dark:border-white/10 shadow-lg">
                                <Users className="w-6 h-6 text-modtale-accent mb-2" />
                                <span className="text-3xl font-black text-slate-900 dark:text-white mb-1">{(jam.participantIds || []).length}</span>
                                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Joined</span>
                            </div>
                            <div className="flex flex-col items-center justify-center p-6 bg-white/60 dark:bg-slate-900/40 backdrop-blur-xl rounded-[2rem] border border-slate-200 dark:border-white/10 shadow-lg">
                                <Upload className="w-6 h-6 text-modtale-accent mb-2" />
                                <span className="text-3xl font-black text-slate-900 dark:text-white mb-1">{submissions.length}</span>
                                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Submissions</span>
                            </div>
                        </div>

                        {(jam.categories || []).length > 0 && (
                            <div className="p-6 bg-white/60 dark:bg-slate-900/40 backdrop-blur-xl rounded-[2rem] border border-slate-200 dark:border-white/10 shadow-xl">
                                <h3 className="text-sm font-black text-slate-900 dark:text-white uppercase tracking-widest mb-6 flex items-center gap-2">
                                    <Scale className="w-4 h-4 text-modtale-accent" /> Judging Criteria
                                </h3>
                                <div className="space-y-3">
                                    {jam.categories.map((cat, idx) => (
                                        <div key={idx} className="p-4 bg-white/50 dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-white/5 transition-all hover:border-modtale-accent/50 group">
                                            <div className="flex items-center justify-between mb-1">
                                                <span className="text-sm font-black text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors">{cat.name}</span>
                                                <span className="text-[10px] font-black text-modtale-accent uppercase tracking-widest bg-modtale-accent/10 px-2.5 py-1 rounded-lg">Max {cat.maxScore}</span>
                                            </div>
                                            {cat.description && <span className="text-xs font-medium text-slate-500">{cat.description}</span>}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                }
                mainContent={
                    <>
                        <h2 className="text-2xl font-black mb-8 flex items-center gap-3 text-slate-900 dark:text-white border-b border-slate-200 dark:border-white/5 pb-4">
                            <LayoutGrid className="w-6 h-6 text-modtale-accent" /> Submissions
                        </h2>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {sortedSubmissions.map(sub => {
                                const resolvedProjectImage = resolveUrl(sub.projectImageUrl);
                                const resolvedProjectBanner = resolveUrl(sub.projectBannerUrl);
                                const isMySubmission = sub.submitterId === currentUser?.id;

                                return (
                                    <div key={sub.id} className="bg-white/60 dark:bg-slate-900/40 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden flex flex-col group shadow-sm transition-all hover:shadow-lg hover:border-modtale-accent/50 dark:hover:border-modtale-accent/50 relative">
                                        <Link to={`/mod/${sub.projectId}`} className="block relative w-full aspect-[3/1] bg-slate-100 dark:bg-slate-800 border-b border-slate-200/50 dark:border-white/5">
                                            {resolvedProjectBanner ? (
                                                <img src={resolvedProjectBanner} alt="" className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105" />
                                            ) : (
                                                <div className="w-full h-full bg-slate-200/50 dark:bg-slate-700/50" />
                                            )}
                                        </Link>

                                        <div className="flex px-4 relative">
                                            <div className="flex-shrink-0 -mt-6 mb-2 relative z-10">
                                                <Link to={`/mod/${sub.projectId}`} className="block w-16 h-16 rounded-xl bg-white dark:bg-slate-800 shadow-md border-[3px] border-white dark:border-slate-900 overflow-hidden relative group-hover:scale-105 transition-transform">
                                                    {resolvedProjectImage ? (
                                                        <img src={resolvedProjectImage} alt={sub.projectTitle} className="w-full h-full object-cover" />
                                                    ) : (
                                                        <div className="w-full h-full flex items-center justify-center text-slate-400 bg-slate-100 dark:bg-slate-800">
                                                            <LayoutGrid className="w-6 h-6 opacity-20" />
                                                        </div>
                                                    )}
                                                </Link>
                                            </div>
                                            <div className="flex-1 min-w-0 pt-2 pl-3 flex justify-between items-start gap-2">
                                                <div className="min-w-0 flex-1">
                                                    <Link to={`/mod/${sub.projectId}`} className="text-lg font-black text-slate-900 dark:text-white truncate group-hover:text-modtale-accent transition-colors block">
                                                        {sub.projectTitle}
                                                    </Link>
                                                    <div className="flex items-center gap-1 text-[11px] text-slate-500 font-medium truncate mt-0.5">
                                                        <span>by</span>
                                                        <Link to={`/creator/${sub.projectAuthor}`} onClick={(e) => e.stopPropagation()} className="text-slate-700 dark:text-slate-300 font-bold hover:text-modtale-accent transition-colors">
                                                            {sub.projectAuthor || 'Unknown'}
                                                        </Link>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="px-4 pb-4 flex-1 flex flex-col">
                                            {sub.projectDescription && (
                                                <p className="text-xs text-slate-600 dark:text-slate-400 line-clamp-2 leading-relaxed mt-1">
                                                    {sub.projectDescription}
                                                </p>
                                            )}
                                        </div>

                                        <div className="mt-auto px-4 py-3 bg-slate-50/50 dark:bg-white/[0.02] border-t border-slate-200/50 dark:border-white/5 flex items-center justify-between">
                                            <div className="flex items-center gap-2">
                                                {canSeeResults && sub.totalScore !== undefined ? (
                                                    <div className="flex flex-col">
                                                        <span className="text-[9px] font-black text-slate-400 uppercase tracking-widest">{jam.status === 'COMPLETED' ? 'Final Score' : 'Score'}</span>
                                                        <span className="text-sm font-black text-slate-900 dark:text-white leading-none mt-0.5">{sub.totalScore.toFixed(2)}</span>
                                                    </div>
                                                ) : (
                                                    <div className="flex flex-col opacity-50">
                                                        <span className="text-[9px] font-black text-slate-400 uppercase tracking-widest">Score</span>
                                                        <span className="text-sm font-black text-slate-500 leading-none mt-0.5">---</span>
                                                    </div>
                                                )}
                                            </div>

                                            <div className="flex items-center">
                                                {canVote && !isMySubmission && (
                                                    <button
                                                        onClick={(e) => { e.preventDefault(); setVotingSubmissionId(sub.id); }}
                                                        className="px-4 py-1.5 bg-modtale-accent hover:bg-modtale-accentHover text-white text-xs font-black rounded-lg shadow-sm transition-all active:scale-95 flex items-center gap-1.5"
                                                    >
                                                        <Star className="w-3 h-3" /> Vote
                                                    </button>
                                                )}
                                                {isMySubmission && (
                                                    <div className="px-2.5 py-1 bg-green-500/10 text-green-600 dark:text-green-400 text-[10px] font-black uppercase tracking-widest rounded-md flex items-center gap-1">
                                                        <CheckCircle2 className="w-3 h-3" /> Yours
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        {submissions.length === 0 && (
                            <div className="text-center py-20 text-slate-500 border-2 border-dashed border-slate-200 dark:border-white/5 rounded-3xl">
                                <LayoutGrid className="w-12 h-12 mx-auto mb-4 opacity-20" />
                                <p className="text-lg font-black text-slate-700 dark:text-slate-300">No submissions yet.</p>
                                <p className="text-sm font-medium mt-1">Be the first to submit a project!</p>
                            </div>
                        )}
                    </>
                }
            />
        </>
    );
};