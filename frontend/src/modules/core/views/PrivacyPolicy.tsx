import React from 'react';
import { ArrowLeft, Shield, Lock, Eye, Server } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export const PrivacyPolicy: React.FC = () => {
    const navigate = useNavigate();

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 pb-20">
            <div className="max-w-[112rem] px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 mx-auto py-12">
                <button onClick={() => navigate(-1)} className="mb-6 flex items-center text-slate-500 hover:text-modtale-accent font-bold transition-colors">
                    <ArrowLeft className="w-4 h-4 mr-2" /> Back
                </button>

                <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl p-8 md:p-12 rounded-3xl border border-slate-200 dark:border-white/10 shadow-2xl">
                    <div className="flex items-center gap-3 mb-8 pb-6 border-b border-slate-100 dark:border-white/5">
                        <div className="p-3 bg-green-100 dark:bg-green-900/20 rounded-lg text-green-600 dark:text-green-400">
                            <Shield className="w-8 h-8" />
                        </div>
                        <div>
                            <h1 className="text-3xl font-black text-slate-900 dark:text-white">Privacy Policy</h1>
                            <p className="text-slate-500 dark:text-slate-400 font-medium">Last updated: June 16, 2026</p>
                        </div>
                    </div>

                    <div className="bg-slate-50 dark:bg-black/20 p-6 rounded-xl border border-slate-200 dark:border-white/5 mb-8">
                        <h3 className="text-lg font-bold text-slate-900 dark:text-white mb-2 flex items-center gap-2">
                            <Eye className="w-5 h-5 text-modtale-accent" /> Plain-English Summary
                        </h3>
                        <ul className="list-disc list-inside space-y-1 text-slate-600 dark:text-slate-300 text-sm">
                            <li>Modtale is operated by <strong>Modtale LLC</strong>, a North Carolina, US company.</li>
                            <li>We do <strong>not</strong> sell your personal information or use third-party advertising cookies.</li>
                            <li>We collect the information needed to run accounts, profiles, organizations, uploads, downloads, security scanning, moderation, analytics, notifications, and the API.</li>
                            <li>Some things are public by design, including public profiles, project pages, comments, licenses, visible connected accounts, download counts, and project metadata.</li>
                            <li>You can update your profile, unlink connections, revoke API keys, change notification settings, and delete your account from the product where those features are available.</li>
                        </ul>
                    </div>

                    <div className="prose dark:prose-invert prose-slate max-w-none">

                        <h3>1. Who We Are</h3>
                        <p>
                            This Privacy Policy explains how Modtale LLC collects, uses, discloses, stores, and protects information when you use Modtale. Modtale is a US-based community platform for Hytale-related mods, plugins, modpacks, project pages, creator profiles, organizations, comments, downloads, analytics, and API tooling.
                        </p>
                        <p>
                            This policy is meant to be readable. It is also meant to be accurate to how the platform works today.
                        </p>

                        <h3>2. Information We Collect</h3>
                        <p>
                            We try to collect only what we need to provide and protect Modtale. The categories below describe the information we may collect.
                        </p>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 my-6 not-prose">
                            <div className="p-4 rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5">
                                <h4 className="font-bold text-slate-900 dark:text-white mb-2 flex items-center gap-2"><Lock className="w-4 h-4 text-blue-500" /> Account and Security Data</h4>
                                <p className="text-sm text-slate-500 dark:text-slate-400 mb-2">
                                    This includes usernames, email addresses, email verification status, password hashes, MFA status and secrets, verification and reset tokens, sessions, API key names, API key prefixes and hashes, API permissions, and API key last-used timestamps.
                                </p>
                                <p className="text-sm text-slate-500 dark:text-slate-400">
                                    We do not store your raw password. We hash passwords and API keys so we can verify them without keeping the original secret.
                                </p>
                            </div>
                            <div className="p-4 rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5">
                                <h4 className="font-bold text-slate-900 dark:text-white mb-2 flex items-center gap-2"><Server className="w-4 h-4 text-purple-500" /> Usage and Diagnostics</h4>
                                <p className="text-sm text-slate-500 dark:text-slate-400 mb-2">
                                    This includes IP addresses, request details, user agents, rate-limit data, Origin and Referer headers, approximate timing, view and download events, server logs, browser error reports, and diagnostic telemetry.
                                </p>
                                <p className="text-sm text-slate-500 dark:text-slate-400">
                                    We use this data for security, abuse prevention, debugging, rate limiting, aggregated analytics, and reliability.
                                </p>
                            </div>
                        </div>

                        <ul>
                            <li><strong>Profile and organization data:</strong> avatar, banner, bio, account type, badges, follows, followers, organization roles, organization members, pending invites, and public connected-account visibility.</li>
                            <li><strong>OAuth and repository data:</strong> provider name, provider ID, provider username, profile URL, avatar URL, email when provided, and GitHub/GitLab access tokens when needed to list or connect repositories. We do not receive your password from OAuth providers.</li>
                            <li><strong>Project and upload data:</strong> project titles, descriptions, about pages, categories, tags, classifications, licenses, repository links, gallery items, YouTube links, version numbers, game versions, changelogs, dependency data, uploaded files, original filenames, file hashes, download URLs, scan results, review status, and rejection reasons.</li>
                            <li><strong>Community and moderation data:</strong> comments, comment votes, reports, report reasons and descriptions, admin decisions, audit logs, banned email records, notifications, transfer requests, contributor invites, and organization invites.</li>
                            <li><strong>Local browser data:</strong> theme preference, external-link warning preference, install-instruction preference, last browse URL, session cookies, and CSRF security cookies.</li>
                        </ul>

                        <h3>3. How We Use Information</h3>
                        <p>
                            We use information to operate Modtale and keep it safe. That includes:
                        </p>
                        <ul>
                            <li>creating accounts, signing users in, verifying email addresses, supporting MFA, and resetting passwords;</li>
                            <li>showing creator profiles, organization pages, project pages, comments, licenses, galleries, dependencies, and download links;</li>
                            <li>uploading, storing, resizing, caching, scanning, reviewing, publishing, rejecting, deleting, or preserving project files and media;</li>
                            <li>generating download counts, view counts, creator analytics, platform analytics, rankings, trending views, and public statistics;</li>
                            <li>sending account, security, invite, transfer, project, follower, comment, and dependency notifications;</li>
                            <li>supporting GitHub and GitLab repository workflows when you connect those accounts;</li>
                            <li>protecting Modtale from malware, spam, scraping, credential abuse, fake statistics, harmful automation, and other misuse;</li>
                            <li>responding to reports, rights complaints, support requests, security incidents, legal requests, and policy enforcement needs; and</li>
                            <li>debugging errors, measuring performance, keeping the service available, and improving the product.</li>
                        </ul>

                        <h3>4. What Is Public</h3>
                        <p>
                            Modtale is built around public project sharing. Public profiles, usernames, avatars, bios, badges, visible connected accounts, organization memberships, project metadata, project licenses, images, videos, comments, download counts, favorite counts, version details, dependencies, and published files may be visible to other users, search engines, API users, and third-party services.
                        </p>
                        <p>
                            Do not put secrets, private keys, passwords, unreleased files, personal information, or anything confidential into public project fields, comments, filenames, changelogs, repository links, or uploaded files.
                        </p>

                        <h3>5. Cookies and Local Storage</h3>
                        <p>
                            We use session cookies such as <code>SESSION</code> or <code>JSESSIONID</code> to keep you signed in. We use <code>XSRF-TOKEN</code> to help protect against cross-site request forgery. These cookies are for authentication and security, not advertising.
                        </p>
                        <p>
                            We also use browser storage for product preferences such as dark mode, external-link warning preferences, install-instruction preferences, and your last browse location. We do not use third-party advertising cookies or tracking pixels.
                        </p>

                        <h3>6. Third Parties and Service Providers</h3>
                        <p>
                            We share information with service providers only as needed to run, secure, debug, and improve Modtale, or when you choose to use an integration.
                        </p>
                        <ul>
                            <li><strong>Storage, CDN, and database infrastructure:</strong> We use MongoDB-compatible database infrastructure and Cloudflare R2/S3-compatible object storage for account data, project records, files, images, and related service data.</li>
                            <li><strong>Authentication providers:</strong> GitHub, GitLab, Discord, Google, Twitter/X, and Bluesky may be used to verify your identity or link accounts when you choose those options.</li>
                            <li><strong>Repository providers:</strong> GitHub and GitLab may receive requests when you connect accounts or ask Modtale to list repositories.</li>
                            <li><strong>Sentry:</strong> We use Sentry for frontend error tracking, performance tracing, and limited session replay so we can diagnose crashes and broken flows. We do not use Sentry for advertising.</li>
                            <li><strong>Email and webhooks:</strong> We use configured email infrastructure to send verification and password-reset messages, and configured webhooks, including Discord/admin webhooks, for operational notifications and moderation workflows.</li>
                            <li><strong>Security scanning:</strong> Uploaded files may be sent to Modtale's Warden scanner so we can assess malware and security risk.</li>
                            <li><strong>Embeds and external content:</strong> YouTube no-cookie embeds, repository links, Discord links, creator websites, and similar third-party links are controlled by their own providers.</li>
                            <li><strong>HytaleModding wiki integration:</strong> If a project uses wiki features, Modtale may request related wiki content from the configured HytaleModding wiki API.</li>
                        </ul>
                        <p>
                            We may also disclose information if we believe it is necessary to comply with law, respond to lawful requests, protect users, enforce our Terms, investigate abuse, protect rights or safety, or complete a merger, acquisition, financing, reorganization, or similar business transaction involving Modtale LLC.
                        </p>

                        <h3>7. We Do Not Sell Personal Information</h3>
                        <p>
                            We do not sell your personal information. We also do not share personal information for cross-context behavioral advertising. Because we do not sell or share personal information for that kind of advertising, there is no advertising sale or share to opt out of on Modtale.
                        </p>

                        <h3>8. Retention and Deletion</h3>
                        <p>
                            We keep information for as long as needed to provide Modtale, protect the service, comply with law, resolve disputes, enforce our Terms, and maintain useful project and dependency records.
                        </p>
                        <ul>
                            <li><strong>Account deletion:</strong> When you delete your account, Modtale soft-deletes it first. API keys are deleted, and GitHub/GitLab repository tokens are cleared. After about 30 days, the account record and that account's notifications are scheduled for permanent deletion.</li>
                            <li><strong>Public content:</strong> Account deletion does not automatically delete every project, file, comment, report, audit record, or moderation record connected to the account. Delete projects and other removable content separately before deleting your account if you want those removed too.</li>
                            <li><strong>Project deletion:</strong> Deleted projects may be soft-deleted, permanently deleted, or scrubbed into a minimal dependency-resolution placeholder if removing every trace would break dependency data for other projects or modpacks.</li>
                            <li><strong>Safety records:</strong> We may retain reports, admin logs, scan results, security logs, banned-email records, and preserved evidence for longer when needed for safety, security, legal compliance, or abuse prevention.</li>
                            <li><strong>Backups and caches:</strong> Backups, CDN caches, logs, and generated previews may persist for a limited time after deletion before they expire or are overwritten.</li>
                        </ul>

                        <h3>9. Your Choices and Rights</h3>
                        <p>
                            You can make many changes directly in Modtale:
                        </p>
                        <ul>
                            <li><strong>Access and correction:</strong> View your account and profile information, and update profile fields from settings.</li>
                            <li><strong>Connections:</strong> Hide visible connected accounts, unlink providers when at least one sign-in method remains, and disconnect GitHub/GitLab repository access.</li>
                            <li><strong>API keys:</strong> Create, review, and revoke API keys from developer settings.</li>
                            <li><strong>Notifications:</strong> Change notification preferences and delete notifications.</li>
                            <li><strong>Deletion:</strong> Delete your account from the Danger Zone in settings, and delete projects separately where project controls allow it.</li>
                        </ul>
                        <p>
                            You may also contact us to request access, correction, deletion, or a copy of information associated with your account. We may need to verify your identity before acting on a request. If privacy laws where you live give you additional rights, we will honor those rights to the extent they apply to Modtale LLC.
                        </p>

                        <h3>10. Children</h3>
                        <p>
                            Modtale is a general-audience service and is not intended for children under 13. If we learn that we have collected personal information from a child under 13 without appropriate consent, we will take appropriate steps to delete or disable the account and remove personal information as required by law.
                        </p>

                        <h3>11. Security</h3>
                        <p>
                            We use reasonable technical and organizational measures to protect Modtale, including password hashing, API key hashing, session security, CSRF protection, rate limiting, access controls, upload limits, security scanning, audit logs, and transport security where available. No online service can be perfectly secure, so please use strong passwords, protect your OAuth accounts, keep API keys secret, and report suspected security issues promptly.
                        </p>

                        <h3>12. International Processing</h3>
                        <p>
                            Modtale LLC is based in North Carolina, United States. Your information may be processed in the United States and in other locations where our service providers operate. By using Modtale, you understand that your information may be transferred to and processed in those locations.
                        </p>

                        <h3>13. Changes to This Policy</h3>
                        <p>
                            We may update this Privacy Policy as Modtale changes. We will update the "Last updated" date and try to give reasonable notice for material changes. Your continued use of Modtale after an update means the updated policy applies going forward.
                        </p>

                        <h3>14. Contact Us</h3>
                        <p>
                            Privacy questions and requests can be sent to <strong>legal@modtale.net</strong>. You can also contact the maintainers through the <a href="https://github.com/Modtale/modtale" className="text-modtale-accent hover:underline" target="_blank" rel="noreferrer">Modtale GitHub repository</a>.
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
};
