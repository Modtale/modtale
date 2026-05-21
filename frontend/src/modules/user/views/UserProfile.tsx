import React, { useEffect, useState, useMemo, useCallback, useRef } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import { api } from '@/utils/api';
import { Package, Users, ChevronLeft, ChevronRight, CornerDownLeft, Building2 } from 'lucide-react';
import { SiteRoutes } from '@/utils/routes';
import { useSSRData } from '@/context/SSRContext';
import { ProfileLayout } from '../components/ProfileLayout';
import { ProjectCard } from '@/modules/project/components/ProjectCard';
import { Spinner } from '@/components/ui/Spinner';
import { EmptyState } from '@/components/ui/EmptyState';
import NotFound from '@/components/ui/error/NotFound';
import { ReportModal } from '@/modules/project/components/dialogs/ReportModal';
import type { Project, User } from '@/types';

interface UserProfileProps {
    onBack: () => void;
    likedModIds: string[];
    onToggleFavorite: (id: string) => void;
    currentUser: User | null;
    onRefreshUser?: () => void;
}

export const UserProfile: React.FC<UserProfileProps> = ({
                                                            onBack, likedModIds, onToggleFavorite, currentUser, onRefreshUser
                                                        }) => {
    const { username } = useParams<{ username: string }>();
    const { initialData } = useSSRData();
    const navigate = useNavigate();
    const location = useLocation();
    const projectsTitleRef = useRef<HTMLHeadingElement>(null);


    const [profileUser, setProfileUser] = useState<User | null>(() => {
        if (initialData && initialData.username === username) {
            return initialData;
        }
        return null;
    });

    const [orgMembers, setOrgMembers] = useState<User[]>([]);
    const [memberOrgs, setMemberOrgs] = useState<User[]>([]);
    const [loadingUser, setLoadingUser] = useState(!profileUser);
    const [notFound, setNotFound] = useState(false);

    const [projects, setProjects] = useState<Project[]>([]);
    const [loadingProjects, setLoadingProjects] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalItems, setTotalItems] = useState(0);
    const [jumpPage, setJumpPage] = useState('');

    const [showReportModal, setShowReportModal] = useState(false);
    const [itemsPerPage] = useState(12);

    const { totalDownloads, totalFavorites } = useMemo(() => {
        return projects.reduce((acc, p) => ({
            totalDownloads: acc.totalDownloads + (p.downloadCount || 0),
            totalFavorites: acc.totalFavorites + (p.favoriteCount || 0)
        }), { totalDownloads: 0, totalFavorites: 0 });
    }, [projects]);

    const isFollowing = useMemo(() => {
        return currentUser?.followingIds?.includes(profileUser?.id || '') || false;
    }, [currentUser, profileUser]);

    const [isFollowingOptimistic, setIsFollowingOptimistic] = useState<boolean | null>(null);
    const actualIsFollowing = isFollowingOptimistic !== null ? isFollowingOptimistic : isFollowing;

    const followerCount = useMemo(() => {
        if (!profileUser) return 0;
        const base = profileUser.followerIds?.length || 0;
        const iAmRecorded = currentUser && profileUser.followerIds?.includes(currentUser.id);

        if (actualIsFollowing && !iAmRecorded) return base + 1;
        if (!actualIsFollowing && iAmRecorded) return base - 1;
        return base;
    }, [profileUser, currentUser, actualIsFollowing]);

    useEffect(() => {
        const fetchUserData = async () => {
            if (!username) return;
            if (profileUser && profileUser.username === username) {
                setLoadingUser(false);
                return;
            }
            setLoadingUser(true);
            setNotFound(false);
            try {
                const lookupRes = await api.get(`/users/lookup/${username}`);
                const userId = lookupRes.data.id;

                const userRes = await api.get(`/user/profile/${userId}`);
                const userData = userRes.data;
                setProfileUser(userData);

                if (userData.accountType === 'ORGANIZATION') {
                    try {
                        const membersRes = await api.get(`/orgs/${userData.id}/members`);
                        setOrgMembers(membersRes.data);
                    } catch (e) {}
                } else {
                    try {
                        const orgsRes = await api.get(`/users/${userData.id}/organizations`);
                        setMemberOrgs(orgsRes.data);
                    } catch (e) {}
                }
            } catch (error: any) {
                if (error.response && error.response.status === 404) {
                    setNotFound(true);
                }
            } finally {
                setLoadingUser(false);
            }
        };
        fetchUserData();
    }, [username]);

    const fetchProjects = useCallback(async () => {
        if (!profileUser?.id) return;

        setLoadingProjects(true);
        try {
            const params = {
                page,
                size: itemsPerPage,
            };

            const projectsRes = await api.get(`/creators/${profileUser.id}/projects`, { params });

            setProjects(projectsRes.data.content);
            setTotalPages(projectsRes.data.totalPages);
            setTotalItems(projectsRes.data.totalElements);
        } catch (error) {
            setProjects([]);
        } finally {
            setLoadingProjects(false);
        }
    }, [profileUser?.id, page, itemsPerPage]);

    useEffect(() => {
        if (!profileUser?.id) return;
        const timer = setTimeout(() => {
            fetchProjects();
        }, 300);
        return () => clearTimeout(timer);
    }, [fetchProjects, profileUser?.id]);

    useEffect(() => {
        if (profileUser && !loadingProjects) {
            const canonicalPath = SiteRoutes.creator(profileUser.username);
            const currentPrefixMatch = location.pathname.match(/^\/(user|creator)\/[^/]+/i);

            if (currentPrefixMatch) {
                const currentBase = currentPrefixMatch[0];
                if (currentBase !== canonicalPath) {
                    const newPath = location.pathname.replace(currentBase, canonicalPath);
                    navigate(
                        { pathname: newPath, search: location.search, hash: location.hash },
                        { replace: true }
                    );
                }
            }
        }
    }, [profileUser, loadingProjects, totalItems, location.pathname, location.search, location.hash, navigate]);

    const handleToggleFollow = async () => {
        if (!currentUser) { navigate(SiteRoutes.login()); return; }
        if (!profileUser) return;
        const previousState = actualIsFollowing;
        setIsFollowingOptimistic(!previousState);
        try {
            if (previousState) await api.post(`/user/unfollow/${profileUser.id}`);
            else await api.post(`/user/follow/${profileUser.id}`);
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

    if (loadingUser) return <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark"><Spinner fullScreen /></div>;
    if (notFound) return <NotFound />;
    if (!profileUser) return <div className="min-h-screen flex flex-col items-center justify-center"><h2 className="text-2xl font-bold">User not found</h2><button onClick={onBack}>Go Back</button></div>;

    const stats = {
        downloads: totalDownloads,
        favorites: totalFavorites,
        followers: followerCount,
        projects: totalItems
    };

    const isSelf = currentUser?.id === profileUser.id;

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark pb-20">
            {profileUser && (
                <ReportModal
                    isOpen={showReportModal}
                    onClose={() => setShowReportModal(false)}
                    targetId={profileUser.id}
                    targetType="USER"
                    targetTitle={profileUser.username}
                />
            )}

            <ProfileLayout
                user={profileUser}
                stats={stats}
                isFollowing={actualIsFollowing}
                onToggleFollow={handleToggleFollow}
                isSelf={isSelf}
                isLoggedIn={!!currentUser}
                onBack={onBack}
                onReport={!isSelf ? () => setShowReportModal(true) : undefined}
            >
                <div className="w-full">
                    {profileUser.accountType === 'ORGANIZATION' && orgMembers.length > 0 && (
                        <div className="mb-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
                            <h2 className="text-xl font-bold text-slate-900 dark:text-white mb-4 flex items-center gap-2">
                                <Users className="w-5 h-5 text-purple-500" />
                                Organization Members
                            </h2>
                            <div className="flex flex-wrap gap-4">
                                {orgMembers.map(member => {
                                    const membership = profileUser.organizationMembers?.find(m => m.userId === member.id);
                                    const role = profileUser.organizationRoles?.find(r => r.id === membership?.roleId);
                                    const roleName = role?.name || 'Member';

                                    return (
                                        <Link
                                            key={member.id}
                                            to={SiteRoutes.creator(member.username)}
                                            className="flex items-center gap-3 p-2 pr-4 bg-white/50 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-xl hover:border-modtale-accent dark:hover:border-modtale-accent transition-all group backdrop-blur-md"
                                        >
                                            <div className="w-10 h-10 rounded-lg overflow-hidden bg-slate-100 border border-slate-200 dark:border-white/10 flex items-center justify-center">
                                                {member.avatarUrl ? (
                                                    <img src={member.avatarUrl} alt={member.username} className="w-full h-full object-cover" />
                                                ) : (
                                                    <span className="font-bold text-slate-400 text-xs">{member.username.charAt(0).toUpperCase()}</span>
                                                )}
                                            </div>
                                            <div>
                                                <div className="font-bold text-slate-800 dark:text-slate-200 group-hover:text-modtale-accent transition-colors text-sm">{member.username}</div>
                                                <div className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">
                                                    {roleName}
                                                </div>
                                            </div>
                                        </Link>
                                    );
                                })}
                            </div>
                        </div>
                    )}

                    {profileUser.accountType === 'USER' && memberOrgs.length > 0 && (
                        <div className="mb-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
                            <h2 className="text-xl font-bold text-slate-900 dark:text-white mb-4 flex items-center gap-2">
                                <Building2 className="w-5 h-5 text-blue-500" />
                                Member Organizations
                            </h2>
                            <div className="flex flex-wrap gap-4">
                                {memberOrgs.map(org => (
                                    <Link
                                        key={org.id}
                                        to={SiteRoutes.creator(org.username)}
                                        className="flex items-center gap-3 p-2 pr-4 bg-white/50 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-xl hover:border-modtale-accent dark:hover:border-modtale-accent transition-all group backdrop-blur-md"
                                    >
                                        <div className="w-10 h-10 rounded-lg overflow-hidden bg-slate-100 border border-slate-200 dark:border-white/10 flex items-center justify-center">
                                            {org.avatarUrl ? (
                                                <img src={org.avatarUrl} alt={org.username} className="w-full h-full object-cover" />
                                            ) : (
                                                <span className="font-bold text-slate-400 text-xs">{org.username.charAt(0).toUpperCase()}</span>
                                            )}
                                        </div>
                                        <div>
                                            <div className="font-bold text-slate-800 dark:text-slate-200 group-hover:text-modtale-accent transition-colors text-sm">{org.username}</div>
                                            <div className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">
                                                Organization
                                            </div>
                                        </div>
                                    </Link>
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
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-[1800px]:grid-cols-4 gap-4 md:gap-6 mt-4">
                            {[...Array(itemsPerPage)].map((_, i) => (
                                <div
                                    key={i}
                                    className="h-[154px] bg-white/40 dark:bg-white/5 backdrop-blur-md rounded-2xl animate-pulse border border-slate-200 dark:border-white/10 relative overflow-hidden"
                                >
                                    <div className="absolute inset-0 -translate-x-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-white/20 to-transparent"></div>
                                </div>
                            ))}
                        </div>
                    ) : projects.length > 0 ? (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-[1800px]:grid-cols-4 gap-4 md:gap-6 mt-4">
                            {projects.map((project) => (
                                <div key={project.id} className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                    <ProjectCard
                                        project={project}
                                        path={SiteRoutes.project(project)}
                                        isFavorite={likedModIds.includes(project.id)}
                                        onToggleFavorite={() => { onToggleFavorite(project.id); }}
                                        isLoggedIn={!!currentUser}
                                    />
                                </div>
                            ))}
                        </div>
                    ) : (
                        <EmptyState
                            icon={Package}
                            title="No projects found"
                            message="This user hasn't published any projects yet."
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
                                <input type="number" min={1} max={totalPages} value={jumpPage} onChange={(e) => setJumpPage(e.target.value)} className="w-12 h-10 rounded-lg border border-slate-200 dark:border-white/10 bg-white/50 dark:bg-white/5 px-1 text-sm font-bold text-center dark:text-white focus:outline-none focus:ring-2 focus:ring-modtale-accent transition-all backdrop-blur-md" placeholder="#" />
                                <button type="submit" disabled={!jumpPage} className="w-10 h-10 flex items-center justify-center rounded-lg bg-white/50 dark:bg-white/5 hover:bg-modtale-accent hover:text-white dark:hover:bg-modtale-accent text-slate-500 dark:text-slate-400 transition-colors disabled:opacity-50 backdrop-blur-md"><CornerDownLeft className="w-4 h-4" /></button>
                            </form>
                        </div>
                    )}
                </div>
            </ProfileLayout>
        </div>
    );
};