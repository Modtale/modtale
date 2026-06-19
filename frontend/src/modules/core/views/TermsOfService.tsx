import React from 'react';
import { ArrowLeft, FileText, AlertTriangle, HeartHandshake } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export const TermsOfService: React.FC = () => {
    const navigate = useNavigate();

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 pb-20">
            <div className="max-w-[112rem] px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 mx-auto py-12">
                <button onClick={() => navigate(-1)} className="mb-6 flex items-center text-slate-500 hover:text-modtale-accent font-bold transition-colors">
                    <ArrowLeft className="w-4 h-4 mr-2" /> Back
                </button>

                <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl p-8 md:p-12 rounded-3xl border border-slate-200 dark:border-white/10 shadow-2xl">
                    <div className="flex items-center gap-3 mb-8 pb-6 border-b border-slate-100 dark:border-white/5">
                        <div className="p-3 bg-blue-100 dark:bg-blue-900/20 rounded-lg text-blue-600 dark:text-blue-400">
                            <FileText className="w-8 h-8" />
                        </div>
                        <div>
                            <h1 className="text-3xl font-black text-slate-900 dark:text-white">Terms of Service</h1>
                            <p className="text-slate-500 dark:text-slate-400 font-medium">Last updated: June 16, 2026</p>
                        </div>
                    </div>

                    <div className="bg-slate-50 dark:bg-black/20 p-6 rounded-xl border border-slate-200 dark:border-white/5 mb-8">
                        <h3 className="text-lg font-bold text-slate-900 dark:text-white mb-2 flex items-center gap-2">
                            <HeartHandshake className="w-5 h-5 text-modtale-accent" /> Plain-English Summary
                        </h3>
                        <ul className="list-disc list-inside space-y-1 text-slate-600 dark:text-slate-300 text-sm">
                            <li><strong>Modtale is independent:</strong> We are not affiliated with Hypixel Studios, Hytale, or their affiliates.</li>
                            <li><strong>You own your projects:</strong> You keep your rights, and you give Modtale LLC the permissions needed to host, scan, display, distribute, and promote them.</li>
                            <li><strong>Keep the community safe:</strong> Do not upload malware, stolen work, abusive content, spam, or anything unlawful.</li>
                            <li><strong>Downloads are your choice:</strong> We scan and review uploads, but user-made mods can still be risky or incompatible.</li>
                            <li><strong>We can enforce these rules:</strong> We may reject, remove, unlist, preserve, or restrict content and accounts when needed.</li>
                        </ul>
                    </div>

                    <div className="prose dark:prose-invert prose-slate max-w-none">

                        <div className="p-4 bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-900/30 rounded-lg not-prose my-6">
                            <h3 className="text-red-600 dark:text-red-400 font-bold flex items-center gap-2 mb-1">
                                <AlertTriangle className="w-5 h-5" /> Unofficial Fan Project
                            </h3>
                            <p className="text-sm text-red-800 dark:text-red-200">
                                Modtale is not affiliated with, endorsed by, sponsored by, or associated with Hypixel Studios or their affiliates. "Hytale" and related names belong to their respective owners.
                            </p>
                        </div>

                        <h3>1. Accepting These Terms</h3>
                        <p>
                            These Terms of Service are the rules for using Modtale, a US, North Carolina-based service operated by Modtale LLC. They apply to the website, API, accounts, organizations, project pages, downloads, comments, reports, notifications, and related services.
                        </p>
                        <p>
                            By using Modtale, creating an account, signing in through an identity provider, uploading content, downloading content, or using an API key, you agree to these terms. If you do not agree, please do not use Modtale. If you are under the age of majority where you live, your parent or guardian must agree to these terms for you. Modtale is a general-audience service and is not intended for children under 13.
                        </p>

                        <h3>2. Accounts and Security</h3>
                        <p>
                            You need an account for features such as uploading projects, commenting, favoriting projects, following creators, managing organizations, receiving notifications, and creating API keys. Modtale supports email/password sign-in and third-party sign-in or account linking through providers such as GitHub, GitLab, Discord, Google, Twitter/X, and Bluesky when those options are enabled.
                        </p>
                        <ul>
                            <li><strong>Keep your account safe:</strong> Use a strong password, protect your third-party accounts, keep API keys private, and consider enabling MFA.</li>
                            <li><strong>You are responsible for activity:</strong> You are responsible for actions taken through your account, organization, project roles, sessions, and API keys unless the activity was caused by our security failure.</li>
                            <li><strong>Tell us about problems:</strong> If you believe your account or API key was compromised, contact us and revoke affected keys as soon as possible.</li>
                            <li><strong>No impersonation:</strong> Do not pretend to be another person, organization, Modtale staff member, Hypixel Studios, or any other company or project.</li>
                        </ul>

                        <h3>3. Your Content and Project Licenses</h3>
                        <p>
                            "Your content" means anything you upload, submit, publish, or make available through Modtale, including mods, plugins, modpacks, project metadata, descriptions, changelogs, source links, images, videos, comments, reports, organization information, and profile information.
                        </p>
                        <ul>
                            <li><strong>You keep ownership:</strong> These terms do not transfer ownership of your content to Modtale.</li>
                            <li><strong>You choose the project license:</strong> If you select or state a license for a project, users may rely on that license for the content you publish under it. Make sure you have the rights to offer that license.</li>
                            <li><strong>You give Modtale hosting permission:</strong> You grant Modtale LLC a worldwide, non-exclusive, royalty-free license to host, store, scan, copy, resize, transcode, cache, display, distribute, make available for download, index, and promote your content as needed to operate, secure, improve, and advertise the service.</li>
                            <li><strong>You let us handle technical copies:</strong> This license includes backups, CDN copies, generated previews, image optimization, search snippets, API responses, security review copies, and copies needed for moderation or legal compliance.</li>
                            <li><strong>You can remove content:</strong> You may delete or unpublish your own projects when the product allows it, subject to dependency, safety, legal, moderation, and backup limits described in these terms and the Privacy Policy.</li>
                        </ul>
                        <p>
                            You promise that you have all rights and permissions needed for the content you upload, including third-party code, libraries, assets, screenshots, trademarks, and game-related materials. Do not upload Hytale game files, leaked materials, proprietary assets, or anyone else's work unless you are allowed to do so.
                        </p>

                        <h3>4. Uploads, Security Scanning, and Review</h3>
                        <p>
                            Modtale may scan uploaded files, generate hashes, inspect metadata, queue releases for review, and block or delay publishing when a file is suspicious, unscanned, unsupported, or awaiting manual review. Scanning helps reduce risk, but it cannot prove that every file is safe, lawful, or compatible.
                        </p>
                        <ul>
                            <li><strong>No bypassing review:</strong> Do not try to evade file limits, rate limits, scans, holds, rejections, or moderation decisions.</li>
                            <li><strong>No harmful files:</strong> Do not upload malware, spyware, credential stealers, destructive code, exploit payloads, zip bombs, hidden miners, or files designed to damage users, games, accounts, devices, or infrastructure.</li>
                            <li><strong>We may preserve evidence:</strong> If content appears unsafe, unlawful, abusive, or rights-infringing, we may preserve copies, logs, reports, and related account information for investigation and enforcement.</li>
                        </ul>

                        <h3>5. Community Rules</h3>
                        <p>
                            Keep Modtale safe, useful, and welcoming. You may not use Modtale to upload, publish, link to, encourage, or coordinate:
                        </p>
                        <ul>
                            <li>illegal content or activity;</li>
                            <li>copyright, trademark, privacy, publicity, or license violations;</li>
                            <li>harassment, threats, hate speech, or content promoting violence or discrimination;</li>
                            <li>sexual content, especially any content involving minors;</li>
                            <li>doxxing, non-consensual personal information, or invasive tracking;</li>
                            <li>spam, scams, phishing, deceptive downloads, fake metadata, manipulated statistics, or search abuse;</li>
                            <li>content that interferes with Modtale, other users, third-party services, or game infrastructure; or</li>
                            <li>anything that otherwise creates legal, security, operational, or community risk for Modtale or its users.</li>
                        </ul>

                        <h3>6. Moderation and Enforcement</h3>
                        <p>
                            We may investigate reports and take action when we believe it is appropriate. Actions may include warning users, rejecting releases, requiring changes, removing comments, unlisting or archiving projects, disabling downloads, deleting files, suspending or deleting accounts, banning email addresses, revoking API keys, limiting features, preserving evidence, or contacting service providers or authorities.
                        </p>
                        <p>
                            We are not required to host or keep hosting any content. We try to be fair, but we may act without advance notice when needed for safety, security, legal compliance, platform integrity, or user protection.
                        </p>

                        <h3>7. Downloads, Dependencies, and Modpacks</h3>
                        <p>
                            User-uploaded content is provided by its creators, not by Modtale. You are responsible for deciding whether to download, install, trust, and run a project. Back up your worlds, servers, and files before installing mods or plugins.
                        </p>
                        <ul>
                            <li><strong>No compatibility promise:</strong> We do not promise that a project works with a particular game version, loader, dependency, modpack, server, client, or operating system.</li>
                            <li><strong>Dependency placeholders:</strong> If deleting a project would break dependency resolution for other projects or modpacks, we may retain a minimal, scrubbed, or placeholder record instead of removing every trace immediately.</li>
                            <li><strong>Third-party links:</strong> Repository links, YouTube embeds, Discord links, project websites, and other external services are controlled by their own operators and terms.</li>
                        </ul>

                        <h3>8. API and Automated Use</h3>
                        <p>
                            Modtale provides API access for community tooling and project workflows. Use API keys only for accounts, organizations, and projects you are authorized to manage. You may not overload the service, bypass rate limits, scrape private or restricted data, share API keys publicly, use the API for spam or abuse, or use automation to manipulate rankings, downloads, favorites, views, comments, reports, or reviews. We may rate-limit, revoke, rotate, or block API access at any time to protect the service.
                        </p>

                        <h3>9. Copyright and Takedowns</h3>
                        <p>
                            If you believe content on Modtale infringes your copyright or other rights, contact <strong>legal@modtale.net</strong> with enough detail for us to identify the content, understand your claim, and reach you. We may remove or restrict content while reviewing a rights complaint. If your content was removed and you believe it was a mistake, you may send a counter-explanation with supporting information.
                        </p>

                        <h3>10. Privacy</h3>
                        <p>
                            Our Privacy Policy explains what information we collect, how we use it, who we share it with, and what choices you have. By using Modtale, you also agree that we may process information as described in the Privacy Policy.
                        </p>

                        <h3>11. Service Changes and Availability</h3>
                        <p>
                            Modtale is provided as a community platform and may change over time. We may add, change, pause, limit, or remove features, integrations, storage behavior, review rules, API endpoints, project categories, and eligibility rules. We do not promise that the service, every feature, every file, or every API endpoint will always be available.
                        </p>

                        <h3>12. Disclaimers</h3>
                        <p>
                            To the maximum extent permitted by law, Modtale is provided <strong>AS IS</strong> and <strong>AS AVAILABLE</strong>, without warranties of any kind. We do not guarantee that Modtale will be uninterrupted, secure, error-free, free of harmful content, or that user-uploaded content will be safe, lawful, accurate, complete, or compatible with your setup.
                        </p>

                        <h3>13. Limitation of Liability</h3>
                        <p>
                            To the maximum extent permitted by law, Modtale LLC, its maintainers, contributors, operators, affiliates, officers, employees, contractors, and agents will not be liable for indirect, incidental, special, consequential, exemplary, or punitive damages, or for lost profits, lost data, lost saves, corrupted worlds, security incidents caused by user-uploaded content, downtime, loss of goodwill, or other damages arising from your use of or inability to use Modtale.
                        </p>
                        <p>
                            Some laws do not allow certain limitations. In those places, our liability is limited only to the extent the law allows.
                        </p>

                        <h3>14. Your Responsibility to Modtale</h3>
                        <p>
                            If your content, account activity, API use, or violation of these terms causes claims, losses, damages, costs, or expenses for Modtale, you agree to be responsible for them to the extent allowed by law. This includes claims that your content infringes someone else's rights, contains harmful code, violates law, or violates a license.
                        </p>

                        <h3>15. Governing Law</h3>
                        <p>
                            These terms are governed by the laws of the State of North Carolina and applicable US federal law, without regard to conflict-of-law rules. This does not limit consumer protection rights that you cannot waive under the laws where you live.
                        </p>

                        <h3>16. Changes to These Terms</h3>
                        <p>
                            We may update these terms as Modtale changes. We will update the "Last updated" date and try to give reasonable notice for material changes. If you continue using Modtale after changes take effect, the updated terms apply to your continued use.
                        </p>

                        <h3>17. Contact</h3>
                        <p>
                            Questions, rights complaints, and legal notices can be sent to <strong>legal@modtale.net</strong>. You can also reach the maintainers through the <a href="https://github.com/Modtale/modtale" className="text-modtale-accent hover:underline" target="_blank" rel="noreferrer">Modtale GitHub repository</a>.
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
};
