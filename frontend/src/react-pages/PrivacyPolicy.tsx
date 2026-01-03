import React from 'react';
import { ArrowLeft, Shield, Lock, Eye, Server, Key } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export const PrivacyPolicy: React.FC = () => {
    const navigate = useNavigate();

    return (
        <div className="max-w-4xl mx-auto px-4 py-12">
            <button onClick={() => navigate(-1)} className="mb-6 flex items-center text-slate-500 hover:text-modtale-accent font-bold transition-colors">
                <ArrowLeft className="w-4 h-4 mr-2" /> Back
            </button>

            <div className="bg-white dark:bg-modtale-card p-8 rounded-xl border border-slate-200 dark:border-white/10 shadow-sm">
                <div className="flex items-center gap-3 mb-8 pb-6 border-b border-slate-100 dark:border-white/5">
                    <div className="p-3 bg-green-100 dark:bg-green-900/20 rounded-lg text-green-600 dark:text-green-400">
                        <Shield className="w-8 h-8" />
                    </div>
                    <div>
                        <h1 className="text-3xl font-black text-slate-900 dark:text-white">Privacy Policy</h1>
                        <p className="text-slate-500 dark:text-slate-400 font-medium">Last updated: January 2, 2026</p>
                    </div>
                </div>

                <div className="bg-slate-50 dark:bg-black/20 p-6 rounded-xl border border-slate-200 dark:border-white/5 mb-8">
                    <h3 className="text-lg font-bold text-slate-900 dark:text-white mb-2 flex items-center gap-2">
                        <Eye className="w-5 h-5 text-modtale-accent" /> The "Human Readable" Summary
                    </h3>
                    <ul className="list-disc list-inside space-y-1 text-slate-600 dark:text-slate-300 text-sm">
                        <li>We <strong>do not</strong> sell your data. Ever.</li>
                        <li>We support multiple login methods (Email, GitHub, Google, etc.) so you can choose how to secure your account.</li>
                        <li>We only collect data necessary to run the site (like your username for your profile).</li>
                        <li>We use cookies strictly for keeping you logged in and security.</li>
                    </ul>
                </div>

                <div className="prose dark:prose-invert prose-slate max-w-none">

                    <h3>1. Information We Collect</h3>
                    <p>We try to collect as little as possible. Here is exactly what we store:</p>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 my-6 not-prose">
                        <div className="p-4 rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5">
                            <h4 className="font-bold text-slate-900 dark:text-white mb-2 flex items-center gap-2"><Lock className="w-4 h-4 text-blue-500" /> Account Data</h4>
                            <p className="text-sm text-slate-500 dark:text-slate-400 mb-2">
                                <strong>Social Login:</strong> When you sign in via GitHub, GitLab, Discord, or Google, we receive your public profile info (Username, Avatar, Email). We do <em>not</em> receive your password from these services.
                            </p>
                            <p className="text-sm text-slate-500 dark:text-slate-400">
                                <strong>Email Login:</strong> If you register with an email and password, we store your email and a securely salted and hashed version of your password. We cannot see your actual password.
                            </p>
                        </div>
                        <div className="p-4 rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5">
                            <h4 className="font-bold text-slate-900 dark:text-white mb-2 flex items-center gap-2"><Server className="w-4 h-4 text-purple-500" /> Usage Logs</h4>
                            <p className="text-sm text-slate-500 dark:text-slate-400">
                                Like almost every website, our servers automatically record your IP address and browser type when you request a page. This helps us prevent abuse, rate-limit malicious bots, and fix bugs.
                            </p>
                        </div>
                    </div>

                    <h3>2. How We Use Your Information</h3>
                    <p>We use your data solely to provide the Modtale service:</p>
                    <ul>
                        <li>To create your user profile so people know who uploaded a mod.</li>
                        <li>To allow you to link multiple accounts (e.g., linking GitHub to an Email account) for easier login.</li>
                        <li>To track download counts and view statistics (these are aggregated).</li>
                        <li>To allow you to post reviews and comments.</li>
                        <li>To send important account notifications (like security alerts or transfer requests).</li>
                    </ul>

                    <h3>3. Third-Party Services</h3>
                    <p>We rely on a few trusted external providers to keep the lights on. We don't share your data with them for marketing, only for infrastructure.</p>
                    <ul>
                        <li><strong>Google Cloud Platform (GCP) & AWS:</strong> Hosting our database, backend API, and file storage.</li>
                        <li><strong>Authentication Providers:</strong> GitHub, GitLab, Discord, and Google are used to verify your identity if you choose to sign in with them.</li>
                        <li><strong>Sentry:</strong> Used for error tracking. If the site crashes, Sentry gets a report so we can fix it. This may include basic device info but attempts to strip sensitive data.</li>
                    </ul>

                    <h3>4. Cookies</h3>
                    <p>
                        We use a session cookie (`SESSION`) to remember that you are logged in. We also use a security cookie (`XSRF-TOKEN`) to prevent cross-site request forgery attacks. We do not use third-party tracking pixels or advertising cookies.
                    </p>

                    <h3>5. Your Rights</h3>
                    <p>
                        You own your data. You have the right to:
                    </p>
                    <ul>
                        <li><strong>Access:</strong> Ask us what data we have on you.</li>
                        <li><strong>Correction:</strong> Fix any wrong information via your Settings page.</li>
                        <li><strong>Unlinking:</strong> You can unlink connected social accounts (like GitHub or Discord) in your dashboard, provided you have at least one valid login method remaining.</li>
                        <li><strong>Deletion:</strong> You can permanently delete your account via the "Danger Zone" in your settings.</li>
                    </ul>
                    <p>
                        <strong>Note on Mod Deletion:</strong> If you delete your account, your public mods may be kept (but anonymized) if deleting them would break modpacks that depend on them. You can manually delete your mods before deleting your account if you wish to remove everything.
                    </p>

                    <h3>6. Contact Us</h3>
                    <p>
                        If you have concerns about your privacy, please open an issue on our <a href="https://github.com/Modtale/modtale" className="text-modtale-accent hover:underline">GitHub repository</a> or contact the maintainers directly.
                    </p>
                </div>
            </div>
        </div>
    );
};