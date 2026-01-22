import React, { useState, useRef, useEffect } from 'react';
import { Menu, X, Upload, Home, LayoutDashboard, User as UserIcon, LogOut, Shield, Users, LogIn } from 'lucide-react';
import { Link } from 'react-router-dom';
import { NotificationMenu } from './user/NotificationMenu';
import { FollowingModal } from './user/FollowingModal';
import { SignInModal } from './user/SignInModal';
import { AnimatedThemeToggler } from './ui/AnimatedThemeToggler';
import type { User } from "@/types.ts";

interface NavbarProps {
    user: User | null;
    onLogout: () => void;
    currentPage: string;
    onNavigate: (page: string) => void;
    isDarkMode: boolean;
    toggleDarkMode: () => void;
    onAuthorClick: (author: string) => void;
}

export const Navbar: React.FC<NavbarProps> = ({
                                                  user, onLogout, currentPage, onNavigate, isDarkMode, toggleDarkMode, onAuthorClick
                                              }) => {
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
    const [isProfileOpen, setIsProfileOpen] = useState(false);
    const [isFollowingOpen, setIsFollowingOpen] = useState(false);
    const [isSignInOpen, setIsSignInOpen] = useState(false);

    const profileRef = useRef<HTMLDivElement>(null);
    const mobileMenuRef = useRef<HTMLDivElement>(null);

    const logoSrc = isDarkMode ? '/assets/logo_light.svg' : '/assets/logo.svg';

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (profileRef.current && !profileRef.current.contains(event.target as Node)) {
                setIsProfileOpen(false);
            }
            if (mobileMenuRef.current && !mobileMenuRef.current.contains(event.target as Node) && !(event.target as Element).closest('#mobile-menu-btn')) {
                setIsMobileMenuOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    useEffect(() => {
        setIsMobileMenuOpen(false);
    }, [currentPage]);

    const NavLink = ({ id, icon: Icon, label }: any) => {
        let isActive = false;
        let toPath = '/';

        if (id === 'home') {
            toPath = '/';
            const homeCategories = ['home', 'plugins', 'modpacks', 'worlds', 'art', 'data'];
            const detailPrefixes = ['mod/', 'world/', 'modpack/'];

            isActive = homeCategories.includes(currentPage) ||
                detailPrefixes.some(prefix => currentPage.startsWith(prefix));
        } else if (id === 'dashboard') {
            toPath = '/dashboard';
            isActive = currentPage.startsWith('dashboard');
        } else if (id === 'upload') {
            toPath = '/upload';
            isActive = currentPage === 'upload';
        } else {
            toPath = `/${id}`;
            isActive = currentPage === id;
        }

        return (
            <Link
                to={toPath}
                className={`flex items-center px-3 py-1.5 rounded-md text-sm transition-all duration-200 ${
                    isActive
                        ? 'text-slate-900 dark:text-white font-extrabold bg-slate-100/50 dark:bg-white/5'
                        : 'text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white font-medium hover:bg-slate-50 dark:hover:bg-white/5'
                }`}
            >
                <Icon className={`w-4 h-4 mr-2 transition-transform duration-200 ${isActive ? 'scale-110 text-modtale-accent' : 'scale-100 opacity-70'}`} />
                {label}
            </Link>
        );
    };

    const homeLikePages = ['home', 'plugins', 'modpacks', 'worlds', 'art', 'data'];
    const isHomeLayout = homeLikePages.includes(currentPage);

    const containerClasses = isHomeLayout
        ? "max-w-7xl min-[1800px]:max-w-[112rem] px-4 sm:px-6 lg:px-8"
        : "max-w-[112rem] px-4 sm:px-12 md:px-16 lg:px-28";

    return (
        <nav className="bg-white/80 dark:bg-[#141d30]/90 text-slate-900 dark:text-slate-300 sticky top-0 z-[100] border-b border-slate-200 dark:border-white/5 transition-colors duration-200 h-24 backdrop-blur-xl">
            <SignInModal isOpen={isSignInOpen} onClose={() => setIsSignInOpen(false)} />

            {isFollowingOpen && user && (
                <FollowingModal username={user.username} onClose={() => setIsFollowingOpen(false)} />
            )}

            <div className={`${containerClasses} mx-auto h-full`}>
                <div className="flex items-center justify-between h-full">

                    <Link to="/" className="flex items-center cursor-pointer group flex-shrink-0">
                        <img
                            src={logoSrc}
                            alt="Modtale"
                            className="h-8 md:h-9 w-auto object-contain transition-transform duration-300 group-hover:scale-105 mt-1"
                        />
                    </Link>

                    <div className="hidden md:flex items-center justify-end flex-1">
                        <div className="flex items-center gap-2">
                            {user && (
                                <div className="flex items-center gap-1 mr-3">
                                    <NavLink id="home" icon={Home} label="Mods" />
                                    <NavLink id="dashboard" icon={LayoutDashboard} label="Dashboard" />
                                    <NavLink id="upload" icon={Upload} label="Create" />
                                </div>
                            )}

                            <div className="h-5 w-px bg-slate-200 dark:bg-white/10 mx-1"></div>

                            <div className="flex items-center gap-2">
                                {user && <NotificationMenu />}

                                <AnimatedThemeToggler
                                    onToggle={toggleDarkMode}
                                    className="p-2 text-slate-500 dark:text-slate-400 hover:text-modtale-accent dark:hover:text-modtale-accent transition-colors"
                                />

                                {user ? (
                                    <div className="relative" ref={profileRef}>
                                        <button
                                            onClick={() => setIsProfileOpen(!isProfileOpen)}
                                            className={`group relative flex items-center justify-center rounded-full transition-all duration-200 ${
                                                isProfileOpen
                                                    ? 'ring-2 ring-modtale-accent ring-offset-2 dark:ring-offset-[#141d30]'
                                                    : 'hover:ring-2 hover:ring-slate-200 dark:hover:ring-white/10 ring-offset-2 dark:ring-offset-[#141d30]'
                                            }`}
                                        >
                                            <img
                                                src={user.avatarUrl}
                                                alt={user.username}
                                                className="h-9 w-9 rounded-full transition-transform group-active:scale-95"
                                            />
                                        </button>

                                        {isProfileOpen && (
                                            <div className="absolute right-0 mt-4 w-64 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl shadow-2xl py-2 z-50 animate-in fade-in slide-in-from-top-2 origin-top-right">
                                                <div className="px-5 py-4 border-b border-slate-100 dark:border-white/5 mb-2 bg-slate-50/50 dark:bg-white/5 mx-2 rounded-xl">
                                                    <p className="text-xs text-slate-500 uppercase font-bold tracking-wider mb-1">Signed in as</p>
                                                    <p className="text-base font-black text-slate-900 dark:text-white truncate">{user.username}</p>
                                                </div>

                                                <div className="px-2 space-y-0.5">
                                                    <button onClick={() => { setIsProfileOpen(false); onAuthorClick(user.username); }} className="w-full text-left px-4 py-2.5 rounded-lg text-sm font-bold text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/5 flex items-center gap-3 transition-colors">
                                                        <UserIcon className="w-4 h-4 text-slate-400" /> Your Profile
                                                    </button>
                                                    <button onClick={() => { setIsProfileOpen(false); setIsFollowingOpen(true); }} className="w-full text-left px-4 py-2.5 rounded-lg text-sm font-bold text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/5 flex items-center gap-3 transition-colors">
                                                        <Users className="w-4 h-4 text-slate-400" /> Following
                                                    </button>
                                                    <Link to="/dashboard" onClick={() => setIsProfileOpen(false)} className="w-full text-left px-4 py-2.5 rounded-lg text-sm font-bold text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/5 flex items-center gap-3 transition-colors">
                                                        <LayoutDashboard className="w-4 h-4 text-slate-400" /> Creator Dashboard
                                                    </Link>
                                                    {(user.roles?.includes('ADMIN')) && (
                                                        <Link to="/admin" onClick={() => setIsProfileOpen(false)} className="w-full text-left px-4 py-2.5 rounded-lg text-sm font-bold text-red-600 hover:bg-red-50 dark:hover:bg-red-900/10 flex items-center gap-3 transition-colors">
                                                            <Shield className="w-4 h-4" /> Admin Panel
                                                        </Link>
                                                    )}
                                                </div>

                                                <div className="border-t border-slate-100 dark:border-white/5 my-2 mx-4"></div>

                                                <div className="px-2">
                                                    <button onClick={() => { setIsProfileOpen(false); onLogout(); }} className="w-full text-left px-4 py-2.5 rounded-lg text-sm font-bold text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10 flex items-center gap-3 transition-colors">
                                                        <LogOut className="w-4 h-4" /> Sign Out
                                                    </button>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                ) : (
                                    <button onClick={() => setIsSignInOpen(true)} className="flex items-center bg-slate-900 dark:bg-modtale-accent text-white dark:text-white hover:opacity-90 font-black py-2 px-5 rounded-lg transition-all text-sm shadow-sm dark:shadow-none ml-2 hover:scale-105 active:scale-95">
                                        <LogIn className="w-4 h-4 mr-2" /> Sign in
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>

                    <div className="-mr-2 flex md:hidden items-center gap-2">
                        {user && <NotificationMenu />}
                        <button id="mobile-menu-btn" onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)} className="inline-flex items-center justify-center p-2 rounded-md text-gray-400 hover:text-white hover:bg-slate-700 focus:outline-none">
                            {isMobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
                        </button>
                    </div>
                </div>
            </div>

            {isMobileMenuOpen && (
                <div ref={mobileMenuRef} className="md:hidden absolute top-24 left-0 right-0 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-white/10 shadow-2xl p-4 flex flex-col gap-2 animate-in slide-in-from-top-2 z-50">
                    <Link to="/" onClick={() => setIsMobileMenuOpen(false)} className="flex items-center p-3 rounded-lg hover:bg-slate-50 dark:hover:bg-white/5 font-bold text-slate-700 dark:text-slate-200 text-left"><Home className="w-4 h-4 mr-3" /> Home</Link>
                    <Link to="/upload" onClick={() => setIsMobileMenuOpen(false)} className="flex items-center p-3 rounded-lg hover:bg-slate-50 dark:hover:bg-white/5 font-bold text-slate-700 dark:text-slate-200 text-left"><Upload className="w-4 h-4 mr-3" /> Create Project</Link>
                    {user && (
                        <Link to="/dashboard" onClick={() => setIsMobileMenuOpen(false)} className="flex items-center p-3 rounded-lg hover:bg-slate-50 dark:hover:bg-white/5 font-bold text-slate-700 dark:text-slate-200 text-left"><LayoutDashboard className="w-4 h-4 mr-3" /> Dashboard</Link>
                    )}

                    {(user?.roles?.includes('ADMIN')) && (
                        <Link to="/admin" onClick={() => setIsMobileMenuOpen(false)} className="flex items-center p-3 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/10 font-bold text-red-600 dark:text-red-400 text-left"><Shield className="w-4 h-4 mr-3" /> Admin Panel</Link>
                    )}

                    <div className="h-px bg-slate-100 dark:bg-white/5 my-2"></div>

                    <div className="flex items-center justify-between p-3">
                        <span className="font-bold text-slate-700 dark:text-slate-200">Theme</span>
                        <AnimatedThemeToggler onToggle={toggleDarkMode} className="p-2 bg-slate-100 dark:bg-white/10 rounded-lg text-slate-600 dark:text-slate-300" />
                    </div>

                    {user ? (
                        <>
                            <button onClick={() => { onAuthorClick(user.username); setIsMobileMenuOpen(false); }} className="p-3 rounded-lg hover:bg-slate-50 dark:hover:bg-white/5 font-bold text-slate-700 dark:text-slate-200 flex items-center gap-3">
                                <img src={user.avatarUrl} className="w-6 h-6 rounded-full" alt="" /> Profile
                            </button>
                            <button onClick={() => { setIsFollowingOpen(true); setIsMobileMenuOpen(false); }} className="p-3 rounded-lg hover:bg-slate-50 dark:hover:bg-white/5 font-bold text-slate-700 dark:text-slate-200 flex items-center gap-3">
                                <Users className="w-5 h-5" /> Following
                            </button>
                            <button onClick={onLogout} className="p-3 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/10 font-bold text-red-500 flex items-center gap-3"><LogOut className="w-5 h-5" /> Sign Out</button>
                        </>
                    ) : (
                        <button onClick={() => { setIsSignInOpen(true); setIsMobileMenuOpen(false); }} className="w-full bg-slate-900 dark:bg-white text-white dark:text-slate-900 py-3 rounded-xl font-bold mt-2 flex items-center justify-center gap-2"><LogIn className="w-4 h-4" /> Sign In</button>
                    )}
                </div>
            )}
        </nav>
    );
};