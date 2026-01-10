import React, { useEffect, useState, useMemo, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import type { Mod, Modpack, User } from '../../types.ts';
import { ModCard } from '../../components/resources/ModCard.tsx';
import { api } from '../../utils/api.ts';
import { Package, Users, ChevronLeft, ChevronRight, CornerDownLeft } from 'lucide-react';
import { createSlug } from '../../utils/slug.ts';
import { ProfileLayout } from '../../components/user/ProfileLayout.tsx';
import { Spinner } from '../../components/ui/Spinner.tsx';
import { EmptyState } from '../../components/ui/EmptyState.tsx';
import NotFound from '../../components/ui/error/NotFound.tsx';

interface CreatorProfileProps {
    onModClick: (mod: Mod) => void;
    onModpackClick: (modpack: Modpack) => void;
    onBack: () => void;
    likedModIds: string[];
    likedModpackIds: string[];
    onToggleFavorite: (id: string) => void;
    onToggleFavoriteModpack: (id: string) => void;
    currentUser: User | null;
    onRefreshUser?: () => void;
}

export const CreatorProfile: React.FC<CreatorProfileProps> = ({
                                                                  onModClick, onModpackClick, onBack, likedModIds, likedModpackIds, onToggleFavorite, onToggleFavoriteModpack, currentUser, onRefreshUser
                                                              }) => {
    const { username } = useParams<{ username: string }>();
    const navigate = useNavigate();
    const projectsTitleRef = useRef<HTMLHeadingElement>(null);

    const [creator, setCreator] = useState<User | null>(null);
    const [orgMembers, setOrgMembers] = useState<User[]>([]);
    const [loadingCreator, setLoadingCreator] = useState(true);
    const [notFound, setNotFound] = useState(false);

    const [projects, setProjects] = useState<(Mod | Modpack)[]>([]);
    const [loadingProjects, setLoadingProjects] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalItems, setTotalItems] = useState(0);
    const [jumpPage, setJumpPage] = useState('');

    const [itemsPerPage] = useState(12);

    const { totalDownloads, totalFavorites } = useMemo(() => {
        return projects.reduce((acc, p) => ({
            totalDownloads: acc.totalDownloads + (p.downloadCount || 0),
            totalFavorites: acc.totalFavorites + (p.favoriteCount || 0)
        }), { totalDownloads: 0, totalFavorites: 0 });
    }, [projects]);

    const isFollowing = useMemo(() => {
        return currentUser?.followingIds?.includes(creator?.id || '') || false;
    }, [currentUser, creator]);

    const [isFollowingOptimistic, setIsFollowingOptimistic] = useState<boolean | null>(null);
    const actualIsFollowing = isFollowingOptimistic !== null ? isFollowingOptimistic : isFollowing;

    const followerCount = useMemo(() => {
        if (!creator) return 0;
        const base = creator.followerIds?.length || 0;
        const iAmRecorded = currentUser && creator.followerIds?.includes(currentUser.id);

        if (actualIsFollowing && !iAmRecorded) return base + 1;
        if (!actualIsFollowing && iAmRecorded) return base - 1;
        return base;
    }, [creator, currentUser, actualIsFollowing]);

    useEffect(() => {
        const fetchCreatorData = async () => {
            if (!username) return;
            setLoadingCreator(true);
            setNotFound(false);
            try {
                const userRes = await api.get(`/user/profile/${username}`);
                const userData = userRes.data;
                setCreator(userData);

                if (userData.accountType === 'ORGANIZATION') {
                    try {
                        const membersRes = await api.get(`/user/org/${username}/members`);
                        setOrgMembers(membersRes.data);
                    } catch (e) {
                        console.warn("Could not fetch org members", e);
                    }
                }
            } catch (error: any) {
                if (error.response && error.response.status === 404) {
                    setNotFound(true);
                }
            } finally {
                setLoadingCreator(false);
            }
        };
        fetchCreatorData();
    }, [username]);

    const fetchProjects = useCallback(async () => {
        if (!username) return;

        setLoadingProjects(true);
        try {
            const params = {
                author: username,
                page,
                size: itemsPerPage,
                sort: 'newest',
            };

            const projectsRes = await api.get('/projects', { params });

            setProjects(projectsRes.data.content);
            setTotalPages(projectsRes.data.totalPages);
            setTotalItems(projectsRes.data.totalElements);
        } catch (error) {
            setProjects([]);
        } finally {
            setLoadingProjects(false);
        }
    }, [username, page, itemsPerPage]);

    useEffect(() => {
        const timer = setTimeout(() => {
            fetchProjects();
        }, 300);
        return () => clearTimeout(timer);
    }, [fetchProjects]);

    const handleToggleFollow = async () => {
        if (!currentUser) { navigate('/login'); return; }
        if (!creator) return;
        const previousState = actualIsFollowing;
        setIsFollowingOptimistic(!previousState);
        try {
            if (previousState) await api.post(`/user/unfollow/${creator.username}`);
            else await api.post(`/user/follow/${creator.username}`);
            if (onRefreshUser) onRefreshUser();
        } catch (e) {
            setIsFollowingOptimistic(previousState);
        }
    };

    const handlePageChange = (p: number) => {
        if (p >= 0 && p < totalPages) {
            setPage(p);
            if (projectsTitleRef.current) {
                const yOffset = -120;
                const y = projectsTitleRef.current.getBoundingClientRect().top + window.pageYOffset + yOffset;
                window.scrollTo({ top: y, behavior: 'smooth' });
            } else {
                window.scrollTo({ top: 0, behavior: 'smooth' });
            }
        }
    };

    const handleJump = (e: React.FormEvent) => {
        e.preventDefault();
        const p = parseInt(jumpPage);
        if (!isNaN(p) && p >= 1 && p <= totalPages) {
            handlePageChange(p - 1);
            setJumpPage('');
        }
    };

    const getPageNumbers = () => {
        const total = totalPages;
        const current = page + 1;
        const delta = 2;
        const range = [];
        const rangeWithDots: (number | string)[] = [];
        let l;
        range.push(1);
        for (let i = current - delta; i <= current + delta; i++) {
            if (i < total && i > 1) { range.push(i); }
        }
        range.push(total);
        const uniqueRange = [...new Set(range)].sort((a, b) => a - b);
        for (const i of uniqueRange) {
            if (l) {
                if (i - l === 2) { rangeWithDots.push(l + 1); }
                else if (i - l !== 1) { rangeWithDots.push('...'); }
            }
            rangeWithDots.push(i);
            l = i;
        }
        return rangeWithDots;
    };

    const getProjectPath = (item: Mod | Modpack) => {
        const slug = createSlug(item.title, item.id);
        return item.classification === 'MODPACK' ? `/modpack/${slug}` : (item.classification === 'SAVE' ? `/world/${slug}` : `/mod/${slug}`);
    };

    if (loadingCreator) return <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark"><Spinner fullScreen /></div>;
    if (notFound) return <NotFound />;
    if (!creator) return <div className="min-h-screen flex flex-col items-center justify-center"><h2 className="text-2xl font-bold">Creator not found</h2><button onClick={onBack}>Go Back</button></div>;

    const stats = {
        downloads: totalDownloads,
        favorites: totalFavorites,
        followers: followerCount,
        projects: totalItems
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark pb-20">
            <ProfileLayout
                user={creator}
                stats={stats}
                isFollowing={actualIsFollowing}
                onToggleFollow={handleToggleFollow}
                isSelf={currentUser?.username === creator.username}
                isLoggedIn={!!currentUser}
                onBack={onBack}
            >
                {creator.accountType === 'ORGANIZATION' && orgMembers.length > 0 && (
                    <div className="mb-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
                        <h2 className="text-xl font-bold text-slate-900 dark:text-white mb-4 flex items-center gap-2">
                            <Users className="w-5 h-5 text-purple-500" />
                            Organization Members
                        </h2>
                        <div className="flex flex-wrap gap-4">
                            {orgMembers.map(member => (
                                <a
                                    key={member.id}
                                    href={`/creator/${member.username}`}
                                    className="flex items-center gap-3 p-2 pr-4 bg-white dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-xl hover:border-modtale-accent dark:hover:border-modtale-accent transition-all group"
                                >
                                    <div className="w-10 h-10 rounded-lg overflow-hidden bg-slate-100">
                                        <img src={member.avatarUrl} alt={member.username} className="w-full h-full object-cover" />
                                    </div>
                                    <div>
                                        <div className="font-bold text-slate-800 dark:text-slate-200 group-hover:text-modtale-accent transition-colors text-sm">{member.username}</div>
                                        <div className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">
                                            {creator.organizationMembers?.find(m => m.userId === member.id)?.role || 'Member'}
                                        </div>
                                    </div>
                                </a>
                            ))}
                        </div>
                    </div>
                )}

                <h2
                    ref={projectsTitleRef}
                    className="text-xl font-bold text-slate-900 dark:text-white mb-6"
                >
                    Published Work
                </h2>

                {loadingProjects && page === 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">
                        {[...Array(itemsPerPage)].map((_, i) => <div key={i} className="h-[280px] bg-white dark:bg-white/5 rounded-xl animate-pulse border border-slate-200 dark:border-white/5" />)}
                    </div>
                ) : projects.length > 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6 gap-y-8">
                        {projects.map((project) => (
                            <div key={project.id} className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                <ModCard
                                    mod={project as Mod}
                                    path={getProjectPath(project)}
                                    isFavorite={likedModIds.includes(project.id) || likedModpackIds.includes(project.id)}
                                    onToggleFavorite={() => { if (project.classification === 'MODPACK') onToggleFavoriteModpack(project.id); else onToggleFavorite(project.id); }}
                                    isLoggedIn={!!currentUser}
                                />
                            </div>
                        ))}
                    </div>
                ) : (
                    <EmptyState
                        icon={Package}
                        title="No projects found"
                        message="This creator hasn't published any projects yet."
                    />
                )}

                {totalPages > 1 && (
                    <div className="mt-12 flex flex-col md:flex-row justify-center items-center gap-4 pb-12 animate-in fade-in">
                        <div className="flex items-center gap-2">
                            <button onClick={() => handlePageChange(page - 1)} disabled={page === 0} className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"><ChevronLeft className="w-5 h-5" /></button>
                            <div className="hidden sm:flex gap-2">
                                {getPageNumbers().map((p, idx) => (
                                    typeof p === 'number' ? (
                                        <button key={p} onClick={() => handlePageChange(p - 1)} className={`w-10 h-10 rounded-lg text-sm font-bold border transition-colors ${page === p - 1 ? 'bg-modtale-accent text-white border-modtale-accent' : 'text-slate-600 dark:text-slate-400 border-transparent hover:bg-slate-100 dark:hover:bg-white/5'}`}>{p}</button>
                                    ) : ( <span key={`dots-${idx}`} className="w-10 h-10 flex items-center justify-center text-slate-400">...</span> )
                                ))}
                            </div>
                            <button onClick={() => handlePageChange(page + 1)} disabled={page === totalPages - 1} className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"><ChevronRight className="w-5 h-5" /></button>
                        </div>
                        <div className="hidden md:block w-px h-6 bg-slate-200 dark:bg-white/10"></div>
                        <form onSubmit={handleJump} className="flex items-center gap-2">
                            <span className="text-xs font-bold text-slate-500 uppercase">Go to</span>
                            <input type="number" min={1} max={totalPages} value={jumpPage} onChange={(e) => setJumpPage(e.target.value)} className="w-12 h-10 rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5 px-1 text-sm font-bold text-center dark:text-white focus:outline-none focus:ring-2 focus:ring-modtale-accent transition-all" placeholder="#" />
                            <button type="submit" disabled={!jumpPage} className="w-10 h-10 flex items-center justify-center rounded-lg bg-slate-100 dark:bg-white/5 hover:bg-modtale-accent hover:text-white dark:hover:bg-modtale-accent text-slate-500 dark:text-slate-400 transition-colors disabled:opacity-50"><CornerDownLeft className="w-4 h-4" /></button>
                        </form>
                    </div>
                )}
            </ProfileLayout>
        </div>
    );
};