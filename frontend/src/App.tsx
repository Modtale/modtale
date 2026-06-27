import React, { useState, useEffect, Suspense, lazy, useCallback, useRef } from 'react';
import { Route, Routes, useNavigate, useLocation, Navigate, BrowserRouter } from 'react-router-dom';
import { StaticRouter } from 'react-router';
import { HelmetProvider } from 'react-helmet-async';
import { api } from '@/utils/api';

import { Navbar } from '@/modules/core/components/Navbar';
import { Footer } from '@/modules/core/components/Footer';
import { SEOHead } from '@/modules/core/components/SEOHead';
import { Home } from '@/modules/home/views/Home';
import { Browse } from '@/modules/discovery/views/Browse';
import { ProjectDetails } from '@/modules/project/views/ProjectDetails';

import { Spinner } from '@/components/ui/Spinner';
import { ErrorBoundary } from '@/components/ui/error/ErrorBoundary';
import NotFound from '@/components/ui/error/NotFound';

import { SSRProvider } from '@/context/SSRContext';
import { ExternalLinkProvider } from '@/context/ExternalLinkContext';
import { NotificationProvider } from '@/context/NotificationsContext';
import { ToastProvider } from '@/components/ui/Toast';
import { MobileProvider } from '@/context/MobileContext';
import type { User } from '@/types';
import { SiteRoutes } from '@/utils/routes';
import { STATUS_PAGE_URL } from '@/utils/status';
import type { Classification } from '@/data/categories';
import { normalizeUser } from '@/utils/users';
import { clearPendingSignInMethod, completeSignInMethod } from '@/modules/auth/api/authClient';

const StatusModal = lazy(() => import('@/components/ui/StatusModal').then((module) => ({ default: module.StatusModal })));
const Onboarding = lazy(() => import('@/modules/user/components/Onboarding').then((module) => ({ default: module.Onboarding })));
const TermsOfService = lazy(() => import('@/modules/core/views/TermsOfService').then((module) => ({ default: module.TermsOfService })));
const PrivacyPolicy = lazy(() => import('@/modules/core/views/PrivacyPolicy').then((module) => ({ default: module.PrivacyPolicy })));
const UserProfile = lazy(() => import('@/modules/user/views/UserProfile').then((module) => ({ default: module.UserProfile })));
const Dashboard = lazy(() => import('@/modules/user/views/Dashboard').then((module) => ({ default: module.Dashboard })));
const VerifyEmail = lazy(() => import('@/modules/auth/views/VerifyEmail').then((module) => ({ default: module.VerifyEmail })));
const ResetPassword = lazy(() => import('@/modules/auth/views/ResetPassword').then((module) => ({ default: module.ResetPassword })));
const MfaVerify = lazy(() => import('@/modules/auth/views/MfaVerify').then((module) => ({ default: module.MfaVerify })));
const LauncherAuth = lazy(() => import('@/modules/auth/views/LauncherAuth').then((module) => ({ default: module.LauncherAuth })));
const LauncherPage = lazy(() => import('@/modules/launcher/views/LauncherPage').then((module) => ({ default: module.LauncherPage })));
const WorldModListView = lazy(() => import('@/modules/worldlist/views/WorldModListView').then((module) => ({ default: module.WorldModListView })));
const CreateProject = lazy(() => import('@/modules/project/views/CreateProject').then((module) => ({ default: module.CreateProject })));
const ProjectEditorView = lazy(() => import('@/modules/project/views/ProjectEditor').then((module) => ({ default: module.ProjectEditorView })));
const AdminPanel = lazy(() => import('@/modules/admin/views/AdminPanel').then((module) => ({ default: module.AdminPanel })));
const ApiDocs = lazy(() => import('@/modules/core/views/ApiDocs').then((module) => ({ default: module.ApiDocs })));
const SwaggerDocs = lazy(() => import('@/modules/core/views/SwaggerDocs').then((module) => ({ default: module.SwaggerDocs })));

const RouteLoading = () => <div className="p-20 flex justify-center"><Spinner /></div>;

type FavoriteToggleOptions = {
    onError?: () => void;
};

const StatusRedirect = () => {
    useEffect(() => {
        if (typeof window !== 'undefined') {
            window.location.replace(STATUS_PAGE_URL);
        }
    }, []);

    return (
        <main className="min-h-[60vh] flex items-center justify-center bg-slate-50 px-6 dark:bg-modtale-dark">
            <div className="max-w-md rounded-lg border border-slate-200 bg-white p-6 text-center shadow-sm dark:border-white/10 dark:bg-slate-900">
                <h1 className="text-xl font-black text-slate-950 dark:text-white">Opening Modtale Status</h1>
                <p className="mt-2 text-sm font-medium leading-6 text-slate-500 dark:text-slate-400">
                    Redirecting to {STATUS_PAGE_URL}.
                </p>
                <a
                    href={STATUS_PAGE_URL}
                    className="mt-5 inline-flex h-10 items-center justify-center rounded-lg bg-modtale-accent px-4 text-sm font-bold text-white transition hover:bg-blue-600"
                >
                    Open Status
                </a>
            </div>
        </main>
    );
};

const hasLikelyAuthCookie = () => {
    if (typeof document === 'undefined') return false;
    const cookies = document.cookie || '';
    return /(?:^|;\s*)(SESSION|JSESSIONID|XSRF-TOKEN)=/.test(cookies);
};

const setProjectLikedState = (user: User, projectId: string, liked: boolean): User => {
    const likedProjectIds = user.likedProjectIds || [];
    const alreadyLiked = likedProjectIds.includes(projectId);

    if (alreadyLiked === liked) return user;

    return {
        ...user,
        likedProjectIds: liked
            ? [...likedProjectIds, projectId]
            : likedProjectIds.filter(likedProjectId => likedProjectId !== projectId)
    };
};

const ScrollToTop = () => {
    const { pathname } = useLocation();
    const previousPathnameRef = useRef<string | undefined>(undefined);

    useEffect(() => {
        const previousPathname = previousPathnameRef.current;
        previousPathnameRef.current = pathname;

        if (previousPathname && SiteRoutes.isSameProjectModalContext(previousPathname, pathname)) {
            return;
        }

        window.scrollTo(0, 0);
    }, [pathname]);

    return null;
};

const AppContent: React.FC = () => {
    const [user, setUser] = useState<User | null>(null);
    const [loadingAuth, setLoadingAuth] = useState(true);
    const [downloadedSessionIds, setDownloadedSessionIds] = useState<Set<string>>(new Set());
    const [globalError, setGlobalError] = useState<string | null>(null);
    const [showOnboarding, setShowOnboarding] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(true);
    const [statusModal, setStatusModal] = useState<{ type: 'success' | 'error' | 'warning' | 'info'; title: string; msg: string } | null>(null);
    const userRef = useRef<User | null>(null);
    const pendingFavoriteIdsRef = useRef<Set<string>>(new Set());

    const navigate = useNavigate();
    const location = useLocation();
    const isHomeRoute = location.pathname === SiteRoutes.home() || location.pathname === '/login';

    useEffect(() => {
        const params = new URLSearchParams(location.search);
        const oauthError = params.get('oauth_error');
        if (oauthError) {
            const decodedError = decodeURIComponent(oauthError).replace(/\+/g, ' ');
            setGlobalError(decodedError);
            clearPendingSignInMethod();
            params.delete('oauth_error');
            const remainingSearch = params.toString();
            navigate(`${location.pathname}${remainingSearch ? `?${remainingSearch}` : ''}`, { replace: true });
        }
    }, [location, navigate]);

    useEffect(() => {
        const stored = localStorage.getItem('modtale-theme');
        if (stored === 'light') setIsDarkMode(false);
    }, []);

    useEffect(() => {
        const root = document.documentElement;
        if (isDarkMode) {
            root.classList.add('dark');
        } else {
            root.classList.remove('dark');
        }
    }, [isDarkMode]);

    const toggleDarkMode = () => {
        setIsDarkMode(prev => {
            const newVal = !prev;
            localStorage.setItem('modtale-theme', newVal ? 'dark' : 'light');
            return newVal;
        });
    };

    useEffect(() => {
        userRef.current = user;
    }, [user]);

    const fetchUser = useCallback(async () => {
        if (!hasLikelyAuthCookie()) {
            setLoadingAuth(false);
            return;
        }

        try {
            const res = await api.get(`/user/me?t=${Date.now()}`);
            if (res.data) {
                const normalizedUser = normalizeUser(res.data);
                userRef.current = normalizedUser;
                setUser(normalizedUser);
                completeSignInMethod();
                if ((res.data as any).is_new_account) {
                    setShowOnboarding(true);
                }
            }
        } catch (e: any) {
            userRef.current = null;
            setUser(null);
        } finally {
            setLoadingAuth(false);
        }
    }, []);

    useEffect(() => {
        fetchUser();
    }, [fetchUser]);

    const handleLogout = async () => {
        try {
            await api.post('/auth/logout');
            userRef.current = null;
            setUser(null);
            setShowOnboarding(false);
            navigate(SiteRoutes.home());
        } catch (e) {
            onShowStatus('error', 'Sign Out Failed', 'We could not end your session right now. Please try again.');
        }
    };

    const handleNavigate = (page: string) => { navigate(page === 'home' ? SiteRoutes.home() : `/${page}`); };
    const handleUserClick = (userId: string, username?: string) => { navigate(SiteRoutes.creator(userId, username)); };

    const handleToggleFavorite = useCallback((id: string, options?: FavoriteToggleOptions) => {
        if (!id || pendingFavoriteIdsRef.current.has(id)) return undefined;

        const currentUser = userRef.current;
        if (!currentUser) return undefined;

        const wasLiked = (currentUser.likedProjectIds || []).includes(id);
        const nextLiked = !wasLiked;
        const nextUser = setProjectLikedState(currentUser, id, nextLiked);

        userRef.current = nextUser;
        pendingFavoriteIdsRef.current.add(id);
        setUser(nextUser);

        api.post(`/projects/${id}/favorite`)
            .catch(() => {
                setUser(latestUser => {
                    if (!latestUser || latestUser.id !== currentUser.id) return latestUser;
                    const revertedUser = setProjectLikedState(latestUser, id, wasLiked);
                    userRef.current = revertedUser;
                    return revertedUser;
                });
                options?.onError?.();
                fetchUser();
            })
            .finally(() => {
                pendingFavoriteIdsRef.current.delete(id);
            });

        return nextLiked;
    }, [fetchUser]);

    const handleDownload = (id: string) => { if (!downloadedSessionIds.has(id)) setDownloadedSessionIds(prev => new Set(prev).add(id)); };
    const onShowStatus = (type: 'success' | 'error' | 'warning' | 'info', title: string, msg: string) => setStatusModal({ type, title, msg });

    const renderBrowse = (classification?: Classification) => (
        <Browse
            likedProjectIds={user?.likedProjectIds || []}
            onToggleFavorite={handleToggleFavorite}
            isLoggedIn={!!user}
            initialClassification={classification}
        />
    );

    const renderProjectDetail = () => (
        <ProjectDetails
            onToggleFavorite={handleToggleFavorite}
            isLiked={(id) => user?.likedProjectIds?.includes(id) || false}
            currentUser={user}
            onRefresh={async () => {}}
            onDownload={handleDownload}
            downloadedSessionIds={downloadedSessionIds}
        />
    );

    return (
        <NotificationProvider userId={user?.id}>
            <div className={`min-h-screen bg-white dark:bg-modtale-dark text-slate-900 dark:text-slate-300 font-sans flex flex-col`}>
                <ScrollToTop /> <SEOHead />

                <Suspense fallback={null}>
                    {globalError && (
                        <StatusModal
                            type="error"
                            title="Login Failed"
                            message={globalError}
                            onClose={() => setGlobalError(null)}
                        />
                    )}

                    {statusModal && (
                        <StatusModal
                            type={statusModal.type}
                            title={statusModal.title}
                            message={statusModal.msg}
                            onClose={() => setStatusModal(null)}
                        />
                    )}

                    {user && showOnboarding && (
                        <Onboarding
                            isOpen={showOnboarding}
                            onClose={() => setShowOnboarding(false)}
                            currentUsername={user.username}
                            currentAvatar={user.avatarUrl}
                            suggestedUsername={(user as any).suggested_username}
                            suggestedAvatar={(user as any).suggested_avatar}
                        />
                    )}
                </Suspense>

                <Navbar
                    user={user}
                    onLogout={handleLogout}
                    currentPage={location.pathname.replace('/', '') || 'home'}
                    onNavigate={handleNavigate}
                    isDarkMode={isDarkMode}
                    toggleDarkMode={toggleDarkMode}
                    onUserClick={handleUserClick}
                />

                <div className="flex-1">
                    <ErrorBoundary>
                        {isHomeRoute ? (
                            <Home
                                likedProjectIds={user?.likedProjectIds || []}
                                onToggleFavorite={handleToggleFavorite}
                                isLoggedIn={!!user}
                                currentUser={user}
                            />
                        ) : (
                            <Suspense fallback={<RouteLoading />}>
                                <Routes>
                                    <Route path="/mods" element={renderBrowse()} />
                                    <Route path="/projects" element={<Navigate to={SiteRoutes.browse()} replace />} />
                                    <Route path="/plugins" element={renderBrowse('PLUGIN')} />
                                    <Route path="/modpacks" element={renderBrowse('MODPACK')} />
                                    <Route path="/worlds" element={renderBrowse('SAVE')} />
                                    <Route path="/art" element={renderBrowse('ART')} />
                                    <Route path="/data" element={renderBrowse('DATA')} />

                                    <Route path="/upload" element={
                                        loadingAuth ? <RouteLoading /> :
                                            <CreateProject onNavigate={handleNavigate} onRefresh={async () => {}} currentUser={user} />
                                    } />

                                    <Route path="/dashboard/*" element={
                                        loadingAuth ? <RouteLoading /> :
                                            user ? <Dashboard user={user} onRefreshUser={fetchUser} /> :
                                                <Navigate to={SiteRoutes.home()} />
                                    } />

                                    {['/project/:id', '/mod/:id', '/modpack/:id', '/world/:id'].map(path => (
                                        <React.Fragment key={path}>
                                            <Route path={path} element={renderProjectDetail()}>
                                                <Route path="download" element={null} />
                                                <Route path="changelog" element={null} />
                                                <Route path="gallery" element={null} />
                                                <Route path="wiki/*" element={null} />
                                            </Route>
                                            <Route path={`${path}/edit`} element={
                                                loadingAuth ? <RouteLoading /> :
                                                    user ? <ProjectEditorView currentUser={user} onShowStatus={onShowStatus} /> :
                                                        <Navigate to={SiteRoutes.home()} />
                                            } />
                                        </React.Fragment>
                                    ))}

                                    <Route path="/creator/:id" element={
                                        <UserProfile
                                            onBack={() => navigate(SiteRoutes.home())}
                                            likedModIds={user?.likedProjectIds || []}
                                            onToggleFavorite={handleToggleFavorite}
                                            currentUser={user}
                                            onRefreshUser={fetchUser}
                                        />
                                    } />
                                    <Route path="/verify" element={<VerifyEmail />} />
                                    <Route path="/reset-password" element={<ResetPassword />} />
                                    <Route path="/mfa" element={<MfaVerify />} />
                                    <Route path="/launcher" element={<LauncherPage />} />
                                    <Route path="/launcher/auth" element={<LauncherAuth user={user} loadingAuth={loadingAuth} />} />
                                    <Route path="/lists/:id" element={<WorldModListView />} />

                                    <Route path="/terms" element={<TermsOfService />} />
                                    <Route path="/privacy" element={<PrivacyPolicy />} />
                                    <Route path="/status" element={<StatusRedirect />} />

                                    <Route path="/api-docs" element={<ApiDocs />} />
                                    <Route path="/api-docs/swagger" element={<SwaggerDocs />} />

                                    <Route path="/admin" element={
                                        loadingAuth ? <RouteLoading /> :
                                            user ? <AdminPanel currentUser={user} /> :
                                                <Navigate to={SiteRoutes.home()} />
                                    } />

                                    <Route path="*" element={<NotFound />} />
                                </Routes>
                            </Suspense>
                        )}
                    </ErrorBoundary>
                </div>
                <Footer isDarkMode={isDarkMode} />
            </div>
        </NotificationProvider>
    );
};

export const App: React.FC<any> = ({ initialPath, ssrData }) => {
    return (
        <SSRProvider data={ssrData || null} initialPath={initialPath || '/'}>
            <HelmetProvider>
                <MobileProvider>
                    <ExternalLinkProvider>
                        <ToastProvider>
                            {import.meta.env.SSR ? (
                                <StaticRouter location={initialPath || "/"}>
                                    <AppContent />
                                </StaticRouter>
                            ) : (
                                <BrowserRouter>
                                    <AppContent />
                                </BrowserRouter>
                            )}
                        </ToastProvider>
                    </ExternalLinkProvider>
                </MobileProvider>
            </HelmetProvider>
        </SSRProvider>
    );
};
export default App;
