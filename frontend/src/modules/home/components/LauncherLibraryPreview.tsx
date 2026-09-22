import React, { useMemo, useState } from 'react';
import { Search, Library, SlidersHorizontal, Download } from 'lucide-react';
import { Checkbox } from '@/components/ui/Checkbox';
import { OptimizedImage } from '@/components/ui/OptimizedImage';
import { BACKEND_URL } from '@/utils/api';
import type { Project } from '@/types';
import './launcher-library-preview.css';
import { LauncherConfigPreview } from './LauncherConfigPreview';
import { LauncherUpdatesPreview } from './LauncherUpdatesPreview';

const worlds = [
    { name: 'Emberwild', image: '/assets/launcher/worlds/emberwild.png' },
    { name: 'Greenhaven', image: '/assets/launcher/worlds/greenhaven.png' },
];

export function LauncherLibraryPreview({ projects = [], loading = false }: { projects?: Project[]; loading?: boolean }) {
    const mods = useMemo(() => Array.from(new Map(projects
        .filter(project => project.id && project.title && project.classification !== 'MODPACK' && project.classification !== 'SAVE')
        .map(project => [project.id, project])).values()).slice(0, 3), [projects]);
    const [view, setView] = useState('library');
    const [world, setWorld] = useState(0);
    const [search, setSearch] = useState('');
    const [selections, setSelections] = useState<Record<string, boolean>>({});
    const enabled = (project: Project) => selections[`${world}:${project.id}`] ?? (world === 0 ? mods.indexOf(project) < 2 : mods.indexOf(project) > 0);
    const count = mods.filter(enabled).length;

    const visible = mods.filter(project => `${project.title} ${project.author}`.toLowerCase().includes(search.toLowerCase()));
    return (
        <div className="launcher-library-preview" aria-label="Interactive launcher library preview">
            <div className="llp-toolbar" role="group" aria-label="Launcher preview views">
                {[{ id: 'library', label: 'Library', icon: Library }, { id: 'config', label: 'Config editor', icon: SlidersHorizontal }, { id: 'updates', label: 'Updates', icon: Download }].map(({ id, label, icon: Icon }) => <button type="button" key={id} aria-pressed={view === id} onClick={() => setView(id)}><Icon size={14} />{label}</button>)}
                <span className="llp-demo-label">Interactive demo</span>
            </div>
            <div className="llp-workspace" hidden={view !== 'library'}>
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
                                        <label key={project.id} className="llp-mod">
                                            <Checkbox checked={active} onChange={checked => setSelections(current => ({ ...current, [`${world}:${project.id}`]: checked }))} />
                                            <OptimizedImage src={project.imageUrl?.startsWith('/api') ? `${BACKEND_URL}${project.imageUrl}` : project.imageUrl || '/assets/favicon.svg'} alt="" baseWidth={40} className="llp-mod-icon" />
                                            <span className="llp-mod-copy"><strong>{project.title}</strong><span>by {project.author}</span></span>
                                        </label>
                                    ))}
                                    {!visible.some(project => enabled(project) === active) && <p className="llp-empty">{search ? 'No matching mods' : active ? 'No mods enabled' : 'All mods enabled'}</p>}
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
            <div hidden={view !== 'config'}>
                <div className="llp-world-picker" role="group" aria-label="Choose a settings demo world">{worlds.map(({ name }, index) => <button type="button" key={name} aria-pressed={world === index} onClick={() => setWorld(index)}>{name}</button>)}</div>
                {worlds.map(({ name }, index) => <div key={name} hidden={world !== index}><LauncherConfigPreview world={name} /></div>)}
            </div>
            <div hidden={view !== 'updates'}><LauncherUpdatesPreview mods={mods} loading={loading} /></div>
            <p className="llp-demo-note">Try the launcher · Changes stay in this preview.</p>
        </div>
    );
}
