import React from 'react';
import {
    Shield,
    Globe,
    Info,
    Server,
    Github,
    ExternalLink,
    Lock,
    Unlock,
    Gauge,
    Key,
    Download,
    User,
    Activity,
    Bell,
    Share2,
    Image,
    Users,
    ArrowRightLeft,
    Zap,
    FileText,
    Database,
    AlertTriangle,
    Layers
} from 'lucide-react';
import { Link } from 'react-router-dom';

const ScrollbarStyles = () => (
    <style>{`
        .response-block::-webkit-scrollbar {
            height: 8px;
            width: 8px;
        }
        .response-block::-webkit-scrollbar-track {
            background: rgba(0, 0, 0, 0.2);
            border-radius: 4px;
        }
        .response-block::-webkit-scrollbar-thumb {
            background: #475569;
            border-radius: 4px;
        }
        .response-block::-webkit-scrollbar-thumb:hover {
            background: #64748b;
        }
        .response-block::-webkit-scrollbar-corner {
            background: transparent;
        }
    `}</style>
);

const Endpoint = ({
                      method,
                      path,
                      desc,
                      params,
                      body,
                      response,
                      auth,
                      note,
                      validation,
                      deprecated
                  }: {
    method: string,
    path: string,
    desc: React.ReactNode,
    params?: Record<string, string>,
    body?: string,
    response?: string,
    auth?: boolean,
    note?: string,
    validation?: string[],
    deprecated?: boolean
}) => (
    <div className={`border-b border-slate-100 dark:border-white/5 pb-10 mb-10 last:border-0 last:pb-0 last:mb-0 ${deprecated ? 'opacity-60' : ''}`}>
        <div className="flex flex-col md:flex-row md:items-center gap-3 font-mono text-sm mb-3">
            <div className="flex items-center gap-2">
                <span className={`px-2 py-1 rounded text-xs font-bold text-white shadow-sm min-w-[60px] text-center ${
                    method === 'GET' ? 'bg-blue-600' :
                        method === 'POST' ? 'bg-green-600' :
                            method === 'PUT' ? 'bg-amber-500' :
                                method === 'DELETE' ? 'bg-red-500' : 'bg-slate-500'
                }`}>
                    {method}
                </span>
                {auth ? (
                    <span className="flex items-center gap-1 text-[10px] uppercase font-bold text-amber-600 bg-amber-50 dark:text-amber-400 dark:bg-amber-500/10 px-1.5 py-0.5 rounded border border-amber-200 dark:border-amber-500/20">
                        <Lock className="w-3 h-3" /> Auth
                    </span>
                ) : (
                    <span className="flex items-center gap-1 text-[10px] uppercase font-bold text-slate-500 bg-slate-100 dark:text-slate-400 dark:bg-white/5 px-1.5 py-0.5 rounded border border-slate-200 dark:border-white/10">
                        <Unlock className="w-3 h-3" /> Public
                    </span>
                )}
            </div>
            <span className="text-slate-700 dark:text-slate-300 font-bold select-all break-all text-base">{path}</span>
        </div>

        <p className="text-sm text-slate-600 dark:text-slate-400 mb-4 leading-relaxed max-w-4xl">{desc}</p>

        {(note || validation) && (
            <div className="space-y-3 mb-6">
                {note && (
                    <div className="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-100 dark:border-blue-800 rounded-lg text-xs text-blue-700 dark:text-blue-300 flex gap-2 items-start">
                        <Info className="w-4 h-4 flex-shrink-0 mt-0.5" />
                        <span className="flex-1 whitespace-pre-line">{note}</span>
                    </div>
                )}
                {validation && (
                    <div className="p-3 bg-amber-50 dark:bg-amber-900/20 border border-amber-100 dark:border-amber-800 rounded-lg text-xs text-amber-800 dark:text-amber-200 flex gap-2 items-start">
                        <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                        <div className="flex-1">
                            <span className="font-bold block mb-1">Validation Rules:</span>
                            <ul className="list-disc list-inside space-y-0.5">
                                {validation.map((v, i) => <li key={i}>{v}</li>)}
                            </ul>
                        </div>
                    </div>
                )}
            </div>
        )}

        <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
            <div className="space-y-6">
                {params && (
                    <div className="bg-slate-50 dark:bg-black/20 p-4 rounded-lg text-xs border border-slate-100 dark:border-white/5 h-fit">
                        <h4 className="font-bold text-slate-500 uppercase mb-3 flex items-center gap-2">
                            <Layers className="w-3 h-3" /> Parameters <span className="text-[10px] font-normal opacity-50 lowercase">(query / path / multipart)</span>
                        </h4>
                        <div className="grid grid-cols-1 gap-3">
                            {Object.entries(params).map(([k, v]) => (
                                <div key={k} className="flex flex-col border-b border-slate-200 dark:border-white/5 last:border-0 pb-2 last:pb-0">
                                    <span className="font-mono font-bold text-slate-700 dark:text-slate-200 mb-0.5">{k}</span>
                                    <span className="text-slate-500 dark:text-slate-400 break-words leading-relaxed">{v}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {body && (
                    <div className="bg-slate-50 dark:bg-black/20 p-4 rounded-lg text-xs border border-slate-100 dark:border-white/5 relative group">
                        <h4 className="font-bold text-slate-500 uppercase mb-2 select-none flex items-center gap-2">
                            <FileText className="w-3 h-3" /> Request Body <span className="text-[10px] font-normal opacity-50 lowercase">(application/json)</span>
                        </h4>
                        <pre className="whitespace-pre overflow-x-auto text-slate-600 dark:text-slate-300 font-mono">{body}</pre>
                    </div>
                )}
            </div>

            {response && (
                <div className="response-block bg-slate-900 p-4 rounded-lg text-xs font-mono text-slate-300 overflow-x-auto border border-slate-800 relative group h-fit max-h-[500px]">
                    <h4 className="font-bold text-slate-500 uppercase mb-2 select-none sticky top-0 left-0">Response Example</h4>
                    <pre className="whitespace-pre">{response}</pre>
                </div>
            )}
        </div>
    </div>
);

export const ApiDocs: React.FC = () => {
    return (
        <div className="max-w-7xl mx-auto px-4 py-16">
            <ScrollbarStyles />

            <div className="text-center mb-16">
                <h1 className="text-4xl md:text-5xl font-black text-slate-900 dark:text-white mb-4 tracking-tight">
                    Modtale <span className="text-modtale-accent">API v1</span>
                </h1>
                <p className="text-lg text-slate-600 dark:text-slate-400 max-w-2xl mx-auto mb-8">
                    Complete programmatic access to the Hytale community repository.
                </p>

                <div className="inline-flex items-center gap-3 px-5 py-3 bg-slate-100 dark:bg-white/5 rounded-full text-sm font-mono text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-white/10 mb-8 shadow-sm">
                    <Server className="w-4 h-4 text-modtale-accent" />
                    <span>Base URL:</span>
                    <span className="font-bold text-slate-900 dark:text-white select-all">https://api.modtale.net</span>
                </div>

                <div className="flex flex-col sm:flex-row justify-center gap-4">
                    <Link to="/dashboard/developer" className="px-6 py-3 bg-slate-900 dark:bg-white text-white dark:text-slate-900 rounded-xl font-bold hover:opacity-90 transition-transform active:scale-95 shadow-lg flex items-center justify-center gap-2">
                        <Shield className="w-4 h-4" /> Get API Key
                    </Link>
                    <a href="https://github.com/Modtale/modtale-example" target="_blank" rel="noreferrer" className="px-6 py-3 bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 text-slate-700 dark:text-white rounded-xl font-bold hover:border-modtale-accent hover:text-modtale-accent transition-all active:scale-95 shadow-sm flex items-center justify-center gap-2 group">
                        <Github className="w-5 h-5 group-hover:text-modtale-accent transition-colors" />
                        <span>View Examples</span>
                        <ExternalLink className="w-3 h-3 opacity-50" />
                    </a>
                </div>
            </div>

            <div className="space-y-20">

                <section>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">
                        <div className="flex items-center gap-3 mb-6">
                            <div className="p-3 bg-green-50 dark:bg-green-500/10 rounded-lg text-green-600 dark:text-green-400">
                                <Key className="w-6 h-6" />
                            </div>
                            <div>
                                <h2 className="text-xl font-black text-slate-900 dark:text-white">Authentication</h2>
                                <p className="text-sm text-slate-500 dark:text-slate-400">Secure your requests.</p>
                            </div>
                        </div>
                        <p className="text-sm text-slate-600 dark:text-slate-400 mb-6 leading-relaxed">
                            All API requests must be directed to <code>https://api.modtale.net</code>.
                            For server-to-server communication or scripts, include your API key in the request header.
                            Headers are also returned in every response to help you track your quota usage.
                        </p>

                        <div className="bg-slate-900 rounded-lg p-4 font-mono text-sm text-slate-300 border border-white/10 overflow-x-auto mb-8">
                            <span className="text-purple-400">X-MODTALE-KEY</span>: <span className="text-white">md_12345abcdef...</span>
                        </div>

                        <h3 className="text-sm font-bold text-slate-900 dark:text-white uppercase mb-4 tracking-wider flex items-center gap-2">
                            <Gauge className="w-4 h-4 text-slate-400" /> Rate Limits
                        </h3>

                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-2">
                            <div className="p-6 rounded-xl bg-slate-50 dark:bg-black/20 border border-slate-100 dark:border-white/5 relative overflow-hidden group hover:border-blue-200 dark:hover:border-blue-500/30 transition-colors">
                                <div className="absolute top-0 right-0 p-4 opacity-5 pointer-events-none text-blue-500"><Shield className="w-24 h-24" /></div>
                                <div className="flex items-center gap-2 mb-3">
                                    <div className="p-2 bg-blue-100 dark:bg-blue-500/20 rounded-lg text-blue-600 dark:text-blue-400">
                                        <Zap className="w-4 h-4" />
                                    </div>
                                    <span className="text-xs font-bold uppercase tracking-wider text-blue-600 dark:text-blue-400">Standard</span>
                                </div>
                                <div className="text-3xl font-black text-slate-900 dark:text-white mb-1">300 <span className="text-sm font-medium text-slate-500">req/min</span></div>
                                <p className="text-xs text-slate-500 dark:text-slate-400 mt-2">Default for personal API keys.</p>
                            </div>

                            <div className="p-6 rounded-xl bg-purple-50 dark:bg-purple-900/10 border border-purple-100 dark:border-purple-500/20 relative overflow-hidden group hover:border-purple-200 dark:hover:border-purple-500/30 transition-colors">
                                <div className="absolute top-0 right-0 p-4 opacity-5 pointer-events-none text-purple-500"><Server className="w-24 h-24" /></div>
                                <div className="flex items-center gap-2 mb-3">
                                    <div className="p-2 bg-purple-100 dark:bg-purple-500/20 rounded-lg text-purple-600 dark:text-purple-400">
                                        <Activity className="w-4 h-4" />
                                    </div>
                                    <span className="text-xs font-bold uppercase tracking-wider text-purple-600 dark:text-purple-400">Enterprise</span>
                                </div>
                                <div className="text-3xl font-black text-purple-900 dark:text-white mb-1">2,000 <span className="text-sm font-medium text-purple-400">req/min</span></div>
                                <p className="text-xs text-purple-700 dark:text-purple-300 mt-2">High volume application integration.</p>
                            </div>

                            <div className="p-6 rounded-xl bg-red-50 dark:bg-red-900/10 border border-red-100 dark:border-red-500/20 relative overflow-hidden group hover:border-red-200 dark:hover:border-red-500/30 transition-colors">
                                <div className="absolute top-0 right-0 p-4 opacity-5 pointer-events-none text-red-500"><AlertTriangle className="w-24 h-24" /></div>
                                <div className="flex items-center gap-2 mb-3">
                                    <div className="p-2 bg-red-100 dark:bg-red-500/20 rounded-lg text-red-600 dark:text-red-400">
                                        <Unlock className="w-4 h-4" />
                                    </div>
                                    <span className="text-xs font-bold uppercase tracking-wider text-red-600 dark:text-red-400">No Auth</span>
                                </div>
                                <div className="text-3xl font-black text-red-900 dark:text-white mb-1">10 <span className="text-sm font-medium text-red-400">req/min</span></div>
                                <p className="text-xs text-red-700 dark:text-red-300 mt-2">Aggressively throttled. Auth recommended.</p>
                            </div>
                        </div>
                    </div>
                </section>

                <section>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-2">
                        <Database className="w-6 h-6 text-slate-400" /> Metadata & Enums
                    </h2>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">
                        <Endpoint
                            method="GET"
                            path="/api/v1/tags"
                            desc="Retrieve the strictly enforced list of allowed project tags. Use these for search filtering or when creating/updating projects."
                            response={`[
  "Adventure", "RPG", "Sci-Fi", "Fantasy", "Survival", "Magic", "Tech",
  "Exploration", "Minigame", "PvP", "Parkour", "Hardcore", "Skyblock",
  "Puzzle", "Quests", "Economy", "Protection", "Admin Tools", "Chat",
  "Anti-Cheat", "Performance", "Library", "API", "Mechanics", "World Gen",
  "Recipes", "Loot Tables", "Functions", "Decoration", "Vanilla+",
  "Kitchen Sink", "City", "Landscape", "Spawn", "Lobby", "Medieval",
  "Modern", "Futuristic", "Models", "Textures", "Animations", "Particles"
]`}
                        />

                        <Endpoint
                            method="GET"
                            path="/api/v1/meta/classifications"
                            desc="Get allowed project types."
                            response={`[ "PLUGIN", "DATA", "ART", "SAVE", "MODPACK" ]`}
                        />

                        <Endpoint
                            method="GET"
                            path="/api/v1/meta/game-versions"
                            desc="Get supported game target versions."
                            response={`[ "Release 1.1", "Release 1.0", "Beta 0.9" ]`}
                        />
                    </div>
                </section>

                <section>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-2">
                        <Globe className="w-6 h-6 text-slate-400" /> Project Discovery
                    </h2>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">

                        <Endpoint
                            method="GET"
                            path="/api/v1/projects"
                            desc="Search projects with advanced filtering. Authentication is required only for specific 'category' filters like Favorites."
                            params={{
                                "search": "string (Keywords)",
                                "page": "int (0-based, default 0)",
                                "size": "int (default 10, max 100)",
                                "sort": "enum (relevance|downloads|updated|newest|rating|favorites)",
                                "classification": "enum (PLUGIN|DATA|ART|SAVE|MODPACK)",
                                "tags": "string (Comma-separated list)",
                                "gameVersion": "string (Exact match)",
                                "category": "string ('Favorites' | 'Your Projects' - Requires Auth)",
                                "author": "string (Username)"
                            }}
                            response={`{
  "content": [
    {
      "id": "550e8400-e29b...",
      "title": "Super Tools",
      "author": "ModDev123",
      "classification": "PLUGIN",
      "description": "Adds powerful tools...",
      "imageUrl": "https://cdn.modtale.net/...",
      "downloads": 15420,
      "rating": 4.8,
      "updatedAt": "2024-03-15",
      "tags": ["Tech", "Survival"]
    }
  ],
  "totalPages": 12,
  "totalElements": 115
}`}
                        />

                        <Endpoint
                            method="GET"
                            path="/api/v1/projects/{id}"
                            desc="Get full project details including versions, gallery, and markdown description."
                            response={`{
  "id": "...",
  "title": "Super Tools",
  "description": "Short summary...",
  "about": "# Markdown Header\\n\\nRich text...",
  "classification": "PLUGIN",
  "status": "PUBLISHED",
  "author": "ModDev123",
  "versions": [
    {
      "id": "v1",
      "versionNumber": "1.0.0",
      "fileUrl": "files/plugin/super.jar",
      "downloadCount": 500
    }
  ],
  "galleryImages": ["https://cdn..."],
  "license": "MIT",
  "repositoryUrl": "https://github.com/..."
}`}
                        />

                        <Endpoint
                            method="GET"
                            path="/api/v1/projects/user/contributed"
                            auth={true}
                            desc="Get a paginated list of all projects the authenticated user owns or has contributed to."
                            response={`{ "content": [ ...projects ], "totalPages": 1 }`}
                        />
                    </div>
                </section>

                <section>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-2">
                        <FileText className="w-6 h-6 text-slate-400" /> Project Management
                    </h2>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">
                        <Endpoint
                            method="POST"
                            path="/api/v1/projects"
                            auth={true}
                            desc="Initialize a new project Draft."
                            validation={[
                                "Title must be unique.",
                                "Description max 250 characters.",
                                "Owner field only required for Organization creation."
                            ]}
                            params={{
                                "title": "string (Required)",
                                "classification": "enum (Required)",
                                "description": "string (Required)",
                                "owner": "string (Optional Org Username)"
                            }}
                            response={`{
  "id": "new-uuid",
  "title": "My Mod",
  "status": "DRAFT",
  "expiresAt": "2024-04-19"
}`}
                        />

                        <Endpoint
                            method="PUT"
                            path="/api/v1/projects/{id}"
                            auth={true}
                            desc="Update project metadata."
                            validation={[
                                "Short Description: max 250 chars.",
                                "Full Description (about): max 50,000 chars.",
                                "Repo URL: Must be valid GitHub/GitLab HTTPS URL."
                            ]}
                            body={`{
  "title": "New Title",
  "description": "Updated summary",
  "about": "# Header\\nUpdated markdown...",
  "tags": ["Tech", "Magic"],
  "license": "Apache-2.0",
  "repositoryUrl": "https://github.com/me/repo"
}`}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/publish"
                            auth={true}
                            desc="Submit a draft for publishing. This makes the project public."
                            validation={[
                                "Must have at least one Version uploaded (except Modpacks).",
                                "Must have a valid License.",
                                "Must have an Icon uploaded.",
                                "Must have Tags selected."
                            ]}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/archive"
                            auth={true}
                            desc="Archive a project. It becomes read-only but remains visible."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/unlist"
                            auth={true}
                            desc="Unlist a project. It is hidden from search but accessible via direct link."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="DELETE"
                            path="/api/v1/projects/{id}"
                            auth={true}
                            desc="Delete a project."
                            note="If the project is a dependency for other active projects, it will be 'Soft Deleted' (metadata scrubbed, files kept). Otherwise, it is permanently purged."
                            response={`200 OK`}
                        />
                    </div>
                </section>

                <section>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-2">
                        <Image className="w-6 h-6 text-slate-400" /> Media & Assets
                    </h2>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">
                        <Endpoint
                            method="PUT"
                            path="/api/v1/projects/{id}/icon"
                            auth={true}
                            desc="Upload project icon."
                            validation={["Aspect Ratio: Exactly 1:1", "Formats: PNG, JPEG, WebP"]}
                            params={{ "file": "MultipartFile (Binary)" }}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="PUT"
                            path="/api/v1/projects/{id}/banner"
                            auth={true}
                            desc="Upload project banner."
                            validation={["Aspect Ratio: Exactly 3:1", "Formats: PNG, JPEG, WebP"]}
                            params={{ "file": "MultipartFile (Binary)" }}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/gallery"
                            auth={true}
                            desc="Add an image to the gallery."
                            params={{ "file": "MultipartFile (Binary)" }}
                            response={`"https://cdn.modtale.net/gallery/image.png"`}
                        />

                        <Endpoint
                            method="DELETE"
                            path="/api/v1/projects/{id}/gallery"
                            auth={true}
                            desc="Remove an image from the gallery."
                            params={{ "imageUrl": "string (Full URL)" }}
                            response={`200 OK`}
                        />
                    </div>
                </section>

                <section>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-2">
                        <Download className="w-6 h-6 text-slate-400" /> Version Management
                    </h2>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">
                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/versions"
                            auth={true}
                            desc="Upload a new version. Handles file uploads for mods/art/data and dependency linking for modpacks."
                            validation={[
                                "Version string must be strictly X.Y.Z (e.g., 1.0.0).",
                                "File is required for standard projects (JAR/ZIP).",
                                "File is ignored for Modpacks.",
                                "Modpacks must have at least 2 dependencies.",
                                "Zip Bombs and malicious extensions (.exe, .sh, etc) are rejected."
                            ]}
                            params={{
                                "versionNumber": "string (Required, X.Y.Z)",
                                "gameVersions": "string[] (Required)",
                                "changelog": "string",
                                "channel": "enum (RELEASE | BETA | ALPHA)",
                                "file": "MultipartFile",
                                "modIds": "string[] (Format: 'UUID:Version' or 'UUID:Version:optional')"
                            }}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="PUT"
                            path="/api/v1/projects/{id}/versions/{versionId}"
                            auth={true}
                            desc="Update version metadata (specifically dependencies for Modpacks)."
                            body={`{
  "modIds": [
    "dependency-uuid-1:1.0.0",
    "dependency-uuid-2:2.1.0:optional"
  ]
}`}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="DELETE"
                            path="/api/v1/projects/{id}/versions/{versionId}"
                            auth={true}
                            desc="Delete a version."
                            note="Prevented if it is the ONLY version of a Published project."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="GET"
                            path="/api/v1/projects/{id}/versions/{version}/download"
                            desc="Download version file."
                            note="For Modpacks, this dynamically generates a .zip containing a manifest.json and all required dependency files."
                            response={`Binary Stream (application/octet-stream)`}
                        />
                    </div>
                </section>

                <section>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-2">
                        <ArrowRightLeft className="w-6 h-6 text-slate-400" /> Collaboration
                    </h2>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">
                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/invite"
                            auth={true}
                            desc="Invite a contributor (Individual Project only, not Org)."
                            params={{ "username": "string" }}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/invite/accept"
                            auth={true}
                            desc="Accept a contribution invite."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/invite/decline"
                            auth={true}
                            desc="Decline a contribution invite."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="DELETE"
                            path="/api/v1/projects/{id}/contributors/{username}"
                            auth={true}
                            desc="Remove a contributor."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/transfer"
                            auth={true}
                            desc="Initiate ownership transfer to another User or Organization."
                            body={`{ "username": "TargetUsername" }`}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/transfer/resolve"
                            auth={true}
                            desc="Accept or decline a transfer request."
                            body={`{ "accept": true }`}
                            response={`200 OK`}
                        />
                    </div>
                </section>

                <section>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-2">
                        <Users className="w-6 h-6 text-slate-400" /> Organizations
                    </h2>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">
                        <Endpoint
                            method="POST"
                            path="/api/v1/orgs"
                            auth={true}
                            desc="Create a new Organization."
                            body={`{ "name": "MyStudio" }`}
                            response={`{
  "id": "org-uuid",
  "username": "MyStudio",
  "accountType": "ORGANIZATION",
  "organizationMembers": [
    { "userId": "your-id", "role": "ADMIN" }
  ]
}`}
                        />

                        <Endpoint
                            method="GET"
                            path="/api/v1/user/orgs"
                            auth={true}
                            desc="List organizations you belong to."
                            response={`[ { "id": "...", "username": "MyStudio", "role": "ADMIN" } ]`}
                        />

                        <Endpoint
                            method="GET"
                            path="/api/v1/orgs/{username}/members"
                            auth={true}
                            desc="Get public members of an organization."
                            response={`[ { "username": "User1", "roles": ["ADMIN"], "avatarUrl": "..." } ]`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/orgs/{id}/members"
                            auth={true}
                            desc="Invite a user to the organization (Admin only)."
                            body={`{ "username": "NewMember", "role": "MEMBER" }`}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="PUT"
                            path="/api/v1/orgs/{id}/members/{userId}"
                            auth={true}
                            desc="Update member role."
                            body={`{ "role": "ADMIN" }`}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="DELETE"
                            path="/api/v1/orgs/{id}/members/{userId}"
                            auth={true}
                            desc="Remove member / Leave organization."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="PUT"
                            path="/api/v1/orgs/{id}"
                            auth={true}
                            desc="Update organization profile."
                            body={`{ "displayName": "My Studio", "bio": "We make mods." }`}
                            response={`{ ...updatedUserObj }`}
                        />

                        <Endpoint
                            method="DELETE"
                            path="/api/v1/orgs/{id}"
                            auth={true}
                            desc="Delete organization."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/orgs/{id}/avatar"
                            auth={true}
                            desc="Upload organization avatar."
                            params={{ "file": "MultipartFile" }}
                            response={`"url"`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/orgs/{id}/banner"
                            auth={true}
                            desc="Upload organization banner."
                            params={{ "file": "MultipartFile" }}
                            response={`"url"`}
                        />
                    </div>
                </section>

                <section>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-2">
                        <User className="w-6 h-6 text-slate-400" /> User Profile & Settings
                    </h2>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">
                        <Endpoint
                            method="GET"
                            path="/api/v1/user/me"
                            auth={true}
                            desc="Get authenticated user details."
                            response={`{
  "id": "u1",
  "username": "Me",
  "email": "me@example.com",
  "roles": ["ROLE_USER"],
  "tier": "FREE",
  "likedModIds": ["m1", "m2"],
  "followingIds": ["u2"],
  "notificationPreferences": { ... }
}`}
                        />

                        <Endpoint
                            method="PUT"
                            path="/api/v1/user/profile"
                            auth={true}
                            desc="Update profile settings."
                            body={`{
  "username": "NewName",
  "bio": "I make things."
}`}
                            response={`{ ...updatedUser }`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/user/profile/avatar"
                            auth={true}
                            desc="Upload user avatar."
                            params={{ "file": "MultipartFile" }}
                            response={`"url"`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/user/profile/banner"
                            auth={true}
                            desc="Upload user banner."
                            params={{ "file": "MultipartFile" }}
                            response={`"url"`}
                        />

                        <Endpoint
                            method="PUT"
                            path="/api/v1/user/settings/notifications"
                            auth={true}
                            desc="Update notification preferences."
                            body={`{
  "projectUpdates": "EMAIL",
  "newFollowers": "ON",
  "dependencyUpdates": "OFF",
  "creatorUploads": "ON"
}`}
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/user/follow/{targetUsername}"
                            auth={true}
                            desc="Follow a user."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/user/unfollow/{targetUsername}"
                            auth={true}
                            desc="Unfollow a user."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="DELETE"
                            path="/api/v1/user/me"
                            auth={true}
                            desc="Permanently delete user account."
                            response={`200 OK`}
                        />
                    </div>
                </section>

                <section>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-2">
                        <Bell className="w-6 h-6 text-slate-400" /> Notifications
                    </h2>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">
                        <Endpoint
                            method="GET"
                            path="/api/v1/notifications"
                            auth={true}
                            desc="Retrieve user notifications."
                            response={`[
  {
    "id": "n1",
    "title": "Project Update",
    "message": "Super Tools updated to 1.1",
    "link": "/mod/super-tools",
    "isRead": false,
    "createdAt": "2024-03-20T10:00:00",
    "type": "INFO"
  }
]`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/notifications/{id}/read"
                            auth={true}
                            desc="Mark notification as read."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/notifications/{id}/unread"
                            auth={true}
                            desc="Mark notification as unread."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/notifications/read-all"
                            auth={true}
                            desc="Mark all notifications as read."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="DELETE"
                            path="/api/v1/notifications/{id}"
                            auth={true}
                            desc="Delete a notification."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="DELETE"
                            path="/api/v1/notifications/clear-all"
                            auth={true}
                            desc="Delete all notifications."
                            response={`200 OK`}
                        />
                    </div>
                </section>

                <section>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-6 flex items-center gap-2">
                        <Activity className="w-6 h-6 text-slate-400" /> Social Actions
                    </h2>
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm">
                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/favorite"
                            auth={true}
                            desc="Toggle favorite status for a project."
                            response={`200 OK`}
                        />

                        <Endpoint
                            method="POST"
                            path="/api/v1/projects/{id}/reviews"
                            auth={true}
                            desc="Post a review."
                            body={`{
  "rating": 5,
  "comment": "Amazing mod!",
  "version": "1.0.0"
}`}
                            response={`200 OK`}
                        />
                    </div>
                </section>
            </div>
        </div>
    );
};