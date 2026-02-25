import React, { useEffect, useState, useMemo } from 'react';
import { useParams, useNavigate, Link, useLocation } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import { api, BACKEND_URL } from '@/utils/api';
import type { Modjam, ModjamSubmission, User, Mod } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { Trophy, Users, Upload, LayoutGrid, AlertCircle, Scale, Star, Edit3, Trash2, Clock, CheckCircle2, ChevronRight, X, Crown, Check } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';
import { JamBuilder } from '@/components/jams/JamBuilder.tsx';
import { JamSubmissionWizard } from '@/react-pages/jams/JamSubmissionWizard';

const EventTimeline: React.FC<{ jam: Modjam, now: number }> = ({ jam, now }) => {
    if (now === 0) return null;

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

    if (jam.status === 'COMPLETED') {
        return null;
    } else if (now < start) {
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
        label = 'Voting Closed';
        progress = 100;
    }

    const diff = target - now;
    let timeStr = '--';

    if (diff > 0 && jam.status !== 'AWAITING_WINNERS') {
        const days = Math.floor(diff / (1000 * 60 * 60 * 24));
        const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
        const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
        const seconds = Math.floor((diff % (1000 * 60)) / 1000);
        const pad = (n: number) => n.toString().padStart(2, '0');
        timeStr = days > 0 ? `${days}d ${pad(hours)}h ${pad(minutes)}m` : `${pad(hours)}h ${pad(minutes)}m ${pad(seconds)}s`;
    } else {
        timeStr = 'Awaiting Winners';
    }

    const [hydrated, setHydrated] = useState(false);
    useEffect(() => {
        setHydrated(true);
    }, []);

    if (!hydrated) {
        return (
            <div className="flex flex-col md:flex-row items-center gap-8 md:gap-12 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-2xl p-8 shadow-sm w-full">
                <div className="flex flex-col shrink-0 min-w-[220px] text-center md:text-left opacity-0">
                    <span className="text-[11px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400 mb-2 flex items-center justify-center md:justify-start gap-2">
                        <Clock className="w-4 h-4" /> {label}
                    </span>
                    <span className={`text-3xl md:text-4xl font-black font-mono drop-shadow-sm leading-none text-modtale-accent`}>--</span>
                </div>
                <div className="flex-1 w-full pt-6 md:pt-0 pl-0 md:pl-8 opacity-0">
                    <div className="relative flex items-start justify-between w-full">
                        <div className="absolute top-3 left-14 right-14 h-2 bg-slate-200/50 dark:bg-slate-800 rounded-full overflow-hidden">
                            <div className="h-full bg-modtale-accent transition-all duration-1000 rounded-full" style={{ width: `0%` }} />
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="flex flex-col md:flex-row items-center gap-8 md:gap-12 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-2xl p-8 shadow-sm w-full mb-10">
            <div className="flex flex-col shrink-0 min-w-[220px] text-center md:text-left">
                <span className="text-[11px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400 mb-2 flex items-center justify-center md:justify-start gap-2">
                    <Clock className="w-4 h-4" /> {label}
                </span>
                <span className={`text-3xl md:text-4xl font-black font-mono drop-shadow-sm leading-none text-modtale-accent`}>{timeStr}</span>
            </div>

            <div className="flex-1 w-full pt-6 md:pt-0 pl-0 md:pl-8">
                <div className="relative flex items-start justify-between w-full">
                    <div className="absolute top-3 left-14 right-14 h-2 bg-slate-200/50 dark:bg-slate-800 rounded-full overflow-hidden">
                        <div className="h-full bg-modtale-accent transition-all duration-1000 rounded-full" style={{ width: `${progress}%` }} />
                    </div>
                    {phases.map((phase, idx) => {
                        const isPast = now >= phase.time || jam.status === 'AWAITING_WINNERS';
                        const isCurrent = jam.status !== 'AWAITING_WINNERS' && (
                            (idx === 0 && now >= start && now < end) ||
                            (idx === 1 && now >= end && now < voting) ||
                            (idx === 2 && now >= voting)
                        );

                        return (
                            <div key={phase.label} className="flex flex-col items-center w-28 relative z-10">
                                <div className={`w-8 h-8 rounded-full border-[5px] transition-colors bg-white dark:bg-slate-900 mb-3.5 flex items-center justify-center ${isPast ? 'border-modtale-accent' : 'border-slate-300 dark:border-slate-700'} ${isCurrent ? 'ring-4 ring-modtale-accent/20 scale-110' : ''}`} />
                                <span className={`text-[11px] font-black uppercase tracking-widest transition-colors ${isPast || isCurrent ? 'text-slate-700 dark:text-slate-200' : 'text-slate-400'}`}>{phase.label}</span>
                                <span suppressHydrationWarning className="text-[10px] font-bold text-slate-400 mt-1">{phase.dateStr}</span>
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
    const location = useLocation();

    const [jam, setJam] = useState<Modjam | null>(null);
    const [submissions, setSubmissions] = useState<ModjamSubmission[]>([]);
    const [loading, setLoading] = useState(true);
    const [myProjects, setMyProjects] = useState<Mod[]>([]);
    const [isFollowing, setIsFollowing] = useState(false);

    const [isSubmittingModalOpen, setIsSubmittingModalOpen] = useState(false);
    const [votingSubmissionId, setVotingSubmissionId] = useState<string | null>(null);
    const [pickingWinners, setPickingWinners] = useState(false);
    const [now, setNow] = useState(0);

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

    const activeTab = useMemo(() => {
        if (location.pathname.endsWith('/entries')) return 'entries';
        if (location.pathname.endsWith('/overview')) return 'overview';
        if (jam && ['VOTING', 'COMPLETED', 'AWAITING_WINNERS'].includes(jam.status)) return 'entries';
        return 'overview';
    }, [location.pathname, jam?.status]);

    useEffect(() => {
        setNow(new Date().getTime());
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

                if (currentUser) {
                    try {
                        const followRes = await api.get(`/user/following/${res.data.hostName}`);
                        setIsFollowing(followRes.data);
                    } catch (e) {}
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

    const handleFollowToggle = async () => {
        if (!jam || !currentUser) return;
        try {
            if (isFollowing) {
                await api.post(`/user/unfollow/${jam.hostName}`);
                setIsFollowing(false);
            } else {
                await api.post(`/user/follow/${jam.hostName}`);
                setIsFollowing(true);
            }
        } catch (err) {
            console.error("Follow toggle failed", err);
        }
    };

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
        if (!jam || !currentUser) return;

        if (jam.votingEndDate && new Date().getTime() > new Date(jam.votingEndDate).getTime()) {
            setStatusModal({ type: 'error', title: 'Voting Closed', message: 'The voting period has ended for this jam.' });
            setVotingSubmissionId(null);
            return;
        }

        const previousSubmissions = [...submissions];

        const optimisticSubmissions = submissions.map(s => {
            if (s.id === submissionId) {
                const votes = [...(s.votes || [])];
                const existingVoteIdx = votes.findIndex(v => v.voterId === currentUser.id && v.categoryId === categoryId);

                if (existingVoteIdx > -1) {
                    votes[existingVoteIdx] = { ...votes[existingVoteIdx], score };
                } else {
                    votes.push({ id: 'temp-id', voterId: currentUser.id, categoryId, score });
                }
                return { ...s, votes };
            }
            return s;
        });

        setSubmissions(optimisticSubmissions);

        try {
            const res = await api.post(`/modjams/${jam.id}/vote`, { submissionId, categoryId, score });

            setSubmissions(prev => prev.map(s => s.id === submissionId ? res.data : s));
        } catch (err: any) {
            setSubmissions(previousSubmissions);
            setStatusModal({
                type: 'error',
                title: 'Vote Failed',
                message: err.response?.data?.message || 'Failed to record vote.'
            });
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

    const handleFinalizeJam = async (winners: { submissionId: string, awardTitle: string }[]) => {
        if (!jam) return;
        setIsSavingJam(true);
        try {
            await api.post(`/modjams/${jam.id}/finalize`, winners);
            window.location.reload();
        } catch (e) {
            setStatusModal({ type: 'error', title: 'Error', message: 'Failed to finalize jam and pick winners.' });
            setIsSavingJam(false);
        }
    };

    const memoizedDescription = useMemo(() => {
        if (!jam?.description) return <p className="text-slate-500 italic">No description provided.</p>;

        return (
            <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                rehypePlugins={[rehypeRaw, [rehypeSanitize, {
                    ...defaultSchema,
                    attributes: {
                        ...defaultSchema.attributes,
                        code: ['className']
                    }
                }]]}
                components={{
                    code({node, inline, className, children, ...props}: any) {
                        const match = /language-(\w+)/.exec(className || '')
                        return !inline && match ? (
                            <SyntaxHighlighter
                                {...props}
                                style={vscDarkPlus}
                                language={match[1]}
                                PreTag="div"
                                className="rounded-lg text-sm"
                            >
                                {String(children).replace(/\n$/, '')}
                            </SyntaxHighlighter>
                        ) : (
                            <code className={`${className || ''} bg-slate-100 dark:bg-white/10 px-1 py-0.5 rounded text-sm`} {...props}>
                                {children}
                            </code>
                        )
                    },
                    p({node, children, ...props}: any) {
                        return <p className="my-2 [li>&]:my-0" {...props}>{children}</p>
                    },
                    li({node, children, ...props}: any) {
                        return <li className="my-1 [&>p]:my-0" {...props}>{children}</li>
                    },
                    ul({node, children, ...props}: any) {
                        return <ul className="list-disc pl-6 my-3" {...props}>{children}</ul>
                    },
                    ol({node, children, ...props}: any) {
                        return <ol className="list-decimal pl-6 my-3" {...props}>{children}</ol>
                    }
                }}
            >
                {jam.description}
            </ReactMarkdown>
        );
    }, [jam?.description]);

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

    const isParticipating = currentUser?.id && (jam.participantIds || []).includes(currentUser.id);
    const hasSubmitted = submissions.some(s => s.submitterId === currentUser?.id);

    const votingClosed = jam.votingEndDate && now > new Date(jam.votingEndDate).getTime();
    const canVote = !votingClosed && jam.status !== 'COMPLETED' && jam.status !== 'AWAITING_WINNERS' && (jam.status === 'VOTING' || (jam.status === 'ACTIVE' && jam.allowConcurrentVoting)) && (jam.allowPublicVoting || currentUser?.id === jam.hostId);
    const canSeeResults = jam.status === 'COMPLETED' || jam.status === 'AWAITING_WINNERS' || jam.showResultsBeforeVotingEnds || currentUser?.id === jam.hostId;

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

            {votingSubmissionId && (
                <VotingModal
                    jam={jam}
                    submission={submissions.find(s => s.id === votingSubmissionId)}
                    currentUser={currentUser}
                    handleVote={handleVote}
                    onClose={() => setVotingSubmissionId(null)}
                />
            )}

            {pickingWinners && (
                <PickWinnersModal
                    submissions={submissions}
                    onClose={() => setPickingWinners(false)}
                    onSubmit={handleFinalizeJam}
                    isSaving={isSavingJam}
                />
            )}

            <JamDetailView
                jam={jam}
                submissions={submissions}
                currentUser={currentUser}
                isFollowing={isFollowing}
                handleFollowToggle={handleFollowToggle}
                startEditing={startEditing}
                handleDelete={handleDelete}
                isParticipating={isParticipating}
                hasSubmitted={hasSubmitted}
                handleJoin={handleJoin}
                setIsSubmittingModalOpen={setIsSubmittingModalOpen}
                setPickingWinners={setPickingWinners}
                activeTab={activeTab}
                now={now}
                canSeeResults={canSeeResults}
                canVote={canVote}
                setVotingSubmissionId={setVotingSubmissionId}
                memoizedDescription={memoizedDescription}
            />
        </>
    );
};

const JamDetailView: React.FC<{
    jam: Modjam,
    submissions: ModjamSubmission[],
    currentUser: User | null,
    isFollowing: boolean,
    handleFollowToggle: () => void,
    startEditing: () => void,
    handleDelete: () => void,
    isParticipating: boolean,
    hasSubmitted: boolean,
    handleJoin: () => void,
    setIsSubmittingModalOpen: (open: boolean) => void,
    setPickingWinners: (open: boolean) => void,
    activeTab: 'overview' | 'entries',
    now: number,
    canSeeResults: boolean,
    canVote: boolean,
    setVotingSubmissionId: (id: string | null) => void,
    memoizedDescription: React.ReactNode
}> = ({
          jam, submissions, currentUser, isFollowing, handleFollowToggle, startEditing, handleDelete,
          isParticipating, hasSubmitted, handleJoin, setIsSubmittingModalOpen, setPickingWinners, activeTab,
          now, canSeeResults, canVote, setVotingSubmissionId, memoizedDescription
      }) => {
    const navigate = useNavigate();
    const sortedSubmissions = useMemo(() => [...submissions].sort((a, b) => (b.totalScore || 0) - (a.totalScore || 0)), [submissions]);

    const winners = useMemo(() => sortedSubmissions.filter(s => (s as any).winner === true), [sortedSubmissions]);
    const regularEntries = useMemo(() => jam.status === 'COMPLETED' ? sortedSubmissions.filter(s => (s as any).winner !== true) : sortedSubmissions, [jam.status, sortedSubmissions]);

    const resolveUrl = (url?: string | null) => {
        if (!url) return '';
        if (url.startsWith('/api') || url.startsWith('/uploads')) {
            return `${BACKEND_URL}${url}`;
        }
        return url;
    };

    return (
        <JamLayout
            bannerUrl={jam.bannerUrl}
            iconUrl={(jam as any).imageUrl}
            backTo="/jams"
            titleContent={
                <h1 className="text-4xl md:text-5xl lg:text-6xl font-black text-slate-900 dark:text-white tracking-tighter drop-shadow-xl leading-tight">
                    {jam.title}
                </h1>
            }
            hostContent={
                <div className="flex flex-col gap-2 mt-2">
                    <div className="flex items-center gap-3">
                        <span className="text-base md:text-lg font-medium text-slate-600 dark:text-slate-400">by <Link to={`/creator/${jam.hostName}`} className="font-black text-slate-800 dark:text-white hover:text-modtale-accent hover:underline decoration-2 underline-offset-4 transition-all">{jam.hostName}</Link></span>
                        {currentUser && currentUser.username !== jam.hostName && (
                            <button
                                onClick={handleFollowToggle}
                                className={`h-6 px-3 rounded-lg text-[9px] uppercase font-black tracking-[0.1em] transition-all ${isFollowing ? 'bg-slate-200 dark:bg-white/10 text-slate-600 dark:text-slate-400 hover:bg-red-500/20 hover:text-red-600 dark:hover:text-red-500' : 'bg-modtale-accent text-white hover:bg-modtale-accentHover shadow-lg shadow-modtale-accent/20'}`}
                            >
                                {isFollowing ? 'Unfollow' : 'Follow'}
                            </button>
                        )}
                    </div>
                    <div className="flex items-center gap-4 text-[10px] font-black uppercase tracking-[0.1em] text-slate-500">
                        <span className="flex items-center gap-1.5"><Users className="w-3 h-3" /> {(jam.participantIds || []).length} participants</span>
                    </div>
                </div>
            }
            actionContent={
                <>
                    {currentUser?.id === jam.hostId && (
                        <div className="flex gap-2.5 shrink-0">
                            {(jam.status === 'AWAITING_WINNERS' || (jam.status === 'VOTING' && jam.votingEndDate && now > new Date(jam.votingEndDate).getTime())) && (
                                <button onClick={() => setPickingWinners(true)} className="h-12 md:h-14 px-6 rounded-xl bg-amber-500 hover:bg-amber-600 text-white font-black text-sm shadow-lg shadow-amber-500/20 flex items-center justify-center gap-2 transition-all hover:scale-105 active:scale-95" title="Finalize Jam">
                                    <Trophy className="w-4 h-4" /> Pick Winners
                                </button>
                            )}
                            <button onClick={startEditing} className="h-12 w-12 md:h-14 md:w-14 rounded-xl bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 hover:border-modtale-accent text-slate-900 dark:text-white shadow-sm flex items-center justify-center transition-all hover:scale-105 active:scale-95 group" title="Edit Jam">
                                <Edit3 className="w-5 h-5 group-hover:text-modtale-accent transition-colors" />
                            </button>
                            <button onClick={handleDelete} className="h-12 w-12 md:h-14 md:w-14 rounded-xl bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 hover:border-red-500 text-slate-900 dark:text-white shadow-sm flex items-center justify-center transition-all hover:scale-105 active:scale-95 group" title="Delete Jam">
                                <Trash2 className="w-5 h-5 group-hover:text-red-500 transition-colors" />
                            </button>
                        </div>
                    )}

                    {!currentUser ? (
                        <div className="h-12 md:h-14 px-6 md:px-8 rounded-xl bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 shadow-sm flex items-center gap-3 text-sm font-bold text-slate-600 dark:text-slate-300">
                            <AlertCircle className="w-4 h-4" /> Sign in to join
                        </div>
                    ) : !isParticipating ? (
                        <button onClick={handleJoin} className="h-12 md:h-14 px-8 md:px-10 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-xl font-black text-base md:text-lg shadow-lg shadow-modtale-accent/20 transition-all hover:scale-105 active:scale-95 flex items-center gap-2">
                            <Users className="w-5 h-5" /> Join Jam
                        </button>
                    ) : jam.status === 'ACTIVE' && !hasSubmitted ? (
                        <button onClick={() => setIsSubmittingModalOpen(true)} className="h-12 md:h-14 px-8 md:px-10 bg-slate-900 dark:bg-white text-white dark:text-slate-900 rounded-xl font-black text-base md:text-lg shadow-lg transition-all hover:scale-105 active:scale-95 flex items-center gap-2 border border-slate-700 dark:border-white/20">
                            <Upload className="w-6 h-6" /> Submit Project
                        </button>
                    ) : null}
                </>
            }
            tabsAndTimers={
                <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-6 border-b-2 border-slate-200/50 dark:border-white/5 pb-0 min-h-[4rem]">
                    <div className="flex items-center gap-8 md:gap-10">
                        <Link
                            to={`/jam/${jam.slug}/overview`}
                            className={`pb-4 text-base font-black uppercase tracking-widest transition-colors ${activeTab === 'overview' ? 'border-modtale-accent text-modtale-accent border-b-4 -mb-[2px]' : 'border-transparent text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'}`}
                        >
                            Overview
                        </Link>
                        <Link
                            to={`/jam/${jam.slug}/entries`}
                            className={`pb-4 text-base font-black uppercase tracking-widest transition-all flex items-center gap-2.5 ${activeTab === 'entries' ? 'border-modtale-accent text-modtale-accent border-b-4 -mb-[2px]' : 'border-transparent text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'}`}
                        >
                            Entries <span className={`px-2.5 py-0.5 rounded-full text-[11px] ml-1 transition-colors ${activeTab === 'entries' ? 'bg-modtale-accent/20 text-modtale-accent' : 'bg-slate-200 dark:bg-white/10'}`}>{submissions.length}</span>
                        </Link>
                    </div>

                    {jam.status === 'COMPLETED' && (
                        <div className="pb-4 flex items-center gap-2.5 text-slate-500">
                            <Clock className="w-4 h-4" />
                            <span className="text-[11px] font-black uppercase tracking-widest">Finished on {new Date(jam.updatedAt || jam.votingEndDate).toLocaleDateString()}</span>
                        </div>
                    )}
                </div>
            }
            mainContent={
                <div className="animate-in fade-in slide-in-from-bottom-2 mt-8 md:mt-10">
                    {jam.status === 'COMPLETED' && activeTab === 'overview' && (
                        <Link
                            to={`/jam/${jam.slug}/entries`}
                            className="bg-amber-500/10 border border-amber-500/30 text-amber-700 dark:text-amber-400 p-5 md:p-6 rounded-2xl flex items-center justify-between mb-10 backdrop-blur-md cursor-pointer hover:bg-amber-500/20 transition-all shadow-[0_0_20px_rgba(245,158,11,0.15)] group block w-full"
                        >
                            <div className="flex items-center gap-4">
                                <div className="p-3 bg-amber-500 text-white rounded-xl shadow-lg shadow-amber-500/40">
                                    <Trophy className="w-6 h-6 md:w-8 h-8" />
                                </div>
                                <div>
                                    <h3 className="font-black text-lg md:text-xl drop-shadow-sm">This jam has ended!</h3>
                                    <p className="font-medium text-sm md:text-base opacity-90 mt-0.5">Click here to view the results and winners.</p>
                                </div>
                            </div>
                            <ChevronRight className="w-6 h-6 transform group-hover:translate-x-1 transition-transform" />
                        </Link>
                    )}

                    {activeTab === 'overview' ? (
                        <div className="space-y-10">
                            <div className="prose dark:prose-invert prose-lg max-none bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-2xl p-8 md:p-10 shadow-sm">
                                {memoizedDescription}
                            </div>

                            {jam.categories.length > 0 && (
                                <div className="mt-12">
                                    <h3 className="text-xl md:text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-3 px-2">
                                        <Scale className="w-6 h-6 text-modtale-accent" /> Judging Criteria
                                    </h3>
                                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                                        {jam.categories.map(cat => (
                                            <div key={cat.id} className="p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-2xl border border-white/40 dark:border-white/10 shadow-sm transition-colors hover:border-modtale-accent/30 group">
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

                            {jam.status === 'COMPLETED' && winners.length > 0 && (
                                <div className="mb-16">
                                    <h3 className="text-2xl md:text-3xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-3 px-2">
                                        <Trophy className="w-8 h-8 text-amber-500" /> Jam Winners
                                    </h3>
                                    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6 md:gap-8">
                                        {winners.map(sub => {
                                            const resolvedProjectImage = resolveUrl(sub.projectImageUrl);
                                            const resolvedProjectBanner = sub.projectBannerUrl ? resolveUrl(sub.projectBannerUrl) : null;

                                            return (
                                                <div
                                                    key={sub.id}
                                                    className="group bg-gradient-to-b from-amber-500/10 to-white/80 dark:to-slate-900/80 border border-amber-500/30 dark:border-amber-500/20 rounded-2xl overflow-hidden flex flex-col shadow-[0_8px_30px_rgba(245,158,11,0.15)] hover:-translate-y-1.5 hover:shadow-[0_12px_40px_rgba(245,158,11,0.25)] hover:border-amber-400 transition-all duration-300 relative cursor-pointer"
                                                >
                                                    <Link to={`/mod/${sub.projectId}`} className="absolute inset-0 z-10"><span className="sr-only">View {sub.projectTitle}</span></Link>
                                                    <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-amber-400 via-yellow-500 to-amber-600 z-20" />

                                                    <div className="absolute top-4 right-4 z-20">
                                                        <div className="bg-amber-500 text-white text-[10px] font-black uppercase tracking-widest px-3 py-1.5 rounded-lg shadow-lg flex items-center gap-1.5 border border-amber-400">
                                                            <Crown className="w-3.5 h-3.5" />
                                                            {sub.awardTitle || 'Winner'}
                                                        </div>
                                                    </div>

                                                    <div className="block relative w-full aspect-[3/1] bg-slate-100 dark:bg-slate-800 shrink-0 overflow-hidden border-b border-amber-500/20">
                                                        {resolvedProjectBanner ? (
                                                            <img
                                                                src={resolvedProjectBanner}
                                                                alt=""
                                                                fetchPriority="high"
                                                                loading="eager"
                                                                decoding="sync"
                                                                className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
                                                            />
                                                        ) : null}
                                                    </div>

                                                    <div className="px-6 flex-1 flex flex-col relative items-center text-center -mt-10 z-20 pointer-events-none">
                                                        <div className="block w-20 h-20 rounded-xl bg-white dark:bg-slate-900 shadow-xl border-[4px] border-amber-500 overflow-hidden relative group-hover:scale-105 transition-transform mb-4 shrink-0 pointer-events-auto">
                                                            {resolvedProjectImage ? (
                                                                <img src={resolvedProjectImage} alt={sub.projectTitle} className="w-full h-full object-cover" />
                                                            ) : (
                                                                <div className="w-full h-full flex items-center justify-center text-slate-400 bg-slate-100 dark:bg-slate-800">
                                                                    <LayoutGrid className="w-8 h-8 opacity-20" />
                                                                </div>
                                                            )}
                                                        </div>

                                                        <h3 className="text-xl font-black text-slate-900 dark:text-white truncate w-full group-hover:text-amber-600 dark:group-hover:text-amber-400 transition-colors pointer-events-auto">
                                                            {sub.projectTitle}
                                                        </h3>

                                                        <div className="flex items-center justify-center gap-1 mt-2 mb-4 pointer-events-auto">
                                                            <Link
                                                                to={`/creator/${sub.projectAuthor}`}
                                                                className="text-[11px] font-bold text-slate-600 dark:text-slate-400 hover:text-amber-600 hover:underline transition-colors bg-white/50 dark:bg-white/5 px-2.5 py-1 rounded-lg border border-slate-200/50 dark:border-white/5 relative z-20"
                                                            >
                                                                by {sub.projectAuthor || 'Unknown'}
                                                            </Link>
                                                        </div>

                                                        <p className="text-sm text-slate-600 dark:text-slate-400 line-clamp-2 leading-relaxed mb-6">
                                                            {sub.projectDescription || 'No description provided.'}
                                                        </p>
                                                    </div>

                                                    <div className="mt-auto px-6 py-5 bg-amber-50/50 dark:bg-amber-950/20 border-t border-amber-500/20 flex items-center justify-center relative backdrop-blur-md z-20 pointer-events-none">
                                                        <div className="flex items-center gap-3 text-amber-700 dark:text-amber-400">
                                                            <span className="text-[10px] font-black uppercase tracking-widest opacity-80">Final Score</span>
                                                            <span className="text-2xl font-black leading-none drop-shadow-sm">{sub.totalScore?.toFixed(2) || '---'}</span>
                                                        </div>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            )}

                            {jam.status === 'COMPLETED' && regularEntries.length > 0 && (
                                <h3 className="text-xl md:text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-3 px-2">
                                    <LayoutGrid className="w-6 h-6 text-modtale-accent" /> Other Entries
                                </h3>
                            )}

                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 md:gap-8">
                                {regularEntries.map(sub => {
                                    const resolvedProjectImage = resolveUrl(sub.projectImageUrl);
                                    const resolvedProjectBanner = sub.projectBannerUrl ? resolveUrl(sub.projectBannerUrl) : null;
                                    const isMySubmission = sub.submitterId === currentUser?.id;

                                    return (
                                        <div
                                            key={sub.id}
                                            className="group bg-white/80 dark:bg-slate-900/80 border border-slate-200 dark:border-white/10 rounded-xl overflow-hidden flex flex-col shadow-xl hover:-translate-y-1.5 hover:border-modtale-accent/50 dark:hover:border-modtale-accent/50 transition-all duration-300 relative cursor-pointer"
                                        >
                                            <Link to={`/mod/${sub.projectId}`} className="absolute inset-0 z-10"><span className="sr-only">View {sub.projectTitle}</span></Link>
                                            <div className="block relative w-full aspect-[3/1] bg-slate-100 dark:bg-slate-800 shrink-0 overflow-hidden border-b border-slate-200/50 dark:border-white/5">
                                                {resolvedProjectBanner ? (
                                                    <img
                                                        src={resolvedProjectBanner}
                                                        alt=""
                                                        fetchPriority="high"
                                                        loading="eager"
                                                        decoding="sync"
                                                        className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
                                                    />
                                                ) : null}
                                            </div>

                                            <div className="px-6 flex-1 flex flex-col relative items-center text-center -mt-10 z-20 pointer-events-none">
                                                <div className="block w-20 h-20 rounded-xl bg-white dark:bg-slate-900 shadow-xl border-[4px] border-white/90 dark:border-slate-800 overflow-hidden relative group-hover:scale-105 transition-transform mb-4 shrink-0 pointer-events-auto">
                                                    {resolvedProjectImage ? (
                                                        <img src={resolvedProjectImage} alt={sub.projectTitle} className="w-full h-full object-cover" />
                                                    ) : (
                                                        <div className="w-full h-full flex items-center justify-center text-slate-400 bg-slate-100 dark:bg-slate-800">
                                                            <LayoutGrid className="w-8 h-8 opacity-20" />
                                                        </div>
                                                    )}
                                                </div>

                                                <h3 className="text-xl font-black text-slate-900 dark:text-white truncate w-full group-hover:text-modtale-accent transition-colors pointer-events-auto">
                                                    {sub.projectTitle}
                                                </h3>

                                                <div className="flex items-center justify-center gap-1 mt-2 mb-4 pointer-events-auto">
                                                    <Link
                                                        to={`/creator/${sub.projectAuthor}`}
                                                        className="text-[11px] font-bold text-slate-600 dark:text-slate-400 hover:text-modtale-accent hover:underline transition-colors bg-white/50 dark:bg-white/5 px-2.5 py-1 rounded-lg border border-slate-200/50 dark:border-white/5 relative z-20"
                                                    >
                                                        by {sub.projectAuthor || 'Unknown'}
                                                    </Link>
                                                </div>

                                                <p className="text-sm text-slate-600 dark:text-slate-400 line-clamp-2 leading-relaxed mb-6">
                                                    {sub.projectDescription || 'No description provided.'}
                                                </p>
                                            </div>

                                            <div className="mt-auto px-6 py-5 bg-white/40 dark:bg-black/20 border-t border-slate-200/50 dark:border-white/5 flex items-center justify-between relative backdrop-blur-md z-20 pointer-events-none">
                                                <div className="flex items-center gap-2">
                                                    {canSeeResults && sub.totalScore !== undefined ? (
                                                        <div className="flex flex-col text-left">
                                                            <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">{jam.status === 'COMPLETED' ? 'Final Score' : 'Score'}</span>
                                                            <span className="text-xl font-black text-slate-900 dark:text-white leading-none mt-1 drop-shadow-sm">{sub.totalScore.toFixed(2)}</span>
                                                        </div>
                                                    ) : (
                                                        <div className="flex flex-col text-left opacity-50">
                                                            <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Score</span>
                                                            <span className="text-xl font-black text-slate-500 leading-none mt-1">---</span>
                                                        </div>
                                                    )}
                                                </div>

                                                <div className="flex items-center gap-3 pointer-events-auto">
                                                    {isMySubmission ? (
                                                        <div className="px-4 py-2 bg-green-500/10 text-green-600 dark:text-green-400 text-[10px] font-black uppercase tracking-widest rounded-xl flex items-center gap-2 border border-green-500/20 shadow-sm relative z-20">
                                                            <CheckCircle2 className="w-4 h-4" /> Your Entry
                                                        </div>
                                                    ) : canVote && (
                                                        <button
                                                            onClick={(e) => { e.preventDefault(); e.stopPropagation(); setVotingSubmissionId(sub.id); }}
                                                            className="px-5 py-2.5 bg-modtale-accent hover:bg-modtale-accentHover text-white text-sm font-black rounded-xl shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 flex items-center gap-2 ml-2 relative z-20"
                                                        >
                                                            <Star className="w-4 h-4" /> Vote
                                                        </button>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                                {submissions.length === 0 && (
                                    <div className="col-span-full py-24 text-center text-slate-500 bg-white/40 dark:bg-slate-900/40 backdrop-blur-xl border-2 border-dashed border-white/60 dark:border-white/10 rounded-2xl">
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
    );
};

const VotingModal: React.FC<{
    jam: Modjam,
    submission?: ModjamSubmission,
    currentUser: User | null,
    handleVote: (sid: string, cid: string, s: number) => void,
    onClose: () => void
}> = ({ jam, submission, currentUser, handleVote, onClose }) => {
    if (!submission) return null;

    const resolveUrl = (url?: string | null) => {
        if (!url) return '';
        if (url.startsWith('/api') || url.startsWith('/uploads')) {
            return `${BACKEND_URL}${url}`;
        }
        return url;
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-200" onClick={onClose}>
            <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl w-full max-w-md rounded-2xl shadow-2xl border border-white/20 dark:border-white/10 overflow-hidden flex flex-col animate-in zoom-in-95" onClick={e => e.stopPropagation()}>
                <div className="p-10 border-b border-slate-200/50 dark:border-white/5 text-center">
                    <div className="w-24 h-24 rounded-xl bg-white dark:bg-slate-800 shadow-lg overflow-hidden mx-auto mb-6 border-[3px] border-white dark:border-slate-700">
                        {submission.projectImageUrl ? (
                            <img src={resolveUrl(submission.projectImageUrl)} className="w-full h-full object-cover" alt="" />
                        ) : (
                            <LayoutGrid className="w-10 h-10 m-auto mt-6 text-slate-400 opacity-20" />
                        )}
                    </div>
                    <h3 className="text-3xl font-black text-slate-900 dark:text-white drop-shadow-sm">Cast Your Vote</h3>
                    <p className="text-base font-medium text-slate-500 mt-2">Voting on <span className="text-modtale-accent font-bold">{submission.projectTitle}</span></p>
                </div>
                <div className="p-10 overflow-y-auto max-h-[50vh] space-y-8 custom-scrollbar">
                    {jam.categories.map(cat => {
                        const myVote = submission.votes?.find(v => v.voterId === currentUser?.id && v.categoryId === cat.id);
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
                                            onClick={() => handleVote(submission.id, cat.id, i + 1)}
                                            className={`flex-1 h-14 rounded-xl flex items-center justify-center font-black text-base transition-all shadow-sm ${myVote?.score === i + 1 ? 'bg-modtale-accent text-white scale-105 ring-4 ring-modtale-accent/30' : 'bg-white dark:bg-slate-800 text-slate-500 hover:bg-slate-50 dark:hover:bg-slate-700 hover:text-slate-900 dark:hover:text-white border border-slate-200 dark:border-white/5'}`}
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
                    <button onClick={onClose} className="w-full h-16 bg-slate-900 dark:bg-white text-white dark:text-slate-900 rounded-xl font-black text-lg transition-all hover:scale-[1.02] active:scale-95 shadow-lg">
                        Done Voting
                    </button>
                </div>
            </div>
        </div>
    );
};

const PickWinnersModal: React.FC<{
    submissions: ModjamSubmission[],
    onClose: () => void,
    onSubmit: (winners: { submissionId: string, awardTitle: string }[]) => void,
    isSaving: boolean
}> = ({ submissions, onClose, onSubmit, isSaving }) => {
    const [selectedWinners, setSelectedWinners] = useState<Record<string, string>>({});
    const sortedSubmissions = useMemo(() => [...submissions].sort((a, b) => (b.totalScore || 0) - (a.totalScore || 0)), [submissions]);

    const toggleWinner = (id: string, e: React.MouseEvent) => {
        if ((e.target as HTMLElement).tagName === 'INPUT') return;

        const next = { ...selectedWinners };
        if (next[id] !== undefined) {
            delete next[id];
        } else {
            next[id] = 'Winner';
        }
        setSelectedWinners(next);
    };

    const handleTitleChange = (id: string, title: string) => {
        setSelectedWinners(prev => ({ ...prev, [id]: title }));
    };

    const handleSubmit = () => {
        const winnersArr = Object.entries(selectedWinners).map(([submissionId, awardTitle]) => ({
            submissionId,
            awardTitle
        }));
        onSubmit(winnersArr);
    };

    const resolveUrl = (url?: string | null) => {
        if (!url) return '';
        if (url.startsWith('/api') || url.startsWith('/uploads')) return `${BACKEND_URL}${url}`;
        return url;
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-200" onClick={onClose}>
            <div className="bg-white dark:bg-slate-900 w-full max-w-2xl rounded-3xl shadow-2xl border border-white/20 dark:border-white/10 overflow-hidden flex flex-col animate-in zoom-in-95 max-h-[90vh]" onClick={e => e.stopPropagation()}>
                <div className="p-8 border-b border-slate-200 dark:border-white/5 flex justify-between items-center bg-slate-50 dark:bg-slate-950/50">
                    <div>
                        <h3 className="text-2xl font-black text-slate-900 dark:text-white flex items-center gap-3">
                            <Trophy className="w-6 h-6 text-amber-500" /> Finalize Jam
                        </h3>
                        <p className="text-sm font-medium text-slate-500 mt-1">Select the winners and assign them custom awards.</p>
                    </div>
                    <button onClick={onClose} className="p-2 bg-slate-200 dark:bg-white/10 text-slate-500 hover:text-slate-900 dark:hover:text-white rounded-xl transition-colors">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <div className="p-4 md:p-8 overflow-y-auto custom-scrollbar flex-1 space-y-3">
                    {sortedSubmissions.length === 0 ? (
                        <p className="text-center text-slate-500 py-10 font-medium">No submissions to pick from.</p>
                    ) : (
                        sortedSubmissions.map(sub => {
                            const isSelected = selectedWinners[sub.id] !== undefined;

                            return (
                                <div
                                    key={sub.id}
                                    onClick={(e) => toggleWinner(sub.id, e)}
                                    className={`p-4 rounded-2xl border cursor-pointer transition-all ${isSelected ? 'bg-amber-500/5 border-amber-500/30 ring-1 ring-amber-500/50' : 'bg-slate-50 dark:bg-slate-950/50 border-slate-200 dark:border-white/5 hover:border-slate-300 dark:hover:border-white/20'}`}
                                >
                                    <div className="flex items-start gap-4">
                                        <div className={`mt-1 w-6 h-6 rounded-md flex items-center justify-center shrink-0 border transition-all ${isSelected ? 'bg-amber-500 border-amber-500 text-white' : 'bg-white dark:bg-slate-900 border-slate-300 dark:border-white/10'}`}>
                                            {isSelected && <Check className="w-4 h-4" strokeWidth={3} />}
                                        </div>

                                        <div className="w-12 h-12 rounded-xl bg-slate-200 dark:bg-slate-800 overflow-hidden shrink-0 border border-slate-200 dark:border-white/5">
                                            {sub.projectImageUrl ? (
                                                <img src={resolveUrl(sub.projectImageUrl)} className="w-full h-full object-cover" alt="" />
                                            ) : (
                                                <LayoutGrid className="w-6 h-6 m-auto mt-3 text-slate-400 opacity-20" />
                                            )}
                                        </div>

                                        <div className="flex-1 min-w-0">
                                            <div className="flex justify-between items-start">
                                                <div>
                                                    <h4 className={`font-bold truncate transition-colors ${isSelected ? 'text-amber-700 dark:text-amber-400' : 'text-slate-900 dark:text-white'}`}>{sub.projectTitle}</h4>
                                                    <div className="text-[10px] text-slate-500 uppercase tracking-widest mt-0.5">by {sub.projectAuthor} • Score: {sub.totalScore?.toFixed(2) || 'N/A'}</div>
                                                </div>
                                            </div>

                                            {isSelected && (
                                                <div className="mt-4 animate-in slide-in-from-top-2">
                                                    <label className="text-[10px] font-black uppercase text-amber-600/80 dark:text-amber-500/80 tracking-widest px-1">Award Title</label>
                                                    <input
                                                        type="text"
                                                        value={selectedWinners[sub.id]}
                                                        onChange={(e) => handleTitleChange(sub.id, e.target.value)}
                                                        className="w-full mt-1 bg-white dark:bg-black/20 border border-amber-200 dark:border-amber-900/30 rounded-xl px-4 py-2.5 text-sm text-slate-900 dark:text-white focus:outline-none focus:border-amber-500 focus:ring-1 focus:ring-amber-500 transition-colors"
                                                        placeholder="e.g., Grand Prize, Best Visuals..."
                                                        autoFocus
                                                    />
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            );
                        })
                    )}
                </div>

                <div className="p-6 border-t border-slate-200 dark:border-white/5 bg-slate-50 dark:bg-slate-950/50 flex justify-end gap-3">
                    <button
                        onClick={onClose}
                        className="px-6 py-3 rounded-xl font-bold text-sm bg-slate-200 dark:bg-white/10 text-slate-600 dark:text-slate-300 hover:bg-slate-300 dark:hover:bg-white/20 transition-all"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSubmit}
                        disabled={isSaving}
                        className="px-8 py-3 rounded-xl font-bold text-sm bg-amber-500 hover:bg-amber-600 text-white shadow-lg shadow-amber-500/20 transition-all flex items-center gap-2 disabled:opacity-50"
                    >
                        {isSaving ? <Spinner className="w-4 h-4 text-white" /> : <CheckCircle2 className="w-4 h-4" />}
                        Complete Jam
                    </button>
                </div>
            </div>
        </div>
    );
};