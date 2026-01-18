import React, { useState, useEffect, useCallback } from 'react';
import { Route, Routes, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { BrowserRouter } from 'react-router-dom';
import { StaticRouter } from 'react-router-dom/server';
import { HelmetProvider } from 'react-helmet-async';
import { api, BACKEND_URL } from './utils/api';

import { Home } from './react-pages/Home';
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
import { Analytics } from '@/components/dashboard/analytics/Analytics';

import { Navbar } from './components/Navbar';
import { Footer } from './components/Footer.tsx';
import { SEOHead } from './components/SEOHead';
import NotFound from './components/ui/error/NotFound.tsx';
import { Spinner } from './components/ui/Spinner';
import { StatusModal } from './components/ui/StatusModal';
import { OnboardingModal } from './components/user/OnboardingModal';

import type { Mod, Modpack, World, User } from './types';
import { createSlug } from './utils/slug';
import type { Classification } from './data/categories';
import { SSRProvider } from './context/SSRContext';
import { ExternalLinkProvider } from './context/ExternalLinkContext';
import { NotificationProvider } from './context/NotificationsContext.tsx';

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
    const [mods, setMods] = useState<Mod[]>([]);
    const [modpacks, setModpacks] = useState<Modpack[]>([]);
    const [worlds, setWorlds] = useState<World[]>([]);
    const [downloadedSessionIds, setDownloadedSessionIds] = useState<Set<string>>(new Set());
    const [globalError, setGlobalError] = useState<string | null>(null);
    const [showOnboarding, setShowOnboarding] = useState(false);

    const [isDarkMode, setIsDarkMode] = useState(() => {
        if (import.meta.env.SSR) return true;
        if (typeof window === 'undefined') return true;
        const stored = localStorage.getItem('modtale-theme');
        if (stored === 'light') return false;
        return true;
    });

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
        if (typeof document !== 'undefined') {
            const root = document.documentElement;
            if (isDarkMode) {
                root.classList.add('dark');
            } else {
                root.classList.remove('dark');
            }
        }
    }, [isDarkMode]);

    const toggleDarkMode = () => {
        setIsDarkMode(prev => {
            const newVal = !prev;
            localStorage.setItem('modtale-theme', newVal ? 'dark' : 'light');
            return newVal;
        });
    };

    const refreshData = useCallback(async () => {
        try {
            const res = await api.get('/projects?size=1000');
            const allProjects = res.data?.content || [];

            setMods(allProjects.filter((p: any) => p.classification !== 'SAVE' && p.classification !== 'MODPACK'));
            setWorlds(allProjects.filter((p: any) => p.classification === 'SAVE'));
            setModpacks(allProjects.filter((p: any) => p.classification === 'MODPACK'));
        } catch (e) { console.error("Background fetch failed", e); }
    }, []);

    const fetchUser = async () => {
        try {
            const res = await api.get(`/user/me?t=${Date.now()}`);
            setUser(res.data);

            if (res.data && (res.data as any).is_new_account) {
                setShowOnboarding(true);
            }
        } catch (e) {
            console.error("Failed to refresh user", e);
        }
    };

    useEffect(() => {
        const init = async () => {
            setLoadingAuth(true);
            try {
                if (!import.meta.env.SSR) await fetchUser();
            } catch (e) { setUser(null); } finally { setLoadingAuth(false); }
            if (!import.meta.env.SSR) refreshData();
        };
        init();
    }, [refreshData]);

    const handleLogout = async () => { try { await api.post(`${BACKEND_URL}/logout`); } catch (e) {} setUser(null); navigate('/'); };
    const handleNavigate = (page: string) => { navigate(page === 'home' ? '/' : `/${page}`); };
    const handleAuthorClick = (author: string) => { navigate(`/creator/${author}`); };
    const handleModClick = (mod: Mod) => { navigate(`/mod/${createSlug(mod.title, mod.id)}`); }
    const handleModpackClick = (modpack: Modpack) => { navigate(`/modpack/${createSlug(modpack.title, modpack.id)}`); }
    const handleWorldClick = (world: World) => { navigate(`/world/${createSlug(world.title, world.id)}`); }

    const handleToggleFavorite = async (id: string) => {
        if (!user) return;
        const isLiked = user.likedModIds?.includes(id) || user.likedModpackIds?.includes(id);

        const newModLikes = isLiked ? (user.likedModIds || []).filter(lid => lid !== id) : [...(user.likedModIds || []), id];
        setUser({ ...user, likedModIds: newModLikes });

        setMods(prev => prev.map(m => m.id === id ? { ...m, favoriteCount: isLiked ? Math.max(0, m.favoriteCount - 1) : m.favoriteCount + 1 } : m));
        setWorlds(prev => prev.map(w => w.id === id ? { ...w, favoriteCount: isLiked ? Math.max(0, w.favoriteCount - 1) : w.favoriteCount + 1 } : w));
        setModpacks(prev => prev.map(m => m.id === id ? { ...m, favoriteCount: isLiked ? Math.max(0, m.favoriteCount - 1) : m.favoriteCount + 1 } : m));

        try { await api.post(`/projects/${id}/favorite`); } catch (e) { refreshData(); }
    };

    const handleDownload = (id: string, isModpack = false) => { if (!downloadedSessionIds.has(id)) setDownloadedSessionIds(prev => new Set(prev).add(id)); };

    if (loadingAuth && !import.meta.env.SSR) return (
        <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark flex items-center justify-center">
            <Spinner fullScreen={false} label="Authenticating..." />
        </div>
    );

    const renderHome = (classification?: Classification) => (
        <Home onModClick={handleModClick} onModpackClick={handleModpackClick} onWorldClick={handleWorldClick}
              onAuthorClick={handleAuthorClick} likedModIds={user?.likedModIds || []}
              likedModpackIds={user?.likedModpackIds || []} onToggleFavoriteMod={handleToggleFavorite}
              onToggleFavoriteModpack={handleToggleFavorite} isLoggedIn={!!user}
              initialClassification={classification} />
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

                <Navbar user={user} onLogout={handleLogout}
                        currentPage={location.pathname.replace('/', '') || 'home'} onNavigate={handleNavigate}
                        isDarkMode={isDarkMode} toggleDarkMode={toggleDarkMode} onAuthorClick={handleAuthorClick} />
                <div className="flex-1">
                    <Routes>
                        <Route path="/" element={renderHome()} />
                        <Route path="/mods" element={<Navigate to="/" replace />} />
                        <Route path="/plugins" element={renderHome('PLUGIN')} />
                        <Route path="/modpacks" element={renderHome('MODPACK')} />
                        <Route path="/worlds" element={renderHome('SAVE')} />
                        <Route path="/art" element={renderHome('ART')} />
                        <Route path="/data" element={renderHome('DATA')} />

                        <Route path="/upload" element={<Upload onNavigate={handleNavigate} onRefresh={refreshData} currentUser={user} />} />
                        <Route path="/dashboard/*" element={<Dashboard user={user} onRefreshUser={fetchUser} />} />
                        <Route path="/analytics/project/:id" element={<Analytics />} />

                        <Route path="/mod/:id" element={<ModDetail onToggleFavorite={handleToggleFavorite} isLiked={(id) => user?.likedModIds?.includes(id) || false} currentUser={user} onRefresh={refreshData} onDownload={(id) => handleDownload(id, false)} downloadedSessionIds={downloadedSessionIds} />} />
                        <Route path="/mod/:id/edit" element={<EditMod currentUser={user} />} />

                        <Route path="/modpack/:id" element={<ModDetail onToggleFavorite={handleToggleFavorite} isLiked={(id) => user?.likedModpackIds?.includes(id) || user?.likedModIds?.includes(id) || false} currentUser={user} onRefresh={refreshData} onDownload={(id) => handleDownload(id, true)} downloadedSessionIds={downloadedSessionIds} />} />
                        <Route path="/modpack/:id/edit" element={<EditMod currentUser={user} />} />

                        <Route path="/world/:id" element={<ModDetail onToggleFavorite={handleToggleFavorite} isLiked={(id) => user?.likedModIds?.includes(id) || false} currentUser={user} onRefresh={refreshData} onDownload={(id) => handleDownload(id, false)} downloadedSessionIds={downloadedSessionIds} />} />

                        <Route path="/creator/:username" element={<CreatorProfile onModClick={handleModClick} onModpackClick={handleModpackClick} onBack={() => handleNavigate('home')} likedModIds={user?.likedModIds || []} likedModpackIds={user?.likedModpackIds || []} onToggleFavorite={handleToggleFavorite} onToggleFavoriteModpack={handleToggleFavorite} currentUser={user} onRefreshUser={fetchUser} />} />

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
                        <Route path="/status" element={<Status />} />
                        <Route path="/settings/developer" element={<Navigate to="/dashboard/developer" replace />} />
                        <Route path="/analytics" element={<Navigate to="/dashboard/analytics" replace />} />
                        <Route path="/admin" element={user ? <AdminPanel currentUser={user} /> : <Navigate to="/" />} />
                        <Route path="*" element={<NotFound />} />
                    </Routes>
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
                <ExternalLinkProvider>
                    {import.meta.env.SSR ? (
                        <StaticRouter location={initialPath || "/"}>
                            <AppContent initialClassification={initialClassification} />
                        </StaticRouter>
                    ) : (
                        <BrowserRouter>
                            <AppContent initialClassification={initialClassification} />
                        </BrowserRouter>
                    )}
                </ExternalLinkProvider>
            </HelmetProvider>
        </SSRProvider>
    );
};
export default App;