import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '@/utils/api';
import type { Modjam, ModjamSubmission, User, Mod } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { Trophy, Calendar, Users, Upload, ChevronRight, CheckCircle2, Layout } from 'lucide-react';

export const JamDetail: React.FC<{ currentUser: User | null }> = ({ currentUser }) => {
    const { slug } = useParams<{ slug: string }>();

    const [jam, setJam] = useState<Modjam | null>(null);
    const [submissions, setSubmissions] = useState<ModjamSubmission[]>([]);
    const [loading, setLoading] = useState(true);
    const [myProjects, setMyProjects] = useState<Mod[]>([]);
    const [selectedProjectId, setSelectedProjectId] = useState<string>('');
    const [message, setMessage] = useState<{ text: string, type: 'success' | 'error' } | null>(null);

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
                setMessage({ text: 'Failed to load jam details', type: 'error' });
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
            setMessage({ text: 'Successfully joined the jam!', type: 'success' });
            setJam({ ...jam, participantIds: [...jam.participantIds, currentUser.id] });
        } catch (err) {
            setMessage({ text: 'Failed to join', type: 'error' });
        }
    };

    const handleSubmit = async () => {
        if (!jam || !selectedProjectId) return;
        try {
            const res = await api.post(`/modjams/${jam.id}/submit`, { projectId: selectedProjectId });
            setMessage({ text: 'Project submitted!', type: 'success' });
            setSubmissions([...submissions, res.data]);
            setSelectedProjectId('');
        } catch (err: any) {
            setMessage({ text: err.response?.data?.message || 'Failed to submit project', type: 'error' });
        }
    };

    const handleVote = async (submissionId: string, categoryId: string, score: number) => {
        if (!jam) return;
        try {
            const res = await api.post(`/modjams/${jam.id}/vote`, { submissionId, categoryId, score });
            setSubmissions(submissions.map(s => s.id === submissionId ? res.data : s));
            setMessage({ text: 'Vote recorded!', type: 'success' });
        } catch (err: any) {
            setMessage({ text: err.response?.data?.message || 'Failed to vote', type: 'error' });
        }
    };

    if (loading) return <div className="p-20 flex justify-center"><Spinner /></div>;
    if (!jam) return <div className="p-20 text-center font-bold">Jam not found.</div>;

    const isParticipating = currentUser?.id && jam.participantIds.includes(currentUser.id);
    const hasSubmitted = submissions.some(s => s.submitterId === currentUser?.id);
    const canVote = jam.status === 'VOTING' && (jam.allowPublicVoting || jam.hostId === currentUser?.id);

    return (
        <div className="max-w-7xl mx-auto px-4 py-8">
            {message && (
                <div className={`mb-6 p-4 rounded-xl font-bold text-sm ${message.type === 'error' ? 'bg-red-50 text-red-600 dark:bg-red-900/20 dark:text-red-400' : 'bg-green-50 text-green-600 dark:bg-green-900/20 dark:text-green-400'}`}>
                    {message.text}
                </div>
            )}

            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden mb-8">
                {jam.bannerUrl && (
                    <div className="h-64 w-full bg-slate-100 dark:bg-slate-800">
                        <img src={jam.bannerUrl} alt={jam.title} className="w-full h-full object-cover" />
                    </div>
                )}
                <div className="p-8">
                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-6">
                        <div>
                            <h1 className="text-4xl font-black mb-2">{jam.title}</h1>
                            <p className="text-slate-500 font-medium flex items-center gap-2">
                                Hosted by <span className="text-slate-900 dark:text-white font-bold">{jam.hostName}</span>
                            </p>
                        </div>
                        <div className="flex items-center gap-4">
                            <div className="bg-slate-100 dark:bg-slate-800 px-4 py-2 rounded-xl text-center">
                                <div className="text-xs font-bold text-slate-500 uppercase">Status</div>
                                <div className="font-black text-modtale-accent">{jam.status}</div>
                            </div>
                            <div className="bg-slate-100 dark:bg-slate-800 px-4 py-2 rounded-xl text-center">
                                <div className="text-xs font-bold text-slate-500 uppercase">Joined</div>
                                <div className="font-black">{jam.participantIds.length}</div>
                            </div>
                        </div>
                    </div>

                    <p className="text-lg text-slate-700 dark:text-slate-300 mb-8 max-w-3xl">
                        {jam.description}
                    </p>

                    <div className="flex items-center gap-4 border-t border-slate-200 dark:border-white/10 pt-6">
                        {!currentUser ? (
                            <Link to="/" className="bg-modtale-accent text-white px-6 py-3 rounded-xl font-bold hover:bg-modtale-accent/90 transition-colors">
                                Sign in to participate
                            </Link>
                        ) : !isParticipating ? (
                            <button onClick={handleJoin} className="bg-modtale-accent text-white px-6 py-3 rounded-xl font-bold hover:bg-modtale-accent/90 transition-colors flex items-center gap-2">
                                <Users className="w-5 h-5" /> Join Jam
                            </button>
                        ) : (
                            <div className="flex items-center gap-2 text-green-600 dark:text-green-400 font-bold bg-green-50 dark:bg-green-900/20 px-4 py-2 rounded-lg">
                                <CheckCircle2 className="w-5 h-5" /> Participating
                            </div>
                        )}

                        {isParticipating && jam.status === 'ACTIVE' && !hasSubmitted && (
                            <div className="flex items-center gap-2 flex-1 max-w-md">
                                <select
                                    className="flex-1 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 font-medium outline-none"
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
                                    disabled={!selectedProjectId}
                                    className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-6 py-3 rounded-xl font-bold disabled:opacity-50 flex items-center gap-2"
                                >
                                    <Upload className="w-4 h-4" /> Submit
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            <h2 className="text-2xl font-black mb-6 flex items-center gap-2">
                <Layout className="w-6 h-6" /> Submissions ({submissions.length})
            </h2>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {submissions.map(sub => (
                    <div key={sub.id} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden flex flex-col">
                        <div className="h-40 bg-slate-100 dark:bg-slate-800">
                            {sub.projectImageUrl && <img src={sub.projectImageUrl} alt={sub.projectTitle} className="w-full h-full object-cover" />}
                        </div>
                        <div className="p-5 flex-1 flex flex-col">
                            <h3 className="text-xl font-bold mb-4">{sub.projectTitle}</h3>

                            {canVote && jam.categories.length > 0 && (
                                <div className="mt-auto space-y-3 border-t border-slate-100 dark:border-white/5 pt-4">
                                    {jam.categories.map(cat => {
                                        const myVote = sub.votes.find(v => v.voterId === currentUser?.id && v.categoryId === cat.id);
                                        return (
                                            <div key={cat.id} className="flex items-center justify-between text-sm">
                                                <span className="font-bold text-slate-600 dark:text-slate-400">{cat.name}</span>
                                                <div className="flex items-center gap-1">
                                                    {[...Array(cat.maxScore)].map((_, i) => (
                                                        <button
                                                            key={i}
                                                            onClick={() => handleVote(sub.id, cat.id, i + 1)}
                                                            className={`w-6 h-6 rounded flex items-center justify-center font-bold text-xs ${myVote?.score === i + 1 ? 'bg-modtale-accent text-white' : 'bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700'}`}
                                                        >
                                                            {i + 1}
                                                        </button>
                                                    ))}
                                                </div>
                                            </div>
                                        )
                                    })}
                                </div>
                            )}

                            {jam.status === 'COMPLETED' && sub.totalScore !== undefined && (
                                <div className="mt-auto border-t border-slate-100 dark:border-white/5 pt-4 flex items-center justify-between">
                                    <span className="font-bold text-slate-500">Score: {sub.totalScore.toFixed(2)}</span>
                                    {sub.rank && <span className="font-black text-modtale-accent flex items-center gap-1"><Trophy className="w-4 h-4"/> #{sub.rank}</span>}
                                </div>
                            )}
                        </div>
                    </div>
                ))}
            </div>

            {submissions.length === 0 && (
                <div className="text-center py-12 text-slate-500">
                    <p className="font-bold">No submissions yet.</p>
                </div>
            )}
        </div>
    );
};