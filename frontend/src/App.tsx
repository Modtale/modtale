import React, { useState, useEffect } from 'react';
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
import { ErrorBoundary } from './components/ui/error/ErrorBoundary';

import type { Mod, Modpack, World, User } from './types';
import { createSlug } from './utils/slug';
import type { Classification } from './data/categories';
import { SSRProvider } from './context/SSRContext';
import { ExternalLinkProvider } from './context/ExternalLinkContext';
import { NotificationProvider } from './context/NotificationsContext.tsx';
import { ToastProvider } from './components/ui/Toast';
import { MobileProvider } from './context/MobileContext';

import { Home } from './react-pages/Home';
import { Browse } from './react-pages/Browse';
import { Upload } from './react-pages/resources/Upload';
import { CreatorProfile } from './react-pages/user/CreatorProfile.tsx';
import { ModDetail } from './react-pages/resources/ModDetail';
import { EditMod } from './react-pages/resources/EditMod';
import { TermsOfService } from './react-pages/TermsOfService';
import { PrivacyPolicy } from './react-pages/PrivacyPolicy';
import { Dashboard } from './react-pages/user/Dashboard.tsx';
import { AdminPanel } from './react-pages/AdminPanel.tsx';
import { ApiDocs } from './react-pages/ApiDocs.tsx';
import { Status } from './react-pages/Status';
import { VerifyEmail } from './react-pages/auth/VerifyEmail.tsx';
import { ResetPassword } from './react-pages/auth/ResetPassword.tsx';
import { MfaVerify } from './react-pages/auth/MfaVerify';
import { Analytics } from './components/dashboard/Analytics.tsx';
import NotFound from './components/ui/error/NotFound.tsx';

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
        setMounted(true);
        fetchUser();
    }, []);

    const handleLogout = async () => {
        try { await api.post(`${BACKEND_URL}/logout`); } catch (e) {}
        setUser(null);
        navigate('/');
    };

    const handleNavigate = (page: string) => { navigate(page === 'home' ? '/' : `/${page}`); };
    const handleAuthorClick = (authorUsername: string) => { navigate(`/creator/${authorUsername}`); };
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

    const renderBrowse = (classification?: Classification) => (
        <Browse
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

    const renderModDetail = () => (
        <ModDetail
            onToggleFavorite={handleToggleFavorite}
            isLiked={(id) => user?.likedModIds?.includes(id) || false}
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
                    <ErrorBoundary>
                        <Routes>
                            <Route path="/" element={<Home user={user} />} />

                            <Route path="/mods" element={renderBrowse()} />
                            <Route path="/projects" element={<Navigate to="/mods" replace />} />
                            <Route path="/plugins" element={renderBrowse('PLUGIN')} />
                            <Route path="/modpacks" element={renderBrowse('MODPACK')} />
                            <Route path="/worlds" element={renderBrowse('SAVE')} />
                            <Route path="/art" element={renderBrowse('ART')} />
                            <Route path="/data" element={renderBrowse('DATA')} />

                            <Route path="/upload" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    <Upload onNavigate={handleNavigate} onRefresh={async () => {}} currentUser={user} />
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

                            <Route path="/mod/:id" element={renderModDetail()} />
                            <Route path="/mod/:id/download" element={renderModDetail()} />
                            <Route path="/mod/:id/changelog" element={renderModDetail()} />
                            <Route path="/mod/:id/gallery" element={renderModDetail()} />
                            <Route path="/mod/:id/wiki/*" element={renderModDetail()} />
                            <Route path="/mod/:id/edit" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    user ? <EditMod currentUser={user} /> :
                                        <Navigate to="/" />
                            } />

                            <Route path="/modpack/:id" element={renderModDetail()} />
                            <Route path="/modpack/:id/download" element={renderModDetail()} />
                            <Route path="/modpack/:id/changelog" element={renderModDetail()} />
                            <Route path="/modpack/:id/gallery" element={renderModDetail()} />
                            <Route path="/modpack/:id/wiki/*" element={renderModDetail()} />
                            <Route path="/modpack/:id/edit" element={
                                loadingAuth ? <div className="p-20 flex justify-center"><Spinner /></div> :
                                    user ? <EditMod currentUser={user} /> :
                                        <Navigate to="/" />
                            } />

                            <Route path="/world/:id" element={renderModDetail()} />
                            <Route path="/world/:id/download" element={renderModDetail()} />
                            <Route path="/world/:id/changelog" element={renderModDetail()} />
                            <Route path="/world/:id/gallery" element={renderModDetail()} />
                            <Route path="/world/:id/wiki/*" element={renderModDetail()} />

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