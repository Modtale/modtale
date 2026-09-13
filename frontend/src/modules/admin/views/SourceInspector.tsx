import { SkeletonSurface } from '@/components/ui/Skeleton';
import React, { useMemo, useState, useCallback, useRef, useEffect } from 'react';
import { Search, FileCode, Terminal, FileText, X, Folder, FolderOpen, ChevronRight, ChevronDown, ShieldAlert, CheckCircle2, Square, RefreshCw } from 'lucide-react';
import { adminClient, type InspectionWindow } from '../api/adminClient';
import { extractApiErrorMessage } from '@/utils/api';
import type { ScanIssue } from '@/types';
import { ModalPortal } from '@/components/ui/ModalPortal';

interface SourceInspectorProps {
    modId: string;
    versionId: string;
    canRescan?: boolean;
    version: string;
    reviewToken: string;
    structure: string[];
    issues?: ScanIssue[];
    initialFile?: string;
    initialLine?: number;
    initialLineEnd?: number;
    onClose: () => void;
}

interface TreeNode {
    name: string;
    path: string;
    type: 'file' | 'folder';
    children: TreeNode[];
}

const FILES_PER_PAGE = 200;

const buildFileTree = (paths: string[]): TreeNode[] => {
    const root: TreeNode[] = [];
    const levels = new Map<TreeNode[], Map<string, TreeNode>>();
    levels.set(root, new Map());
    for (const path of paths) {
        const parts = path.split('/');
        let children = root;
        let prefix = '';
        for (let index = 0; index < parts.length; index++) {
            const name = parts[index];
            const type = index === parts.length - 1 ? 'file' : 'folder';
            prefix += (index ? '/' : '') + name;
            const level = levels.get(children)!;
            const key = `${type}:${name}`;
            let node = level.get(key);
            if (!node) {
                node = { name, path: prefix, type, children: [] };
                level.set(key, node); children.push(node);
                levels.set(node.children, new Map());
            }
            children = node.children;
        }
    }
    // Archive paths can be deeply nested; neither sorting nor rendering recurses.
    for (const nodes of levels.keys()) nodes.sort((a, b) => a.type === b.type
        ? a.name.localeCompare(b.name) : a.type === 'folder' ? -1 : 1);
    return root;
};

const visibleFileRows = (tree: TreeNode[], expanded: Set<string>) => {
    const rows: { node: TreeNode; depth: number }[] = [];
    const pending = tree.map(node => ({ node, depth: 0 })).reverse();
    while (pending.length) {
        const row = pending.pop()!;
        rows.push(row);
        if (row.node.type === 'folder' && expanded.has(row.node.path)) {
            for (let i = row.node.children.length - 1; i >= 0; i--)
                pending.push({ node: row.node.children[i], depth: row.depth + 1 });
        }
    }
    return rows;
};

const FileRow: React.FC<{
    node: TreeNode;
    depth: number;
    expanded: Set<string>;
    toggleFolder: (path: string) => void;
    selectedFile: string | null;
    onSelectFile: (path: string) => void;
}> = ({ node, depth, expanded, toggleFolder, selectedFile, onSelectFile }) => {
    const folder = node.type === 'folder';
    const isExpanded = expanded.has(node.path);
    const isSelected = !folder && selectedFile === node.path;
    const Icon = folder ? (isExpanded ? FolderOpen : Folder)
        : /\.(class|java)$/.test(node.name) ? FileCode : FileText;
    return <button type="button" title={node.path}
        aria-label={`${folder ? 'Folder' : 'File'} ${node.path}`}
        aria-expanded={folder ? isExpanded : undefined}
        aria-current={isSelected ? 'true' : undefined}
        onClick={() => folder ? toggleFolder(node.path) : onSelectFile(node.path)}
        className={`w-full flex items-center gap-1.5 py-1 pr-2 rounded-lg text-left text-xs font-mono select-none focus-visible:outline focus-visible:outline-indigo-400 ${isSelected ? 'bg-indigo-500/20 text-indigo-300' : 'text-slate-400 hover:text-slate-200 hover:bg-white/5'}`}
        style={{ paddingLeft: `${Math.min(depth, 12) * 12 + 12}px` }}>
        {folder ? (isExpanded ? <ChevronDown aria-hidden="true" className="w-3 h-3 shrink-0" /> : <ChevronRight aria-hidden="true" className="w-3 h-3 shrink-0" />) : <span className="w-3 shrink-0" />}
        <Icon aria-hidden="true" className={`w-3.5 h-3.5 shrink-0 ${folder ? 'text-blue-400' : ''}`} />
        <span className="truncate">{node.name}</span>
    </button>;
};

const CodeViewer: React.FC<{ content: any; filename: string; startLine?: number; endLine?: number; firstLine?: number; format?: string }> = ({ content, filename, startLine, endLine, firstLine = 1, format }) => {
    const scrollContainerRef = useRef<HTMLDivElement>(null);

    const safeContent = useMemo(() => {
        if (content === null || content === undefined) return '';
        if (typeof content === 'object') {
            try { return JSON.stringify(content, null, 2); } catch (e) { return '[Object]'; }
        }
        return String(content);
    }, [content]);

    const lines = useMemo(() => safeContent.split('\n'), [safeContent]);
    const displayedRange = useMemo(() => {
        if (!startLine || startLine < 1) return undefined;
        if (format !== 'JVM_BYTECODE') return startLine >= firstLine && startLine < firstLine + lines.length ? { start: startLine - firstLine + 1, end: (endLine || startLine) - firstLine + 1 } : undefined;
        const matching = lines.flatMap((line, index) => {
            const marker = line.match(/^\s*LINENUMBER (\d+) /);
            const sourceLine = marker ? Number(marker[1]) : 0;
            return sourceLine >= startLine && sourceLine <= (endLine || startLine) ? [index + 1] : [];
        });
        return matching.length ? { start: matching[0], end: matching[matching.length - 1] } : undefined;
    }, [lines, safeContent, startLine, endLine, firstLine, format]);
    const displayedStart = displayedRange?.start;


    useEffect(() => {
        if (displayedStart && scrollContainerRef.current && displayedStart > 1) {
            const timer = setTimeout(() => {
                if (scrollContainerRef.current) {
                    const lineHeight = 20;
                    scrollContainerRef.current.scrollTop = Math.max(0, displayedStart - 5) * lineHeight;
                }
            }, 100);
            return () => clearTimeout(timer);
        }
    }, [displayedStart, content]);

    return (
        <div ref={scrollContainerRef} className="flex h-full overflow-auto bg-[#0d1117] font-mono text-xs relative">
            <div className="sticky left-0 z-10 h-fit min-h-full w-12 select-none border-r border-white/5 bg-[#0d1117] py-4 pr-3 text-right leading-5 text-slate-600">
                {lines.map((_, i) => (
                    <div key={i} className={(displayedRange && (i+1) >= displayedRange.start && (i+1) <= displayedRange.end) ? 'text-yellow-500 font-bold bg-yellow-500/10 w-full pr-1' : ''}>
                        {i + firstLine}
                    </div>
                ))}
            </div>

            <div className="flex-1 min-w-0">
                <pre className="text-slate-300 leading-5 p-4 pt-4 w-fit min-w-full">
                     <code>{safeContent}</code>
                </pre>
            </div>
        </div>
    );
};

export const SourceInspector: React.FC<SourceInspectorProps> = ({ modId, versionId, canRescan = false, version, reviewToken, structure, issues = [], initialFile, initialLine, initialLineEnd, onClose }) => {
    const requestGeneration = useRef(0);
    const fileListRef = useRef<HTMLElement>(null);
    useEffect(() => () => { requestGeneration.current++; }, [modId, version, reviewToken]);
    const [inspectorFile, setInspectorFile] = useState<string | null>(null);
    const [inspectorContent, setInspectorContent] = useState<any>('');
    const [window, setWindow] = useState<InspectionWindow | null>(null);
    const [previousOffsets, setPreviousOffsets] = useState<number[]>([]);
    const [loadingFile, setLoadingFile] = useState(false);
    const [fileSearch, setFileSearch] = useState('');
    const [filePage, setFilePage] = useState(0);
    const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
    const [showIssuesDropdown, setShowIssuesDropdown] = useState(false);
    const [resolvedIssues, setResolvedIssues] = useState<Set<number>>(new Set());
    const [isScanning, setIsScanning] = useState(false);
    const [actionError, setActionError] = useState<string | null>(null);

    const [activeHighlight, setActiveHighlight] = useState<{
        file: string;
        start: number;
        end: number;
    } | null>(null);

    useEffect(() => {
        setInspectorContent(''); setInspectorFile(null); setLoadingFile(false); setWindow(null); setPreviousOffsets([]);
        setResolvedIssues(new Set()); setActiveHighlight(null); setActionError(null);
        setFileSearch(''); setFilePage(0); setExpandedFolders(new Set());
    }, [modId, version, reviewToken]);

    const fileTree = useMemo(() => buildFileTree(structure), [structure]);

    const processedIssues = useMemo(() => {
        const severityWeight = (s: string) => ({ CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }[s] || 0);
        const sorter = (a: any, b: any) => severityWeight(b.severity) - severityWeight(a.severity);

        const withIndex = issues.map((issue, idx) => ({ ...issue, originalIndex: idx }));
        const unresolved = withIndex.filter(i => !resolvedIssues.has(i.originalIndex)).sort(sorter);
        const resolved = withIndex.filter(i => resolvedIssues.has(i.originalIndex)).sort(sorter);

        return { unresolved, resolved };
    }, [issues, resolvedIssues]);

    const toggleFolder = useCallback((path: string) => {
        setExpandedFolders(prev => {
            const next = new Set(prev);
            if (next.has(path)) next.delete(path);
            else next.add(path);
            return next;
        });
    }, []);

    const toggleResolved = (idx: number, e: React.MouseEvent) => {
        e.stopPropagation();
        setResolvedIssues(prev => {
            const next = new Set(prev);
            if (next.has(idx)) next.delete(idx);
            else next.add(idx);
            return next;
        });
    };

    const loadInspectorFile = async (path: string, offset = 0, identity?: string, previous: number[] = [], sourceLine = 0) => {
        const generation = ++requestGeneration.current;
        setInspectorContent(''); setWindow(null);
        setInspectorFile(path);
        setLoadingFile(true);
        try {
            const data = await adminClient.getFileWindow(modId, version, path, reviewToken, offset, identity, sourceLine);
            if (generation !== requestGeneration.current) return;
            if (!data || typeof data.content !== 'string' || typeof data.format !== 'string' || !Array.isArray(data.gaps) || !data.gaps.every(gap => typeof gap === 'string')
                || !Number.isInteger(data.firstLine) || data.firstLine < 1 || data.firstLine > data.start + 1
                || typeof data.lineMatched !== 'boolean' || typeof data.representationComplete !== 'boolean' || !/^[0-9a-f]{64}$/.test(data.identity)
                || (identity && identity !== data.identity) || !Number.isInteger(data.start) || !Number.isInteger(data.end)
                || !Number.isInteger(data.totalCharacters) || data.start < 0 || data.end < data.start || data.end > data.totalCharacters
                || data.totalCharacters > 4_000_000 || data.end - data.start !== data.content.length || data.content.length > 32000
                || (sourceLine === 0 && data.start !== offset)) throw new Error('The inspection changed. Reopen this file.');
            setInspectorContent(data.content); setWindow(data); setPreviousOffsets(previous);
            setActionError(null);
        } catch (e) {
            if (generation !== requestGeneration.current) return;
            const message = extractApiErrorMessage(e, 'We could not load this file from the archive.');
            setActionError(message);
            setInspectorContent(`// ${message}`);
        } finally {
            if (generation === requestGeneration.current) setLoadingFile(false);
        }
    };

    const handleJumpToIssue = (file: string, lineStart: number, lineEnd: number) => {
        let targetFile = file;
        if (!structure.includes(targetFile) && structure.includes(targetFile + ".class")) {
            targetFile = targetFile + ".class";
        }

        const parts = targetFile.split('/');
        const foldersToExpand = new Set<string>();
        let currentPath = "";
        for(let i=0; i<parts.length-1; i++) {
            currentPath += (i > 0 ? "/" : "") + parts[i];
            foldersToExpand.add(currentPath);
        }
        setExpandedFolders(prev => new Set([...prev, ...foldersToExpand]));

        setFileSearch('');
        setActiveHighlight({
            file: targetFile,
            start: lineStart,
            end: lineEnd,
        });

        loadInspectorFile(targetFile, 0, undefined, [], Math.max(0, lineStart || 0));
        setShowIssuesDropdown(false);
    };

    const handleRescan = async () => {
        if (!canRescan || !versionId) return;
        setIsScanning(true);
        try {
            await adminClient.scanVersion(modId, versionId);
            setActionError(null);
        } catch (e) {
            setActionError(extractApiErrorMessage(e, 'We could not start a rescan for this version.'));
        } finally {
            setIsScanning(false);
        }
    };

    useEffect(() => {
        if (initialFile) {
            const issue = issues.find(i => i.filePath === initialFile && i.lineStart === initialLine);
            const targetLineEnd = initialLineEnd || issue?.lineEnd || initialLine || 0;
            handleJumpToIssue(initialFile, initialLine || 0, targetLineEnd);
        }
    }, [initialFile, modId, version, reviewToken]);

    const dynamicHighlight = useMemo(() => {
        if (!activeHighlight || activeHighlight.file !== inspectorFile) return undefined;
        if (activeHighlight.start > 0) {
            return { start: activeHighlight.start, end: activeHighlight.end };
        }
        return undefined;
    }, [activeHighlight, inspectorFile]);

    const fileRows = useMemo(() => {
        const search = fileSearch.toLowerCase();
        if (search) return structure.filter(path => path.toLowerCase().includes(search))
            .map(path => ({ node: { name: path, path, type: 'file' as const, children: [] }, depth: 0 }));
        return visibleFileRows(fileTree, expandedFolders);
    }, [structure, fileSearch, fileTree, expandedFolders]);
    const lastFilePage = Math.max(0, Math.ceil(fileRows.length / FILES_PER_PAGE) - 1);
    const currentFilePage = Math.min(filePage, lastFilePage);
    const firstFileRow = currentFilePage * FILES_PER_PAGE;
    useEffect(() => {
        // A finding jump reveals its file even when its ancestors span several pages.
        const index = fileRows.findIndex(row => row.node.type === 'file' && row.node.path === inspectorFile);
        if (index >= 0) setFilePage(Math.floor(index / FILES_PER_PAGE));
    }, [inspectorFile, modId, version, reviewToken]);
    useEffect(() => {
        if (fileListRef.current) fileListRef.current.scrollTop = 0;
    }, [currentFilePage, fileSearch]);

    const renderIssueItem = (issue: any, isResolved: boolean) => (
        <div
            key={issue.originalIndex}
            className={`w-full text-left p-3 hover:bg-white/5 rounded-lg group border border-transparent hover:border-white/5 transition-all mb-1 ${isResolved ? 'opacity-50' : ''}`}
        >
            <div className="flex items-start justify-between gap-3">
                <div className="flex-1 cursor-pointer" onClick={() => handleJumpToIssue(issue.filePath, issue.lineStart, issue.lineEnd)}>
                    <div className="flex items-center gap-2 mb-1">
                        <span className={`font-black text-[10px] px-1.5 py-0.5 rounded uppercase
                                                        ${issue.severity === 'CRITICAL' ? 'bg-red-500 text-white' : 'bg-amber-500 text-white'}`}>
                            {issue.severity}
                        </span>
                        <span className={`text-xs font-bold truncate flex-1 ${isResolved ? 'text-slate-500 line-through' : 'text-slate-300'}`}>{issue.type}</span>
                    </div>
                    <div className="text-[10px] text-slate-500 font-mono truncate mb-1">
                        {issue.filePath.split('/').pop()} {issue.lineStart > 0 ? `:${issue.lineStart} - ${issue.lineEnd}` : ''}
                    </div>
                    <p className="text-[10px] text-slate-400 line-clamp-2">{issue.description}</p>
                </div>

                <button
                    onClick={(e) => toggleResolved(issue.originalIndex, e)}
                    className={`shrink-0 p-1 rounded hover:bg-white/10 transition-colors ${isResolved ? 'text-emerald-500' : 'text-slate-600'}`}
                    title={isResolved ? "Mark as Unresolved" : "Mark as Resolved"}
                >
                    {isResolved ? <CheckCircle2 className="w-5 h-5"/> : <Square className="w-5 h-5"/>}
                </button>
            </div>
        </div>
    );

    return (
        <ModalPortal>
        <div className="fixed inset-0 z-[160] bg-slate-950/90 backdrop-blur-md flex flex-col animate-in fade-in duration-200">
            <div className="h-14 border-b border-white/10 bg-slate-900 flex items-center justify-between px-4 shrink-0">
                <div className="flex items-center gap-4">
                    <FileCode className="w-5 h-5 text-indigo-400" />
                    <div>
                        <h3 className="text-sm font-bold text-white">Source Inspector</h3>
                        <p className="text-[10px] text-slate-400 font-mono">{modId} @ {version}</p>
                    </div>

                    {issues.length > 0 && (
                        <div className="relative ml-4">
                            <button
                                onClick={() => setShowIssuesDropdown(!showIssuesDropdown)}
                                className="flex items-center gap-2 px-3 py-1.5 bg-red-500/10 hover:bg-red-500/20 text-red-400 rounded-lg text-xs font-bold transition-colors border border-red-500/20"
                            >
                                <ShieldAlert className="w-3.5 h-3.5" />
                                {processedIssues.unresolved.length} / {issues.length} Issues Active
                                <ChevronDown className="w-3 h-3 opacity-50" />
                            </button>

                            {showIssuesDropdown && (
                                <div className="absolute top-full left-0 mt-2 w-[500px] max-h-[600px] overflow-y-auto bg-slate-900 border border-white/10 rounded-xl shadow-2xl z-50 p-2">
                                    <h4 className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-2 px-2 flex justify-between sticky top-0 bg-slate-900 z-10 py-1">
                                        <span>Active Issues</span>
                                    </h4>
                                    {processedIssues.unresolved.length > 0 ? (
                                        processedIssues.unresolved.map(issue => renderIssueItem(issue, false))
                                    ) : (
                                        <div className="p-4 text-center text-slate-500 text-xs italic">No active issues.</div>
                                    )}

                                    {processedIssues.resolved.length > 0 && (
                                        <>
                                            <div className="h-px bg-white/10 my-2 mx-2"></div>
                                            <h4 className="text-[10px] font-bold text-emerald-500/70 uppercase tracking-wider mb-2 px-2 sticky top-0 bg-slate-900 z-10 py-1">
                                                Resolved ({processedIssues.resolved.length})
                                            </h4>
                                            {processedIssues.resolved.map(issue => renderIssueItem(issue, true))}
                                        </>
                                    )}
                                </div>
                            )}
                        </div>
                    )}
                </div>
                <div className="flex items-center gap-2">
                    {canRescan && versionId && <button
                        onClick={handleRescan}
                        disabled={isScanning}
                        className="p-2 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white transition-colors"
                        title="Rescan File"
                    >
                        <RefreshCw className={`w-5 h-5 ${isScanning ? 'animate-spin' : ''}`} />
                    </button>}
                    <button aria-label="Close source inspector" onClick={onClose} className="p-2 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white">
                        <X className="w-5 h-5" />
                    </button>
                </div>
            </div>

            {actionError && (
                <div className="border-b border-red-500/20 bg-red-500/10 px-4 py-3 text-sm font-medium text-red-300">
                    {actionError}
                </div>
            )}

            <div className="flex-1 flex overflow-hidden">
                <div className="w-80 bg-slate-950 border-r border-white/10 flex flex-col">
                    <div className="p-3 border-b border-white/10 bg-slate-950 sticky top-0 z-10">
                        <div className="relative">
                            <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-500" />
                            <input
                                type="text"
                                placeholder="Search files..."
                                aria-label="Search files"
                                className="w-full pl-9 pr-4 py-2 bg-white/5 border border-white/10 rounded-lg text-sm text-white placeholder:text-slate-500 focus:ring-1 focus:ring-indigo-500 outline-none"
                                value={fileSearch}
                                onChange={e => { setFileSearch(e.target.value); setFilePage(0); }}
                            />
                        </div>
                    </div>

                    <nav ref={fileListRef} aria-label="Archive files" className="flex-1 overflow-y-auto p-2">
                        {!fileRows.length && <p className="p-4 text-center text-xs text-slate-500">{fileSearch ? 'No files found' : 'No files available'}</p>}
                        {fileRows.slice(firstFileRow, firstFileRow + FILES_PER_PAGE).map(({ node, depth }) => (
                            <FileRow key={`${node.type}:${node.path}`} node={node} depth={depth}
                                expanded={expandedFolders} toggleFolder={toggleFolder}
                                selectedFile={inspectorFile} onSelectFile={loadInspectorFile} />
                        ))}
                    </nav>
                    <div className="border-t border-white/10 p-3 text-xs text-slate-300">
                        <p role="status">{fileRows.length ? `Entries ${firstFileRow + 1}–${Math.min(firstFileRow + FILES_PER_PAGE, fileRows.length)} of ${fileRows.length}` : '0 entries'}</p>
                        <div className="flex flex-wrap gap-3 mt-2">
                            <button disabled={currentFilePage === 0} onClick={() => setFilePage(0)} className="disabled:opacity-40">First files</button>
                            <button disabled={currentFilePage === 0} onClick={() => setFilePage(currentFilePage - 1)} className="disabled:opacity-40">Previous files</button>
                            <button disabled={currentFilePage === lastFilePage} onClick={() => setFilePage(currentFilePage + 1)} className="disabled:opacity-40">Next files</button>
                            <button disabled={currentFilePage === lastFilePage} onClick={() => setFilePage(lastFilePage)} className="disabled:opacity-40">Last files</button>
                        </div>
                    </div>
                </div>

                <div className="flex-1 bg-[#0d1117] overflow-hidden flex flex-col">
                    {window && inspectorFile && <div className="border-b border-white/10 px-4 py-2 text-xs text-slate-300 space-y-2">
                        <div className="flex items-center gap-3">
                            <span>{window.totalCharacters ? `Characters ${window.start + 1}–${window.end} of ${window.totalCharacters}` : 'No text content'}</span>
                            <button disabled={loadingFile || window.start === 0} onClick={() => loadInspectorFile(inspectorFile, 0, window.identity)} className="disabled:opacity-40">Start of file</button>
                            <button disabled={loadingFile || !previousOffsets.length} onClick={() => loadInspectorFile(inspectorFile, previousOffsets[previousOffsets.length - 1], window.identity, previousOffsets.slice(0, -1))} className="disabled:opacity-40">Previous section</button>
                            <button disabled={loadingFile || window.end >= window.totalCharacters} onClick={() => loadInspectorFile(inspectorFile, window.end, window.identity, [...previousOffsets, window.start])} className="disabled:opacity-40">Next section</button>
                        </div>
                        {window.format === 'JVM_BYTECODE' && <p>JVM bytecode of the uploaded class. LINENUMBER entries refer to original source lines.</p>}
                        {!window.lineMatched && <p>The requested source line was not found in this representation.</p>}
                        {!window.representationComplete && <p className="text-amber-300">This representation is incomplete. {window.gaps.join(' ')}</p>}
                    </div>}
                    {loadingFile ? (
                        <SkeletonSurface className="h-full [&>.skeleton-layout]:h-full" label="Loading source file">
                            <CodeViewer filename={inspectorFile || 'source.txt'} content={Array.from({ length: 24 }, (_, index) => `${'    '.repeat(index % 3)}Source code line awaiting file content`).join('\n')} />
                        </SkeletonSurface>
                    ) : inspectorFile ? (
                        <CodeViewer
                            key={`${inspectorFile}:${window?.start ?? 0}`}
                            content={inspectorContent}
                            filename={inspectorFile}
                            firstLine={window?.firstLine}
                            format={window?.format}
                            startLine={dynamicHighlight?.start}
                            endLine={dynamicHighlight?.end}
                        />
                    ) : (
                        <div className="flex h-full items-center justify-center text-slate-600 flex-col gap-4">
                            <Terminal className="w-12 h-12 opacity-50" />
                            <p>Select a file to inspect its contents</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
        </ModalPortal>
    );
};
