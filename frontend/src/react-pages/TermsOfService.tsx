import React from 'react';
import { ArrowLeft, FileText, AlertTriangle, HeartHandshake } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export const TermsOfService: React.FC = () => {
    const navigate = useNavigate();

    return (
        <div className="max-w-4xl mx-auto px-4 py-12">
            <button onClick={() => navigate(-1)} className="mb-6 flex items-center text-slate-500 hover:text-modtale-accent font-bold transition-colors">
                <ArrowLeft className="w-4 h-4 mr-2" /> Back
            </button>

            <div className="bg-white dark:bg-modtale-card p-8 rounded-xl border border-slate-200 dark:border-white/10 shadow-sm">
                <div className="flex items-center gap-3 mb-8 pb-6 border-b border-slate-100 dark:border-white/5">
                    <div className="p-3 bg-blue-100 dark:bg-blue-900/20 rounded-lg text-blue-600 dark:text-blue-400">
                        <FileText className="w-8 h-8" />
                    </div>
                    <div>
                        <h1 className="text-3xl font-black text-slate-900 dark:text-white">Terms of Service</h1>
                        <p className="text-slate-500 dark:text-slate-400 font-medium">Last updated: January 2, 2026</p>
                    </div>
                </div>

                <div className="bg-slate-50 dark:bg-black/20 p-6 rounded-xl border border-slate-200 dark:border-white/5 mb-8">
                    <h3 className="text-lg font-bold text-slate-900 dark:text-white mb-2 flex items-center gap-2">
                        <HeartHandshake className="w-5 h-5 text-modtale-accent" /> The "Be Nice" Summary
                    </h3>
                    <ul className="list-disc list-inside space-y-1 text-slate-600 dark:text-slate-300 text-sm">
                        <li><strong>We aren't Hypixel:</strong> This is a fan project. Don't blame them for us, or us for them.</li>
                        <li><strong>Be cool:</strong> Don't upload viruses, stolen content, or hate speech.</li>
                        <li><strong>Use at your own risk:</strong> If a mod breaks your save file, we are sorry, but we aren't liable.</li>
                        <li><strong>You own your stuff:</strong> You keep the rights to your mods, you just let us host them.</li>
                    </ul>
                </div>

                <div className="prose dark:prose-invert prose-slate max-w-none">

                    <div className="p-4 bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-900/30 rounded-lg not-prose my-6">
                        <h3 className="text-red-600 dark:text-red-400 font-bold flex items-center gap-2 mb-1">
                            <AlertTriangle className="w-5 h-5" /> Unofficial Fan Project
                        </h3>
                        <p className="text-sm text-red-800 dark:text-red-200">
                            Modtale is NOT affiliated with, endorsed by, or associated with Hypixel Studios or their affiliates. "Hytale" is a trademark of Hypixel Studios.
                        </p>
                    </div>

                    <h3>1. Acceptance of Terms</h3>
                    <p>
                        By using Modtale (logging in via Email, GitHub, GitLab, Discord, Google, downloading content, or uploading content), you agree to these rules. If you don't agree, please don't use the site.
                    </p>

                    <h3>2. User Accounts</h3>
                    <p>
                        To upload content or post comments, you need to create an account. You can do this via standard email/password registration or by linking a third-party account (GitHub, GitLab, Discord, Google).
                    </p>
                    <ul>
                        <li><strong>Security:</strong> If you use an email/password, you are responsible for keeping your password complex and secure. If you use a social login, you are responsible for securing that third-party account.</li>
                        <li><strong>Responsibility:</strong> You are responsible for all activity that occurs under your account.</li>
                        <li><strong>Termination:</strong> We reserve the right to ban or suspend accounts that violate these terms (e.g., spamming, uploading malware).</li>
                    </ul>

                    <h3>3. Uploading Content (Your Rights)</h3>
                    <p>
                        When you upload a mod, plugin, art asset, or modpack:
                    </p>
                    <ul>
                        <li><strong>You keep ownership:</strong> You are still the owner of your creation.</li>
                        <li><strong>You give us permission:</strong> You grant Modtale a license to host, display, and distribute your content to other users.</li>
                        <li><strong>You promise it's yours:</strong> You represent that you actually created the content or have explicit permission/license to upload it. Do not upload other people's work without consent.</li>
                    </ul>

                    <h3>4. Prohibited Content</h3>
                    <p>
                        We want to keep this community safe and high-quality. The following is strictly prohibited:
                    </p>
                    <ul>
                        <li><strong>Malware:</strong> Viruses, spyware, zip bombs, or code designed to harm users or infrastructure.</li>
                        <li><strong>Illegal Content:</strong> Anything that violates applicable laws.</li>
                        <li><strong>Hate Speech:</strong> Content that promotes violence, discrimination, or harassment.</li>
                        <li><strong>NSFW Content:</strong> Modtale is for a general gaming audience; keep it appropriate.</li>
                    </ul>
                    <p>We reserve the right to remove ANY content for ANY reason, without warning.</p>

                    <h3>5. Copyright & DMCA</h3>
                    <p>
                        If you believe a mod hosted here infringes on your copyright, please let us know immediately at <strong>legal@modtale.net</strong> or via our GitHub Issues. We comply with the DMCA and will remove infringing content.
                    </p>

                    <h3>6. Disclaimer of Warranties (The "No Warranty" Clause)</h3>
                    <p>
                        THE SERVICE IS PROVIDED "AS IS". We try our best to keep Modtale running and safe, but we cannot guarantee that every mod is bug-free, compatible with your game version, or that the site will never go down.
                    </p>
                    <p>
                        <strong>Risk of Downloading:</strong> You download and use mods at your own risk. Modtale is not responsible for corrupted save files, game crashes, or other issues caused by user-uploaded content. Always back up your worlds!
                    </p>

                    <h3>7. Liability</h3>
                    <p>
                        To the maximum extent permitted by law, Modtale and its maintainers shall not be liable for any damages (direct, indirect, incidental) arising from your use of the service.
                    </p>

                    <h3>8. Changes</h3>
                    <p>
                        We may update these terms from time to time. We will try to notify you of major changes, but continuing to use the site means you accept the new terms.
                    </p>
                </div>
            </div>
        </div>
    );
};