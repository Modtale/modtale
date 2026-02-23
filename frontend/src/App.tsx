import React, { useState, useEffect, Suspense, lazy } from 'react';
import { Route, Routes, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { BrowserRouter } from 'react-router-dom';
import { StaticRouter } from 'react-router-dom/server';
import { HelmetProvider } from 'react-helmet-async';
import { api, BACKEND_URL } from './utils/api';

import { Navbar } from './components/Navbar';
import { Footer } from './components/Footer.tsx';
import { SEOHead } from './components/SEOHead';
import { Spinner } from './components/ui/Spinner';
import { StatusModal } from './components/ui/StatusModal';
import { OnboardingModal } from './components/user/OnboardingModal';

import type { Mod, Modpack, World, User } from './types';
import { createSlug } from './utils/slug';
import type { Classification } from './data/categories';
import { SSRProvider } from './context/SSRContext';
import { ExternalLinkProvider } from './context/ExternalLinkContext';
import { NotificationProvider } from './context/NotificationsContext.tsx';
import { ToastProvider } from './components/ui/Toast';
import { MobileProvider } from './context/MobileContext';

const Home = lazy(() => import('./react-pages/Home').then(module => ({ default: module.Home })));
const Upload = lazy(() => import('./react-pages/resources/Upload').then(module => ({ default: module.Upload })));
const CreatorProfile = lazy(() => import('./react-pages/user/CreatorProfile.tsx').then(module => ({ default: module.CreatorProfile })));
const ModDetail = lazy(() => import('./react-pages/resources/ModDetail').then(module => ({ default: module.ModDetail })));
const EditMod = lazy(() => import('./react-pages/resources/EditMod').then(module => ({ default: module.EditMod })));
const TermsOfService = lazy(() => import('./react-pages/TermsOfService').then(module => ({ default: module.TermsOfService })));
const PrivacyPolicy = lazy(() => import('./react-pages/PrivacyPolicy').then(module => ({ default: module.PrivacyPolicy })));
const Dashboard = lazy(() => import('./react-pages/user/Dashboard.tsx').then(module => ({ default: module.Dashboard })));
const AdminPanel = lazy(() => import('./react-pages/AdminPanel.tsx').then(module => ({ default: module.AdminPanel })));
const ApiDocs = lazy(() => import('./react-pages/ApiDocs.tsx').then(module => ({ default: module.ApiDocs })));
const Status = lazy(() => import('./react-pages/Status').then(module => ({ default: module.Status })));
const VerifyEmail = lazy(() => import('./react-pages/auth/VerifyEmail.tsx').then(module => ({ default: module.VerifyEmail })));
const ResetPassword = lazy(() => import('./react-pages/auth/ResetPassword.tsx').then(module => ({ default: module.ResetPassword })));
const MfaVerify = lazy(() => import('./react-pages/auth/MfaVerify').then(module => ({ default: module.MfaVerify })));
const Analytics = lazy(() => import('@/components/dashboard/Analytics.tsx').then(module => ({ default: module.Analytics })));
const NotFound = lazy(() => import('./components/ui/error/NotFound.tsx'));

const ScrollToTop = () => {
    const { pathname } = useLocation();
    useEffect(() => {
        window.scrollTo(0, 0);
    }, [pathname]);
    return null;
};

const AppContent: React.FC<{ initialClassification?: Classification }> = ({ initialClassification }) => {
    const [user, setUser] = useState<User | null>(null);
    const [loadingAuth, setLoadingAuth] = useState(true);
    const [mounted, setMounted] = useState(false);
    const [downloadedSessionIds, setDownloadedSessionIds] = useState<Set<string>>(new Set());
    const [globalError, setGlobalError] = useState<string | null>(null);
    const [showOnboarding, setShowOnboarding] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(true);

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
            setUser(res.data);
            if (res.data && (res.data as any).is_new_account) {
                setShowOnboarding(true);
            }
        } catch (e: any) {
            setUser(null);
        } finally {
            setLoadingAuth(false);
        }
    };

    useEffect(() => {
        setMounted(true);
        fetchUser();
    }, []);

    const handleLogout = async () => {
        try { await api.post(`${BACKEND_URL}/logout`); } catch (e) {}
        setUser(null);
        navigate('/');
    };

    const handleNavigate = (page: string) => { navigate(page === 'home' ? '/' : `/${page}`); };
    const handleAuthorClick = (author: string) => { navigate(`/creator/${author}`); };
    const handleModClick = (mod: Mod) => { navigate(`/mod/${createSlug(mod.title, mod.id)}`); }
    const handleWorldClick = (world: World) => { navigate(`/world/${createSlug(world.title, world.id)}`); }

    const handleToggleFavorite = async (id: string) => {
        if (!user) return;
        const isLiked = user.likedModIds?.includes(id);
        const newModLikes = isLiked ? (user.likedModIds || []).filter(lid => lid !== id) : [...(user.likedModIds || []), id];
        setUser({ ...user, likedModIds: newModLikes });
        try { await api.post(`/projects/${id}/favorite`); } catch (e) { fetchUser(); }
    };

    const handleDownload = (id: string) => { if (!downloadedSessionIds.has(id)) setDownloadedSessionIds(prev => new Set(prev).add(id)); };

    const renderHome = (classification?: Classification) => (
        <Home
            onModClick={handleModClick}
            onModpackClick={(pack: Modpack) => handleModClick(pack as unknown as Mod)}
            onWorldClick={handleWorldClick}
            onAuthorClick={handleAuthorClick}
            likedModIds={user?.likedModIds || []}
            onToggleFavoriteMod={handleToggleFavorite}
            onToggleFavoriteModpack={handleToggleFavorite}
            isLoggedIn={!!user}
            initialClassification={classification}
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

                {user && (
                    <OnboardingModal
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
                    onAuthorClick={handleAuthorClick}
                />

                <div className="flex-1">
                    <Suspense fallback={<div className="p-20 flex justify-center"><Spinner /></div>}>
                        <Routes>
                            <Route path="/" element={renderHome()} />
                            <Route path="/mods" element={<Navigate to="/" replace />} />
                            <Route path="/plugins" element={renderHome('PLUGIN')} />
                            <Route path="/modpacks" element={renderHome('MODPACK')} />
                            <Route path="/worlds" element={renderHome('SAVE')} />
                            <Route path="/art" element={renderHome('ART')} />
                            <Route path="/data" element={renderHome('DATA')} />

                            <Route path="/upload" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    user ? <Upload onNavigate={handleNavigate} onRefresh={async () => {}} currentUser={user} /> :
                                        <Navigate to="/" />
                            } />

                            <Route path="/dashboard/*" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    user ? <Dashboard user={user} onRefreshUser={fetchUser} /> :
                                        <Navigate to="/" />
                            } />

                            <Route path="/analytics/project/:id" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    user ? <Analytics /> :
                                        <Navigate to="/" />
                            } />

                            <Route path="/mod/:id" element={<ModDetail onToggleFavorite={handleToggleFavorite} isLiked={(id) => user?.likedModIds?.includes(id) || false} currentUser={user} onRefresh={async () => {}} onDownload={handleDownload} downloadedSessionIds={downloadedSessionIds} />} />
                            <Route path="/mod/:id/edit" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    user ? <EditMod currentUser={user} /> :
                                        <Navigate to="/" />
                            } />

                            <Route path="/modpack/:id" element={<ModDetail onToggleFavorite={handleToggleFavorite} isLiked={(id) => user?.likedModIds?.includes(id) || false} currentUser={user} onRefresh={async () => {}} onDownload={handleDownload} downloadedSessionIds={downloadedSessionIds} />} />
                            <Route path="/modpack/:id/edit" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    user ? <EditMod currentUser={user} /> :
                                        <Navigate to="/" />
                            } />

                            <Route path="/world/:id" element={<ModDetail onToggleFavorite={handleToggleFavorite} isLiked={(id) => user?.likedModIds?.includes(id) || false} currentUser={user} onRefresh={async () => {}} onDownload={handleDownload} downloadedSessionIds={downloadedSessionIds} />} />

                            <Route path="/creator/:username" element={
                                <CreatorProfile
                                    onModClick={handleModClick}
                                    onModpackClick={(pack: Modpack) => handleModClick(pack as unknown as Mod)}
                                    onBack={() => handleNavigate('home')}
                                    likedModIds={user?.likedModIds || []}
                                    onToggleFavorite={handleToggleFavorite}
                                    onToggleFavoriteModpack={handleToggleFavorite}
                                    currentUser={user}
                                    onRefreshUser={fetchUser}
                                />
                            } />

                            <Route path="/verify" element={
                                <VerifyEmail
                                    user={user}
                                    isDarkMode={isDarkMode}
                                    toggleDarkMode={toggleDarkMode}
                                    onLogout={handleLogout}
                                    onNavigate={handleNavigate}
                                    currentPage={location.pathname.replace('/', '')}
                                    onAuthorClick={handleAuthorClick}
                                />
                            } />

                            <Route path="/reset-password" element={<ResetPassword />} />

                            <Route path="/mfa" element={
                                <MfaVerify
                                    user={user}
                                    isDarkMode={isDarkMode}
                                    toggleDarkMode={toggleDarkMode}
                                    onLogout={handleLogout}
                                    onNavigate={handleNavigate}
                                    currentPage={location.pathname.replace('/', '')}
                                    onAuthorClick={handleAuthorClick}
                                />
                            } />

                            <Route path="/terms" element={<TermsOfService />} />
                            <Route path="/privacy" element={<PrivacyPolicy />} />
                            <Route path="/api-docs" element={<ApiDocs />} />
                            <Route path="/admin" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    user ? <AdminPanel currentUser={user} /> :
                                        <Navigate to="/" />
                            } />
                            <Route path="*" element={<NotFound />} />
                        </Routes>
                    </Suspense>
                </div>
                <Footer isDarkMode={isDarkMode} />
            </div>
        </NotificationProvider>
    );
};

export const App: React.FC<any> = ({ initialPath, initialClassification, ssrData }) => {
    return (
        <SSRProvider data={ssrData}>
            <HelmetProvider>
                <MobileProvider>
                    <ExternalLinkProvider>
                        <ToastProvider>
                            {import.meta.env.SSR ? (
                                <StaticRouter location={initialPath || "/"}>
                                    <AppContent initialClassification={initialClassification} />
                                </StaticRouter>
                            ) : (
                                <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
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