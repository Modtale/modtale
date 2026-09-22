import React, { useEffect, useState, useRef } from 'react';
import { EditorContent, useEditor, useEditorState } from '@tiptap/react';
import { Extension, Node, mergeAttributes } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import { TableKit } from '@tiptap/extension-table';
import TextAlign from '@tiptap/extension-text-align';
import Highlight from '@tiptap/extension-highlight';
import { TextStyleKit } from '@tiptap/extension-text-style';
import Subscript from '@tiptap/extension-subscript';
import Superscript from '@tiptap/extension-superscript';
import { Bold, Italic, Underline, Strikethrough, List, ListOrdered, Quote, Code, Link, Unlink, ImagePlus, Video, Table, Undo2, Redo2, AlignLeft, AlignCenter, AlignRight, Highlighter, RemoveFormatting, Minus, Upload } from 'lucide-react';
import clips from '@/data/newsMediaVersions.json';
import { newsClient } from '@/modules/news/api/newsClient';
import { extractApiErrorMessage } from '@/utils/api';

const Demo = Node.create({
    name: 'demo', group: 'block', atom: true, draggable: true,
    addAttributes: () => ({ clip: { default: 'browse-projects', parseHTML: el => el.getAttribute('data-demo-clip') }, alt: { default: '', parseHTML: el => el.getAttribute('data-demo-alt') } }),
    parseHTML: () => [{ tag: 'div[data-demo-clip]' }],
    renderHTML: ({ node }) => ['div', { 'data-demo-clip': node.attrs.clip, 'data-demo-alt': node.attrs.alt }],
    addNodeView: () => ({ node }) => {
        const dom = document.createElement('div'); dom.className = 'news-editor-demo';
        const version = clips[node.attrs.clip as keyof typeof clips];
        const image = document.createElement('img'); image.src = `/assets/news/${encodeURIComponent(node.attrs.clip)}.jpg${version ? `?v=${version}` : ''}`; image.alt = node.attrs.alt;
        const caption = document.createElement('span'); caption.textContent = `Demo · ${node.attrs.clip.replaceAll('-', ' ')}`;
        dom.append(image, caption); return { dom };
    },
});
const VideoNode = Node.create({
    name: 'video', group: 'block', atom: true, draggable: true,
    addAttributes: () => ({ src: { default: '' }, poster: { default: null }, title: { default: '' } }),
    parseHTML: () => [{ tag: 'video' }],
    renderHTML: ({ HTMLAttributes }) => ['video', mergeAttributes(HTMLAttributes, { controls: '', playsinline: '', preload: 'metadata' })],
});
const HeadingIds = Extension.create({ name: 'headingIds', addGlobalAttributes: () => [{ types: ['heading'], attributes: { id: { default: null } } }] });

export function NewsRichEditor({ value, onChange, disabled = false }: { value: string; onChange: (html: string) => void; disabled?: boolean }) {
    const initialized = useRef(false);
    const [insert, setInsert] = useState<'link' | 'image' | 'video' | 'demo' | null>(null);
    const [url, setUrl] = useState(''); const [alt, setAlt] = useState('');
    const [clip, setClip] = useState('browse-projects');
    const [error, setError] = useState(''); const [uploading, setUploading] = useState(false);
    const editor = useEditor({
        extensions: [StarterKit.configure({ trailingNode: false, link: { openOnClick: false } }), Image, TableKit, TextAlign.configure({ types: ['heading', 'paragraph'] }), Highlight.configure({ multicolor: true }), TextStyleKit, Subscript, Superscript, Demo, VideoNode, HeadingIds],
        content: value, immediatelyRender: false, editable: !disabled,
        editorProps: { attributes: { class: 'news-body news-writing-surface', 'aria-label': 'Article body', role: 'textbox', 'aria-multiline': 'true' } },
        onCreate: () => { initialized.current = true; },
        onUpdate: ({ editor }) => { if (initialized.current) onChange(editor.getHTML()); },
    });
    useEditorState({ editor, selector: ctx => ctx.editor?.state });
    useEffect(() => { if (editor && editor.getHTML() !== value) editor.commands.setContent(value, { emitUpdate: false }); }, [editor, value]);
    useEffect(() => { editor?.setEditable(!disabled); }, [editor, disabled]);
    if (!editor) return <p role="status">Loading editor…</p>;
    const button = (label: string, Icon: React.ElementType, action: () => void, active = false, unavailable = false) => <button key={label} type="button" title={label} aria-label={label} aria-pressed={active} disabled={disabled || unavailable} onClick={action}><Icon size={17} /></button>;
    const showInsert = (kind: typeof insert) => {
        const attrs = editor.getAttributes(kind || 'link');
        setInsert(kind); setUrl(attrs.href || attrs.src || ''); setAlt(attrs.alt || attrs.title || '');
        if (attrs.clip) setClip(attrs.clip);
        setError('');
    };
    const insertMedia = () => {
        if (insert !== 'demo' && !/^(https?:\/\/|\/(?!\/)|#|mailto:)/i.test(url.trim())) { setError('Enter a web URL, site path, or anchor link.'); return; }
        if ((insert === 'image' || insert === 'video') && !/^(https?:\/\/|\/(?!\/))/i.test(url.trim())) { setError('Media needs a web URL or site path.'); return; }
        if (insert === 'link') {
            if (editor.state.selection.empty && !editor.isActive('link')) editor.chain().focus().insertContent({ type: 'text', text: url.trim(), marks: [{ type: 'link', attrs: { href: url.trim() } }] }).run();
            else editor.chain().focus().extendMarkRange('link').setLink({ href: url.trim() }).run();
        }
        if (insert === 'image') editor.chain().focus().setImage({ src: url.trim(), alt }).run();
        if (insert === 'video' && editor.isActive('video')) editor.chain().focus().updateAttributes('video', { src: url.trim(), title: alt }).run();
        else if (insert === 'video') editor.chain().focus().insertContent({ type: 'video', attrs: { src: url.trim(), title: alt } }).run();
        if (insert === 'demo' && editor.isActive('demo')) editor.chain().focus().updateAttributes('demo', { clip, alt }).run();
        else if (insert === 'demo') editor.chain().focus().insertContent({ type: 'demo', attrs: { clip, alt } }).run();
        setInsert(null);
    };
    return <div className="news-rich-editor">
        <div className="news-format-bar" role="toolbar" aria-label="Article formatting">
            <select aria-label="Text style" disabled={disabled} value={editor.isActive('heading') ? editor.getAttributes('heading').level : 'paragraph'} onChange={e => e.target.value === 'paragraph' ? editor.chain().focus().setParagraph().run() : editor.chain().focus().setHeading({ level: Number(e.target.value) as 1 | 2 | 3 | 4 | 5 | 6 }).run()}>
                <option value="paragraph">Paragraph</option>{[1,2,3,4,5,6].map(n => <option key={n} value={n}>Heading {n}</option>)}
            </select>
            {button('Bold', Bold, () => editor.chain().focus().toggleBold().run(), editor.isActive('bold'))}
            {button('Italic', Italic, () => editor.chain().focus().toggleItalic().run(), editor.isActive('italic'))}
            {button('Underline', Underline, () => editor.chain().focus().toggleUnderline().run(), editor.isActive('underline'))}
            {button('Strikethrough', Strikethrough, () => editor.chain().focus().toggleStrike().run(), editor.isActive('strike'))}
            {button('Highlight', Highlighter, () => editor.chain().focus().toggleHighlight().run(), editor.isActive('highlight'))}
            <label title="Text color" className="news-color"><span className="sr-only">Text color</span><input type="color" disabled={disabled} value={editor.getAttributes('textStyle').color || '#60a5fa'} onChange={e => editor.chain().focus().setColor(e.target.value).run()} /></label>
            <select aria-label="Font size" disabled={disabled} value={editor.getAttributes('textStyle').fontSize || ''} onChange={e => e.target.value ? editor.chain().focus().setFontSize(e.target.value).run() : editor.chain().focus().unsetFontSize().run()}><option value="">Size</option>{[14,16,18,20,24,30,36,48].map(n => <option key={n} value={`${n}px`}>{n}</option>)}</select>
            {button('Bulleted list', List, () => editor.chain().focus().toggleBulletList().run(), editor.isActive('bulletList'))}
            {button('Numbered list', ListOrdered, () => editor.chain().focus().toggleOrderedList().run(), editor.isActive('orderedList'))}
            {button('Quote', Quote, () => editor.chain().focus().toggleBlockquote().run(), editor.isActive('blockquote'))}
            {button('Code block', Code, () => editor.chain().focus().toggleCodeBlock().run(), editor.isActive('codeBlock'))}
            {button('Align left', AlignLeft, () => editor.chain().focus().setTextAlign('left').run(), editor.isActive({ textAlign: 'left' }))}
            {button('Align center', AlignCenter, () => editor.chain().focus().setTextAlign('center').run(), editor.isActive({ textAlign: 'center' }))}
            {button('Align right', AlignRight, () => editor.chain().focus().setTextAlign('right').run(), editor.isActive({ textAlign: 'right' }))}
            {button('Insert link', Link, () => showInsert('link'), editor.isActive('link'))}
            {button('Remove link', Unlink, () => editor.chain().focus().unsetLink().run(), false, !editor.isActive('link'))}
            {button('Insert image or GIF', ImagePlus, () => showInsert('image'))}
            {button('Insert video', Video, () => showInsert('video'))}
            <button type="button" disabled={disabled} onClick={() => showInsert('demo')}>Demo</button>
            {button('Insert table', Table, () => editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run())}
            {button('Divider', Minus, () => editor.chain().focus().setHorizontalRule().run())}
            <button type="button" title="Subscript" aria-label="Subscript" aria-pressed={editor.isActive('subscript')} disabled={disabled} onClick={() => editor.chain().focus().toggleSubscript().run()}>x₂</button>
            <button type="button" title="Superscript" aria-label="Superscript" aria-pressed={editor.isActive('superscript')} disabled={disabled} onClick={() => editor.chain().focus().toggleSuperscript().run()}>x²</button>
            {button('Clear formatting', RemoveFormatting, () => editor.chain().focus().unsetAllMarks().clearNodes().run())}
            {button('Undo', Undo2, () => editor.chain().focus().undo().run(), false, !editor.can().undo())}
            {button('Redo', Redo2, () => editor.chain().focus().redo().run(), false, !editor.can().redo())}
        </div>
        {editor.isActive('table') && <div className="news-format-bar" aria-label="Table controls">
            <button type="button" onClick={() => editor.chain().focus().addRowAfter().run()}>Add row</button><button type="button" onClick={() => editor.chain().focus().addColumnAfter().run()}>Add column</button>
            <button type="button" onClick={() => editor.chain().focus().deleteRow().run()}>Delete row</button><button type="button" onClick={() => editor.chain().focus().deleteColumn().run()}>Delete column</button>
            <button type="button" onClick={() => editor.chain().focus().mergeOrSplit().run()}>Merge / split cells</button><button type="button" onClick={() => editor.chain().focus().deleteTable().run()}>Remove table</button>
        </div>}
        {insert && <div className="news-insert-panel">
            <strong>{insert === 'demo' ? 'Insert a product demo' : `Insert ${insert}`}</strong>
            {insert === 'demo' ? <select aria-label="Demo video" value={clip} onChange={e => setClip(e.target.value)}>{Object.keys(clips).filter(c => !c.includes('thumbnail')).map(c => <option key={c} value={c}>{c.replaceAll('-', ' ')}</option>)}</select> : <input autoFocus aria-label="URL" placeholder={insert === 'video' ? 'Video URL (.mp4 or .webm)' : 'https://… or /assets/…'} value={url} onChange={e => setUrl(e.target.value)} />}
            {insert !== 'link' && <input aria-label="Media description" placeholder="Describe this media for readers" value={alt} onChange={e => setAlt(e.target.value)} />}
            {insert === 'image' && <label className="news-upload"><Upload size={16} />{uploading ? 'Uploading…' : 'Upload image or GIF'}<input type="file" accept="image/png,image/jpeg,image/webp,image/gif" disabled={uploading} onChange={async e => { const file = e.target.files?.[0]; if (!file) return; setUploading(true); try { setUrl(await newsClient.upload(file)); } catch (error) { setError(extractApiErrorMessage(error, 'Upload failed.')); } finally { setUploading(false); } }} /></label>}
            {error && <p role="alert">{error}</p>}
            <div className="news-editor-actions"><button type="button" onClick={() => setInsert(null)}>Cancel</button><button type="button" className="news-primary" disabled={uploading} onClick={insertMedia}>Insert</button></div>
        </div>}
        <EditorContent editor={editor} />
    </div>;
}
