import React, { useEffect, useMemo, useState } from 'react';
import { Search, SlidersHorizontal, Download, Check } from 'lucide-react';
import { Checkbox } from '@/components/ui/Checkbox';
import { OptimizedImage } from '@/components/ui/OptimizedImage';
import { BACKEND_URL } from '@/utils/api';
import type { Project } from '@/types';
import './launcher-library-preview.css';
import { LauncherConfigPreview, defaultModConfig, type ModConfig } from './LauncherConfigPreview';

const worlds = [
    { name: 'Emberwild', image: '/assets/launcher/worlds/emberwild.png' },
    { name: 'Greenhaven', image: '/assets/launcher/worlds/greenhaven.png' },
];

export function LauncherLibraryPreview({ projects = [], loading = false }: { projects?: Project[]; loading?: boolean }) {
    const mods = useMemo(() => Array.from(new Map(projects
        .filter(project => project.id && project.title && project.classification !== 'MODPACK' && project.classification !== 'SAVE')
        .map(project => [project.id, project])).values()).slice(0, 3), [projects]);
    const [configProject, setConfigProject] = useState<Project | null>(null);
    const [configs, setConfigs] = useState<Record<string, ModConfig>>({});
    const [updates, setUpdates] = useState<Record<string, number>>({});
    const updating = Object.values(updates).some(progress => progress < 100);
    useEffect(() => {
        if (!updating) return;
        const timer = window.setInterval(() => setUpdates(current => Object.fromEntries(
            Object.entries(current).map(([id, progress]) => [id, Math.min(100, progress + 20)])
        )), 220);
        return () => window.clearInterval(timer);
    }, [updating]);
    const [world, setWorld] = useState(0);
    const [search, setSearch] = useState('');
    const [selections, setSelections] = useState<Record<string, boolean>>({});
    const enabled = (project: Project) => selections[`${world}:${project.id}`] ?? (world === 0 ? mods.indexOf(project) < 2 : mods.indexOf(project) > 0);
    const count = mods.filter(enabled).length;

    const visible = mods.filter(project => `${project.title} ${project.author}`.toLowerCase().includes(search.toLowerCase()));
    return (
        <div className="launcher-library-preview" aria-label="Interactive launcher library preview">
            <div className="llp-workspace" inert={configProject !== null}>
                <div className="llp-worlds" role="group" aria-label="Choose a demo world">
                    {worlds.map(({ name, image }, index) => (
                        <button key={name} type="button" aria-pressed={world === index} onClick={() => setWorld(index)} title={name}>
                            <img src={image} alt="" width={40} height={40} loading="lazy" />
                            <span>{name}</span>
                        </button>
                    ))}
                </div>
                <div className="llp-library">
                    <div className="llp-world-heading">
                        <img src={worlds[world].image} alt="" width={48} height={48} loading="lazy" />
                        <div><h3>{worlds[world].name}</h3><p aria-live="polite">{count} of {mods.length} mods enabled</p></div>
                    </div>
                    <label className="llp-search"><Search size={14} aria-hidden="true" /><input aria-label="Search installed mods in the preview" placeholder="Search installed mods…" value={search} onChange={event => setSearch(event.target.value)} /></label>
                    {loading && !mods.length ? <div className="llp-loading" role="status">Loading projects…</div> : !mods.length ? <p className="llp-empty">Projects are unavailable right now.</p> : (
                        <div className="llp-groups">
                            {[true, false].map(active => (
                                <div key={String(active)}>
                                    <h4>{active ? 'Enabled' : 'Disabled'}</h4>
                                    {visible.filter(project => enabled(project) === active).map(project => (
                                        <div key={project.id} className="llp-mod">
                                            <label className="llp-mod-selection">
                                                <Checkbox checked={active} onChange={checked => setSelections(current => ({ ...current, [`${world}:${project.id}`]: checked }))} />
                                                <OptimizedImage src={project.imageUrl?.startsWith('/api') ? `${BACKEND_URL}${project.imageUrl}` : project.imageUrl || '/assets/favicon.svg'} alt="" baseWidth={40} className="llp-mod-icon" />
                                                <span className="llp-mod-copy"><strong>{project.title}</strong><span>by {project.author}</span></span>
                                            </label>
                                            <div className="llp-mod-actions">
                                                <button type="button" className="llp-button llp-icon-button" aria-label={`Configure ${project.title}`} title="Configure mod" onClick={() => setConfigProject(project)}><SlidersHorizontal size={14} /></button>
                                                {mods.indexOf(project) < 2 && <button type="button" className={`llp-button llp-icon-button ${updates[project.id] === 100 ? 'llp-complete' : 'llp-primary'}`} aria-label={updates[project.id] === 100 ? `${project.title} updated` : updates[project.id] !== undefined ? `Updating ${project.title}` : `Update ${project.title}`} title={updates[project.id] === 100 ? 'Updated to demo version 1.1.0' : 'Update to demo version 1.1.0'} disabled={updates[project.id] !== undefined} onClick={() => setUpdates(current => ({ ...current, [project.id]: 0 }))}>
                                                    {updates[project.id] === 100 ? <Check size={14} /> : <Download size={14} />}
                                                </button>}
                                            </div>
                                            {updates[project.id] !== undefined && updates[project.id] < 100 && <div className="llp-row-progress" role="progressbar" aria-label={`Updating ${project.title}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={updates[project.id]}><span style={{ width: `${updates[project.id]}%` }} /></div>}
                                        </div>
                                    ))}
                                    {!visible.some(project => enabled(project) === active) && <p className="llp-empty">{search ? 'No matching mods' : active ? 'No mods enabled' : 'All mods enabled'}</p>}
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
            {configProject && <LauncherConfigPreview key={configProject.id} project={configProject.title} saved={configs[configProject.id] ?? defaultModConfig} onSave={values => setConfigs(current => ({ ...current, [configProject.id]: values }))} onClose={() => setConfigProject(null)} />}
            <p className="llp-demo-note" role="status">{updating ? 'Updating demo mods…' : Object.keys(updates).length ? 'Mods updated. Ready for your next game.' : 'Try the launcher · Changes stay in this preview.'}</p>
        </div>
    );
}
