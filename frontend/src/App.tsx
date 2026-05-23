import React, { useState, useEffect, useRef } from 'react';
import { Route, Routes, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { BrowserRouter } from 'react-router-dom';
import { StaticRouter } from 'react-router-dom/server';
import { HelmetProvider } from 'react-helmet-async';
import { api, BACKEND_URL } from '@/utils/api';

import { Navbar } from '@/modules/core/components/Navbar';
import { Footer } from '@/modules/core/components/Footer';
import { SEOHead } from '@/modules/core/components/SEOHead';
import { TermsOfService } from '@/modules/core/views/TermsOfService';
import { PrivacyPolicy } from '@/modules/core/views/PrivacyPolicy';
import { Status } from '@/modules/core/views/Status';

import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { ErrorBoundary } from '@/components/ui/error/ErrorBoundary';
import NotFound from '@/components/ui/error/NotFound';

import { Onboarding } from '@/modules/user/components/Onboarding';
import { UserProfile } from '@/modules/user/views/UserProfile';
import { Dashboard } from '@/modules/user/views/Dashboard';
import { VerifyEmail } from '@/modules/auth/views/VerifyEmail';
import { ResetPassword } from '@/modules/auth/views/ResetPassword';
import { MfaVerify } from '@/modules/auth/views/MfaVerify';

import { Home } from '@/modules/home/views/Home';
import { Browse } from '@/modules/discovery/views/Browse';
import { CreateProject } from '@/modules/project/views/CreateProject';
import { ProjectDetails } from '@/modules/project/views/ProjectDetails';
import { ProjectEditorView } from '@/modules/project/views/ProjectEditor';
import { AdminPanel } from '@/modules/admin/views/AdminPanel';

import { ApiDocs } from '@/modules/core/views/ApiDocs';
import { JamsList } from '@/modules/jam/views/JamsList';
import { JamDetail } from '@/modules/jam/views/JamDetail';

import { SSRProvider } from '@/context/SSRContext';
import { ExternalLinkProvider } from '@/context/ExternalLinkContext';
import { NotificationProvider } from '@/context/NotificationsContext';
import { ToastProvider } from '@/components/ui/Toast';
import { MobileProvider } from '@/context/MobileContext';
import type { User } from '@/types';
import { SiteRoutes } from '@/utils/routes';
import type { Classification } from '@/data/categories';

const ScrollToTop = () => {
    const { pathname } = useLocation();
    const previousPathname = useRef<string | null>(null);
    useEffect(() => {
        const prev = previousPathname.current;
        previousPathname.current = pathname;
        if (!prev) return;

        const jamTabPattern = /^\/jam\/[^/]+\/(overview|rules|entries)$/;
        const prevJamTabMatch = prev.match(jamTabPattern);
        const nextJamTabMatch = pathname.match(jamTabPattern);

        if (prevJamTabMatch && nextJamTabMatch) {
            const prevJamBase = prev.replace(/\/(overview|rules|entries)$/, '');
            const nextJamBase = pathname.replace(/\/(overview|rules|entries)$/, '');
            if (prevJamBase === nextJamBase) return;
        }

        window.scrollTo(0, 0);
    }, [pathname]);
    return null;
};

const AppContent: React.FC<{ initialClassification?: Classification }> = ({ initialClassification }) => {
    const [user, setUser] = useState<User | null>(null);
    const [loadingAuth, setLoadingAuth] = useState(true);
    const [downloadedSessionIds, setDownloadedSessionIds] = useState<Set<string>>(new Set());
    const [globalError, setGlobalError] = useState<string | null>(null);
    const [showOnboarding, setShowOnboarding] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(true);
    const [statusModal, setStatusModal] = useState<{ type: 'success' | 'error' | 'warning' | 'info'; title: string; msg: string } | null>(null);

    const navigate = useNavigate();
    const location = useLocation();

    useEffect(() => {
        const params = new URLSearchParams(location.search);
        const oauthError = params.get('oauth_error');
        if (oauthError) {
            const decodedError = decodeURIComponent(oauthError).replace(/\+/g, ' ');
            setGlobalError(decodedError);
            navigate(location.pathname, { replace: true });
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

    const fetchUser = async () => {
        try {
            const res = await api.get(`/user/me?t=${Date.now()}`);
            if (res.data) {
                setUser(res.data);
                if ((res.data as any).is_new_account) {
                    setShowOnboarding(true);
                }
            }
        } catch (e: any) {
            setUser(null);
        } finally {
            setLoadingAuth(false);
        }
    };

    useEffect(() => {
        fetchUser();
    }, []);

    const handleLogout = async () => {
        try { await api.post(`${BACKEND_URL}/logout`); } catch (e) {}
        setUser(null);
        navigate(SiteRoutes.home());
    };

    const handleNavigate = (page: string) => { navigate(page === 'home' ? SiteRoutes.home() : `/${page}`); };
    const handleUserClick = (username: string) => { navigate(SiteRoutes.creator(username)); };

    const handleToggleFavorite = async (id: string) => {
        if (!user) return;
        const isLiked = user.likedProjectIds?.includes(id);
        const newProjectLikes = isLiked ? (user.likedProjectIds || []).filter(lid => lid !== id) : [...(user.likedProjectIds || []), id];
        setUser({ ...user, likedProjectIds: newProjectLikes });
        try { await api.post(`/projects/${id}/favorite`); } catch (e) { fetchUser(); }
    };

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

                {user && (
                    <Onboarding
                        isOpen={showOnboarding}
                        onClose={() => setShowOnboarding(false)}
                        currentUsername={user.username}
                        currentAvatar={user.avatarUrl}
                        suggestedUsername={(user as any).suggested_username}
                        suggestedAvatar={(user as any).suggested_avatar}
                    />
                )}

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
                        <Routes>
                            <Route path="/" element={<Home user={user} />} />

                            <Route path="/mods" element={renderBrowse()} />
                            <Route path="/projects" element={<Navigate to={SiteRoutes.browse()} replace />} />
                            <Route path="/plugins" element={renderBrowse('PLUGIN')} />
                            <Route path="/modpacks" element={renderBrowse('MODPACK')} />
                            <Route path="/worlds" element={renderBrowse('SAVE')} />
                            <Route path="/art" element={renderBrowse('ART')} />
                            <Route path="/data" element={renderBrowse('DATA')} />

                            <Route path={SiteRoutes.jams()} element={<JamsList currentUser={user} />} />
                            <Route path="/jam/:slug/*" element={<JamDetail currentUser={user} />} />
                            <Route path="/jam/:id/edit" element={<JamDetail currentUser={user} />} />

                            <Route path="/upload" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    <CreateProject onNavigate={handleNavigate} onRefresh={async () => {}} currentUser={user} />
                            } />

                            <Route path="/dashboard/*" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    user ? <Dashboard user={user} onRefreshUser={fetchUser} /> :
                                        <Navigate to={SiteRoutes.home()} />
                            } />

                            {['/project/:id', '/mod/:id', '/modpack/:id', '/world/:id'].map(path => (
                                <React.Fragment key={path}>
                                    <Route path={path} element={renderProjectDetail()} />
                                    <Route path={`${path}/download`} element={renderProjectDetail()} />
                                    <Route path={`${path}/changelog`} element={renderProjectDetail()} />
                                    <Route path={`${path}/gallery`} element={renderProjectDetail()} />
                                    <Route path={`${path}/wiki/*`} element={renderProjectDetail()} />
                                    <Route path={`${path}/edit`} element={
                                        loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                            user ? <ProjectEditorView currentUser={user} onShowStatus={onShowStatus} /> :
                                                <Navigate to={SiteRoutes.home()} />
                                    } />
                                </React.Fragment>
                            ))}

                            <Route path="/creator/:username" element={
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

                            <Route path="/terms" element={<TermsOfService />} />
                            <Route path="/privacy" element={<PrivacyPolicy />} />
                            <Route path="/status" element={<Status />} />

                            <Route path="/api-docs" element={<ApiDocs />} />

                            <Route path="/admin" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    user ? <AdminPanel currentUser={user} /> :
                                        <Navigate to={SiteRoutes.home()} />
                            } />

                            <Route path="*" element={<NotFound />} />
                        </Routes>
                    </ErrorBoundary>
                </div>
                <Footer isDarkMode={isDarkMode} />
            </div>
        </NotificationProvider>
    );
};

export const App: React.FC<any> = ({ initialPath, initialClassification, ssrData }) => {
    return (
        <SSRProvider data={ssrData || null}>
            <HelmetProvider>
                <MobileProvider>
                    <ExternalLinkProvider>
                        <ToastProvider>
                            {import.meta.env.SSR ? (
                                <StaticRouter location={initialPath || "/"}>
                                    <AppContent initialClassification={initialClassification} />
                                </StaticRouter>
                            ) : (
                                <BrowserRouter>
                                    <AppContent initialClassification={initialClassification} />
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
