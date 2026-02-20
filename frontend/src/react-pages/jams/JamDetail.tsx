import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '@/utils/api';
import type { Modjam, ModjamSubmission, User, Mod } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { Trophy, Calendar, Users, Upload, CheckCircle2, LayoutGrid, AlertCircle, Scale } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';

export const JamDetail: React.FC<{ currentUser: User | null }> = ({ currentUser }) => {
    const { slug } = useParams<{ slug: string }>();
    const navigate = useNavigate();

    const [jam, setJam] = useState<Modjam | null>(null);
    const [submissions, setSubmissions] = useState<ModjamSubmission[]>([]);
    const [loading, setLoading] = useState(true);
    const [myProjects, setMyProjects] = useState<Mod[]>([]);
    const [selectedProjectId, setSelectedProjectId] = useState<string>('');
    const [statusModal, setStatusModal] = useState<{type: 'success' | 'error' | 'warning', title: string, msg: string} | null>(null);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        const fetchJamData = async () => {
            try {
                const res = await api.get(`/modjams/${slug}`);
                setJam(res.data);

                const subRes = await api.get(`/modjams/${res.data.id}/submissions`);
                setSubmissions(subRes.data);

                if (currentUser) {
                    const projRes = await api.get(`/user/repos`);
                    setMyProjects(projRes.data.content || []);
                }
            } catch (err) {
                console.error(err);
                setStatusModal({ type: 'error', title: 'Not Found', msg: 'Failed to load jam details.' });
            } finally {
                setLoading(false);
            }
        };
        fetchJamData();
    }, [slug, currentUser]);

    const handleJoin = async () => {
        if (!jam || !currentUser) return;
        try {
            await api.post(`/modjams/${jam.id}/participate`, {});
            setStatusModal({ type: 'success', title: 'Joined!', msg: 'Successfully joined the jam.' });
            setJam({ ...jam, participantIds: [...jam.participantIds, currentUser.id] });
        } catch (err) {
            setStatusModal({ type: 'error', title: 'Error', msg: 'Failed to join the jam.' });
        }
    };

    const handleSubmit = async () => {
        if (!jam || !selectedProjectId) return;
        setSubmitting(true);
        try {
            const res = await api.post(`/modjams/${jam.id}/submit`, { projectId: selectedProjectId });
            setStatusModal({ type: 'success', title: 'Submitted!', msg: 'Project submitted successfully.' });
            setSubmissions([...submissions, res.data]);
            setSelectedProjectId('');
        } catch (err: any) {
            setStatusModal({ type: 'error', title: 'Submission Failed', msg: err.response?.data?.message || 'Failed to submit project.' });
        } finally {
            setSubmitting(false);
        }
    };

    const handleVote = async (submissionId: string, categoryId: string, score: number) => {
        if (!jam) return;
        try {
            const res = await api.post(`/modjams/${jam.id}/vote`, { submissionId, categoryId, score });
            setSubmissions(submissions.map(s => s.id === submissionId ? res.data : s));
        } catch (err: any) {
            setStatusModal({ type: 'error', title: 'Vote Failed', msg: err.response?.data?.message || 'Failed to record vote.' });
        }
    };

    if (loading) return <div className="min-h-screen bg-slate-950 flex items-center justify-center"><Spinner fullScreen={false} className="w-8 h-8" /></div>;
    if (!jam) return <div className="p-20 text-center font-bold text-slate-500">Jam not found.</div>;

    const isParticipating = currentUser?.id && jam.participantIds.includes(currentUser.id);
    const hasSubmitted = submissions.some(s => s.submitterId === currentUser?.id);
    const canVote = jam.status === 'VOTING' && (jam.allowPublicVoting || jam.hostId === currentUser?.id);

    const isPast = (dateString: string) => new Date(dateString) < new Date();

    return (
        <>
            {statusModal && <StatusModal type={statusModal.type} title={statusModal.title} message={statusModal.msg} onClose={() => setStatusModal(null)} />}

            <JamLayout
                bannerUrl={jam.bannerUrl}
                iconUrl={jam.bannerUrl || "https://modtale.net/assets/favicon.svg"}
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
                                        <select
                                            className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 font-bold text-sm outline-none focus:ring-2 focus:ring-modtale-accent shadow-sm min-w-[200px]"
                                            value={selectedProjectId}
                                            onChange={(e) => setSelectedProjectId(e.target.value)}
                                        >
                                            <option value="">Select a project to submit...</option>
                                            {myProjects.map(p => (
                                                <option key={p.id} value={p.id}>{p.title}</option>
                                            ))}
                                        </select>
                                        <button
                                            onClick={handleSubmit}
                                            disabled={!selectedProjectId || submitting}
                                            className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-6 py-3 rounded-xl font-black disabled:opacity-50 flex items-center justify-center gap-2 hover:scale-105 active:scale-95 transition-all shadow-md"
                                        >
                                            {submitting ? <Spinner className="w-4 h-4" fullScreen={false} /> : <Upload className="w-4 h-4" />} Submit
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
                        <div className="relative overflow-hidden p-6 rounded-[2rem] border border-slate-200 dark:border-white/10 bg-gradient-to-br from-slate-100 to-slate-50 dark:from-slate-800/50 dark:to-slate-900/50 shadow-lg">
                            <div className="relative z-10 flex flex-col items-center justify-center text-center">
                                <span className="text-xs font-black text-slate-500 uppercase tracking-widest mb-2">Current Status</span>
                                <span className="text-3xl font-black text-modtale-accent uppercase tracking-widest drop-shadow-sm">{jam.status}</span>
                            </div>
                            <Trophy className="absolute -bottom-4 -right-4 w-24 h-24 text-slate-900/5 dark:text-white/5" />
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
                                <span className="text-3xl font-black text-slate-900 dark:text-white mb-1">{jam.participantIds.length}</span>
                                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Joined</span>
                            </div>
                            <div className="flex flex-col items-center justify-center p-6 bg-white/60 dark:bg-slate-900/40 backdrop-blur-xl rounded-[2rem] border border-slate-200 dark:border-white/10 shadow-lg">
                                <Upload className="w-6 h-6 text-modtale-accent mb-2" />
                                <span className="text-3xl font-black text-slate-900 dark:text-white mb-1">{submissions.length}</span>
                                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Submissions</span>
                            </div>
                        </div>

                        {jam.categories.length > 0 && (
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

                        <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
                            {submissions.map(sub => (
                                <div key={sub.id} className="bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 rounded-3xl overflow-hidden flex flex-col group hover:border-modtale-accent transition-colors shadow-sm">
                                    <div className="h-48 bg-slate-100 dark:bg-slate-800 relative overflow-hidden">
                                        {sub.projectImageUrl ? (
                                            <img src={sub.projectImageUrl} alt={sub.projectTitle} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center text-slate-400">
                                                <LayoutGrid className="w-12 h-12 opacity-20" />
                                            </div>
                                        )}
                                        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-90" />
                                        <div className="absolute bottom-4 left-5 right-5">
                                            <h3 className="text-xl font-black text-white truncate drop-shadow-md">{sub.projectTitle}</h3>
                                        </div>
                                    </div>

                                    <div className="p-5 flex-1 flex flex-col bg-slate-50 dark:bg-transparent">
                                        {canVote && jam.categories.length > 0 ? (
                                            <div className="mt-auto space-y-3 pt-2">
                                                {jam.categories.map(cat => {
                                                    const myVote = sub.votes.find(v => v.voterId === currentUser?.id && v.categoryId === cat.id);
                                                    return (
                                                        <div key={cat.id} className="flex flex-col gap-2 p-3 bg-white dark:bg-slate-900/50 rounded-xl border border-slate-200 dark:border-white/5">
                                                            <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">{cat.name}</span>
                                                            <div className="flex flex-wrap items-center gap-1.5">
                                                                {[...Array(cat.maxScore)].map((_, i) => (
                                                                    <button
                                                                        key={i}
                                                                        onClick={() => handleVote(sub.id, cat.id, i + 1)}
                                                                        className={`flex-1 h-8 rounded-lg flex items-center justify-center font-black text-xs transition-all ${myVote?.score === i + 1 ? 'bg-modtale-accent text-white shadow-md scale-105' : 'bg-slate-100 dark:bg-white/5 text-slate-400 hover:bg-slate-200 dark:hover:bg-white/10 hover:text-slate-900 dark:hover:text-white'}`}
                                                                    >
                                                                        {i + 1}
                                                                    </button>
                                                                ))}
                                                            </div>
                                                        </div>
                                                    )
                                                })}
                                            </div>
                                        ) : (
                                            <div className="flex-1 flex flex-col items-center justify-center py-6 text-center text-slate-500">
                                                <AlertCircle className="w-8 h-8 opacity-20 mb-2" />
                                                <span className="text-sm font-bold">Voting not available</span>
                                            </div>
                                        )}

                                        {jam.status === 'COMPLETED' && sub.totalScore !== undefined && (
                                            <div className="mt-4 pt-4 border-t border-slate-200 dark:border-white/5 flex items-center justify-between">
                                                <div className="flex flex-col">
                                                    <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Final Score</span>
                                                    <span className="text-lg font-black text-slate-900 dark:text-white">{sub.totalScore.toFixed(2)}</span>
                                                </div>
                                                {sub.rank && (
                                                    <div className="flex items-center gap-2 bg-modtale-accent/10 text-modtale-accent px-4 py-2 rounded-xl font-black text-lg">
                                                        <Trophy className="w-5 h-5"/> #{sub.rank}
                                                    </div>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            ))}
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