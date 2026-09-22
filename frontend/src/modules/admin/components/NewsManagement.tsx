import React, { useEffect, useState } from 'react';
import { Plus, Search, Save, Eye, Pencil, ArrowLeft, Upload, ExternalLink, Newspaper } from 'lucide-react';
import { NewsRichEditor } from './NewsRichEditor';
import { NewsBody } from '@/modules/news/components/NewsBody';
import { newsClient, type NewsDraft, type NewsContent } from '@/modules/news/api/newsClient';
import { extractApiErrorMessage } from '@/utils/api';
import { StatusModal } from '@/components/ui/StatusModal';
import './news-management.css';

const newPost = (): NewsDraft => ({ slug: '', version: null, published: null, draft: { title: '', description: '', excerpt: '', author: 'Modtale Team', tags: [], heroImage: '', heroAlt: '', body: '<p></p>' } });
const slugify = (title: string) => title.toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0,120);

export function NewsManagement({ userId }: { userId: string }) {
    const [posts, setPosts] = useState<NewsDraft[]>([]);
    const [post, setPost] = useState<NewsDraft | null>(null);
    const [saved, setSaved] = useState('');
    const [filter, setFilter] = useState('');
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [preview, setPreview] = useState(false);
    const [slugEdited, setSlugEdited] = useState(false);
    const [recovery, setRecovery] = useState<NewsDraft | null>(null);
    const [confirm, setConfirm] = useState<{ title: string; message: string; action: string; run: () => void } | null>(null);
    const dirty = !!post && JSON.stringify(post) !== saved;
    const storageKey = `modtale:news-draft:${userId}`;
    const load = async () => { setLoading(true); setError(''); try { setPosts(await newsClient.drafts()); } catch (e) { setError(extractApiErrorMessage(e, 'Could not load posts.')); } finally { setLoading(false); } };
    useEffect(() => { void load(); try { const stored = localStorage.getItem(storageKey); if (stored) setRecovery(JSON.parse(stored)); } catch { /* Storage may be disabled. */ } }, [storageKey]);
    useEffect(() => {
        if (!post) return;
        try { if (dirty) localStorage.setItem(storageKey, JSON.stringify(post)); else localStorage.removeItem(storageKey); } catch { /* Server saving remains available. */ }
        if (!dirty) return;
        const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [post, dirty, storageKey]);
    const clearRecovery = () => { setRecovery(null); try { localStorage.removeItem(storageKey); } catch {} };
    const select = (next: NewsDraft | null) => {
        const apply = () => { setPost(next); setSaved(JSON.stringify(next)); setPreview(false); setError(''); setNotice(''); setSlugEdited(!!next?.slug); clearRecovery(); };
        if (dirty) setConfirm({ title: 'Discard unsaved changes?', message: 'Your last saved draft will remain available.', action: 'Discard changes', run: apply }); else apply();
    };
    const update = (changes: Partial<NewsContent>) => setPost(current => current ? { ...current, draft: { ...current.draft, ...changes } } : current);
    const save = async () => {
        if (!post) return null;
        if (!post.slug || !post.draft.title.trim()) { setError('Add a title and URL before saving.'); return null; }
        const result = await newsClient.save({ ...post, draft: { ...post.draft, tags: post.draft.tags.map(t => t.trim()).filter(Boolean) } });
        setPost(result); setSaved(JSON.stringify(result));
        setPosts(current => [result, ...current.filter(p => p.slug !== result.slug)]);
        clearRecovery(); return result;
    };
    const run = async (action: 'save' | 'publish' | 'unpublish') => {
        setBusy(true); setError(''); setNotice('');
        try {
            let result = await save();
            if (!result) return;
            if (action === 'publish') result = await newsClient.publish(result);
            if (action === 'unpublish') result = await newsClient.unpublish(result);
            setPost(result); setSaved(JSON.stringify(result)); setPosts(current => [result!, ...current.filter(p => p.slug !== result!.slug)]);
            setNotice(action === 'save' ? 'Draft saved. Your live article has not changed.' : action === 'publish' ? 'Article published.' : 'Article unpublished. Your draft is preserved.');
        } catch (e) { setError(extractApiErrorMessage(e, 'Could not save this post. Your edits are still here.')); }
        finally { setBusy(false); }
    };
    useEffect(() => {
        const shortcut = (event: KeyboardEvent) => {
            if (post && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
                event.preventDefault();
                if (!busy) void run('save');
            }
        };
        window.addEventListener('keydown', shortcut);
        return () => window.removeEventListener('keydown', shortcut);
    }, [post, busy]);
    const requestPublish = () => setConfirm({ title: post?.published ? 'Publish these changes?' : 'Publish this article?', message: 'This version will appear on the news page, homepage, and RSS feed.', action: 'Publish', run: () => void run('publish') });
    return <div className="news-admin">
        {confirm && <StatusModal type="info" title={confirm.title} message={confirm.message} secondaryLabel="Cancel" actionLabel={confirm.action} onClose={() => setConfirm(null)} onAction={() => { const action = confirm.run; setConfirm(null); action(); }} />}
        <header className="news-admin-heading"><div><h1><Newspaper size={25} />News</h1><p>Write, preview, and publish stories from Modtale.</p></div>{!post && <button className="news-primary" onClick={() => select(newPost())}><Plus size={17} />New post</button>}</header>
        {error && <div className="news-admin-error" role="alert">{error}{!post && <button onClick={load}>Try again</button>}</div>}
        {notice && <p className="news-admin-notice" role="status">{notice}</p>}
        {recovery && !post && <div className="news-admin-recovery"><span>Unsaved edits: {recovery.draft?.title || 'Untitled post'}</span><button onClick={() => { setPost(recovery); setSaved(''); setRecovery(null); setSlugEdited(!!recovery.slug); }}>Restore</button><button onClick={clearRecovery}>Dismiss</button></div>}
        {!post ? <>
            <label className="news-admin-search"><Search size={18} /><input aria-label="Search news posts" placeholder="Search posts…" value={filter} onChange={e => setFilter(e.target.value)} /></label>
            {loading ? <p role="status">Loading posts…</p> : <div className="news-post-list">{posts.filter(p => `${p.draft.title} ${p.slug}`.toLowerCase().includes(filter.toLowerCase())).map(p => <button key={p.slug} className="news-post-row" onClick={() => select(p)}>
                {p.draft.heroImage ? <img src={p.draft.heroImage} alt="" /> : <span className="news-post-placeholder"><Newspaper /></span>}
                <span className="news-post-title"><strong>{p.draft.title}</strong><span>{p.draftUpdatedAt ? `Edited ${new Date(p.draftUpdatedAt).toLocaleDateString()}` : 'Draft'} · {p.draft.author}</span></span>
                <span className={`news-post-state ${p.published ? 'is-published' : ''}`}>{p.published ? JSON.stringify(p.draft) === JSON.stringify(p.published) ? 'Published' : 'Unpublished edits' : 'Draft'}</span><Pencil size={17} />
            </button>)}{!posts.length && <div className="news-admin-empty"><Newspaper size={32} /><h2>Your next story starts here</h2><p>Create a draft and see how it will look before publishing.</p></div>}</div>}
        </> : <>
            <div className="news-editor-topbar"><button disabled={busy} onClick={() => select(null)}><ArrowLeft size={16} />All posts</button><span className="news-save-state">{busy ? 'Saving…' : dirty ? 'Unsaved changes' : post.version === null ? 'New draft' : 'Saved draft'}</span><div className="news-editor-actions"><button disabled={busy} onClick={() => setPreview(!preview)}>{preview ? <Pencil size={16} /> : <Eye size={16} />}{preview ? 'Edit' : 'Preview'}</button><button disabled={busy} onClick={() => run('save')}><Save size={16} />Save draft</button><button disabled={busy} className="news-primary" onClick={requestPublish}>{post.published ? 'Publish changes' : 'Publish'}</button></div></div>
            {preview ? <article className="news-admin-preview"><header><h1>{post.draft.title || 'Untitled post'}</h1><p>{post.draft.description}</p><small>{post.draft.author}</small></header>{post.draft.heroImage && <img className="news-preview-cover" src={post.draft.heroImage} alt={post.draft.heroAlt} />}<NewsBody content={post.draft.body} /></article> : <div className="news-editor-layout">
                <div className="news-compose"><label className="sr-only" htmlFor="news-title">Title</label><input id="news-title" className="news-title-input" placeholder="Give your story a title" maxLength={180} disabled={busy} value={post.draft.title} onChange={e => setPost({ ...post, slug: !slugEdited && post.version === null ? slugify(e.target.value) : post.slug, draft: { ...post.draft, title: e.target.value } })} />
                    <label className="sr-only" htmlFor="news-summary">Summary</label><textarea id="news-summary" className="news-summary-input" rows={2} placeholder="A short introduction for readers…" maxLength={500} disabled={busy} value={post.draft.description} onChange={e => update({ description: e.target.value })} />
                    <NewsRichEditor value={post.draft.body} onChange={body => update({ body })} disabled={busy} />
                    <div className="news-editor-footnote">{post.draft.body.replace(/<[^>]+>/g, ' ').trim().split(/\s+/).filter(Boolean).length} words · Formatting shortcuts supported</div>
                </div>
                <aside className="news-post-settings"><h2>Post settings</h2>
                    <label>URL<input value={post.slug} disabled={busy || post.version !== null} placeholder="your-post-title" maxLength={120} onChange={e => { setSlugEdited(true); setPost({ ...post, slug: slugify(e.target.value) }); }} /><small>/news/{post.slug || 'your-post-title'}</small></label>
                    <label>Author<input value={post.draft.author} maxLength={100} disabled={busy} onChange={e => update({ author: e.target.value })} /></label>
                    <label>Tags<input value={post.draft.tags.join(', ')} disabled={busy} onChange={e => update({ tags: e.target.value.split(',').map(t => t.trimStart()) })} placeholder="Updates, Launcher" /></label>
                    <label>Cover image<input value={post.draft.heroImage} disabled={busy} onChange={e => update({ heroImage: e.target.value })} placeholder="https://… or /assets/…" /></label>
                    <label className="news-upload"><Upload size={16} />Upload cover<input type="file" accept="image/png,image/jpeg,image/webp,image/gif" disabled={busy} onChange={async e => { const file = e.target.files?.[0]; if (!file) return; setBusy(true); try { update({ heroImage: await newsClient.upload(file) }); } catch (e) { setError(extractApiErrorMessage(e, 'Could not upload cover.')); } finally { setBusy(false); } }} /></label>
                    {post.draft.heroImage && <img className="news-settings-cover" src={post.draft.heroImage} alt={post.draft.heroAlt} />}
                    <label>Image description<input value={post.draft.heroAlt} maxLength={300} disabled={busy} onChange={e => update({ heroAlt: e.target.value })} placeholder="Describe the cover image" /></label>
                    <label>Feed excerpt<textarea rows={3} value={post.draft.excerpt} maxLength={700} disabled={busy} onChange={e => update({ excerpt: e.target.value })} placeholder="Optional longer summary for RSS" /></label>
                    {post.published && <div className="news-published-actions"><a href={`/news/${post.slug}`} target="_blank" rel="noreferrer"><ExternalLink size={16} />View live article</a><button disabled={busy} onClick={() => setConfirm({ title: 'Unpublish this article?', message: 'It will be removed from public news pages. The saved draft remains available.', action: 'Unpublish', run: () => void run('unpublish') })}>Unpublish</button></div>}
                </aside>
            </div>}
        </>}
    </div>;
}
