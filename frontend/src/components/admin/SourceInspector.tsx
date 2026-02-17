import React, { useMemo, useState, useCallback, useRef, useEffect } from 'react';
import { Search, FileCode, Terminal, FileText, X, Folder, FolderOpen, ChevronRight, ChevronDown, ShieldAlert, CheckCircle2, Square, RefreshCw } from 'lucide-react';
import { api } from '../../utils/api';
import type { ScanIssue } from '../../types';

interface SourceInspectorProps {
    modId: string;
    versionId: string;
    version: string;
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

const buildFileTree = (paths: string[]): TreeNode[] => {
    const root: TreeNode[] = [];

    paths.forEach(path => {
        const parts = path.split('/');
        let currentLevel = root;

        parts.forEach((part, index) => {
            const isFile = index === parts.length - 1;
            const existingNode = currentLevel.find(n => n.name === part && n.type === (isFile ? 'file' : 'folder'));

            if (existingNode) {
                currentLevel = existingNode.children;
            } else {
                const newNode: TreeNode = {
                    name: part,
                    path: isFile ? path : parts.slice(0, index + 1).join('/'),
                    type: isFile ? 'file' : 'folder',
                    children: []
                };
                currentLevel.push(newNode);
                currentLevel = newNode.children;
            }
        });
    });

    const sortNodes = (nodes: TreeNode[]) => {
        nodes.sort((a, b) => {
            if (a.type === b.type) return a.name.localeCompare(b.name);
            return a.type === 'folder' ? -1 : 1;
        });
        nodes.forEach(node => {
            if (node.children.length > 0) sortNodes(node.children);
        });
    };

    sortNodes(root);
    return root;
};

const FileTreeNode: React.FC<{
    node: TreeNode;
    depth: number;
    expanded: Set<string>;
    toggleFolder: (path: string) => void;
    selectedFile: string | null;
    onSelectFile: (path: string) => void;
}> = ({ node, depth, expanded, toggleFolder, selectedFile, onSelectFile }) => {
    const isExpanded = expanded.has(node.path);
    const isSelected = selectedFile === node.path;

    let Icon = FileText;
    if (node.type === 'folder') Icon = isExpanded ? FolderOpen : Folder;
    else if (node.name.endsWith('.class') || node.name.endsWith('.java')) Icon = FileCode;
    else if (node.name.endsWith('.json') || node.name.endsWith('.yml')) Icon = FileText;

    const handleClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (node.type === 'folder') {
            toggleFolder(node.path);
        } else {
            onSelectFile(node.path);
        }
    };

    return (
        <div>
            <div
                onClick={handleClick}
                className={`flex items-center gap-1.5 py-1 pr-2 rounded-lg cursor-pointer transition-colors text-xs font-mono select-none
                ${isSelected ? 'bg-indigo-500/20 text-indigo-300' : 'text-slate-400 hover:text-slate-200 hover:bg-white/5'}
                `}
                style={{ paddingLeft: `${depth * 12 + 12}px` }}
            >
                {node.type === 'folder' && (
                    <span className="opacity-50">
                        {isExpanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                    </span>
                )}
                {node.type === 'file' && <span className="w-3" />}

                <Icon className={`w-3.5 h-3.5 shrink-0 ${node.type === 'folder' ? 'text-blue-400' : ''}`} />
                <span className="truncate">{node.name}</span>
            </div>

            {node.type === 'folder' && isExpanded && (
                <div>
                    {node.children.map(child => (
                        <FileTreeNode
                            key={child.path}
                            node={child}
                            depth={depth + 1}
                            expanded={expanded}
                            toggleFolder={toggleFolder}
                            selectedFile={selectedFile}
                            onSelectFile={onSelectFile}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

const CodeViewer: React.FC<{ content: any; filename: string; startLine?: number; endLine?: number }> = ({ content, filename, startLine, endLine }) => {
    let ext = filename.split('.').pop()?.toLowerCase();
    if (ext === 'class') ext = 'java';

    const scrollContainerRef = useRef<HTMLDivElement>(null);

    const scrollbarStyles = `
        .custom-scrollbar::-webkit-scrollbar { width: 8px; height: 8px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: #0f1117; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background: #334155; border-radius: 4px; border: 2px solid #0f1117; }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #475569; }
    `;

    const safeContent = useMemo(() => {
        if (content === null || content === undefined) return '';
        if (typeof content === 'object') {
            try { return JSON.stringify(content, null, 2); } catch (e) { return '[Object]'; }
        }
        return String(content);
    }, [content]);

    const lines = useMemo(() => safeContent.split('\n'), [safeContent]);

    const highlightedCode = useMemo(() => {
        if (!safeContent) return '';
        const entityMap: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
        let src = safeContent.replace(/[&<>"']/g, (m) => entityMap[m] || m);
        if (src.length > 50000) return src;

        const tokens: string[] = [];
        const saveToken = (text: string, type: string) => {
            tokens.push(`<span class="${type}">${text}</span>`);
            return `___TOKEN${tokens.length - 1}___`;
        };

        if (ext === 'java') {
            src = src.replace(/(&quot;(\\.|[^&"\\])*&quot;)/g, (m) => saveToken(m, 'text-emerald-400'));
            src = src.replace(/(\/\/.*)/g, (m) => saveToken(m, 'text-slate-500 italic'));
            src = src.replace(/\b(public|private|protected|static|final|class|void|int|boolean|if|else|return|new)\b/g, (m) => saveToken(m, 'text-purple-400 font-bold'));
        }

        tokens.forEach((html, i) => { src = src.split(`___TOKEN${i}___`).join(html); });
        return src;
    }, [safeContent, ext]);

    useEffect(() => {
        if (startLine && scrollContainerRef.current && startLine > 1) {
            setTimeout(() => {
                if (scrollContainerRef.current) {
                    const lineHeight = 20;
                    scrollContainerRef.current.scrollTop = (startLine - 5) * lineHeight;
                }
            }, 100);
        }
    }, [startLine, content]);

    return (
        <>
            <style>{scrollbarStyles}</style>
            <div ref={scrollContainerRef} className="flex h-full font-mono text-xs overflow-auto custom-scrollbar bg-[#0d1117] relative">
                <div className="sticky left-0 z-10 w-12 bg-[#0d1117] border-r border-white/5 text-slate-600 text-right py-4 pr-3 select-none leading-5 min-h-full h-fit">
                    {lines.map((_, i) => (
                        <div key={i} className={(startLine && endLine && (i+1) >= startLine && (i+1) <= endLine) ? 'text-yellow-500 font-bold bg-yellow-500/10 w-full pr-1' : ''}>
                            {i + 1}
                        </div>
                    ))}
                </div>

                <div className="flex-1 min-w-0">
                    <pre className="text-slate-300 leading-5 p-4 pt-4 w-fit min-w-full">
                         <code dangerouslySetInnerHTML={{ __html: highlightedCode || safeContent }} />
                    </pre>
                </div>
            </div>
        </>
    );
};

export const SourceInspector: React.FC<SourceInspectorProps> = ({ modId, versionId, version, structure, issues = [], initialFile, initialLine, initialLineEnd, onClose }) => {
    const [inspectorFile, setInspectorFile] = useState<string | null>(null);
    const [inspectorContent, setInspectorContent] = useState<any>('');
    const [loadingFile, setLoadingFile] = useState(false);
    const [fileSearch, setFileSearch] = useState('');
    const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
    const [showIssuesDropdown, setShowIssuesDropdown] = useState(false);
    const [resolvedIssues, setResolvedIssues] = useState<Set<number>>(new Set());
    const [isScanning, setIsScanning] = useState(false);

    const [activeHighlight, setActiveHighlight] = useState<{
        file: string;
        start: number;
        end: number;
    } | null>(null);

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

    const loadInspectorFile = async (path: string) => {
        setInspectorFile(path);
        setLoadingFile(true);
        try {
            const res = await api.get(`/admin/projects/${modId}/versions/${version}/file`, { params: { path } });
            setInspectorContent(res.data);
        } catch (e) {
            setInspectorContent('// Error loading file content.');
        } finally {
            setLoadingFile(false);
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

        setActiveHighlight({
            file: targetFile,
            start: lineStart,
            end: lineEnd,
        });

        loadInspectorFile(targetFile);
        setShowIssuesDropdown(false);
    };

    const handleRescan = async () => {
        setIsScanning(true);
        try {
            await api.post(`/admin/projects/${modId}/versions/${versionId}/scan`);
        } catch (e) {
            console.error("Rescan failed", e);
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
    }, [initialFile]);

    const dynamicHighlight = useMemo(() => {
        if (!activeHighlight || activeHighlight.file !== inspectorFile) return undefined;
        if (activeHighlight.start > 0) {
            return { start: activeHighlight.start, end: activeHighlight.end };
        }
        return undefined;
    }, [activeHighlight, inspectorFile]);

    const filteredFiles = useMemo(() => {
        if (!fileSearch) return [];
        return structure.filter(f => f.toLowerCase().includes(fileSearch.toLowerCase()));
    }, [structure, fileSearch]);

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
                                <div className="absolute top-full left-0 mt-2 w-[500px] max-h-[600px] overflow-y-auto bg-slate-900 border border-white/10 rounded-xl shadow-2xl z-50 p-2 custom-scrollbar">
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
                    <button
                        onClick={handleRescan}
                        disabled={isScanning}
                        className="p-2 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white transition-colors"
                        title="Rescan File"
                    >
                        <RefreshCw className={`w-5 h-5 ${isScanning ? 'animate-spin' : ''}`} />
                    </button>
                    <button onClick={onClose} className="p-2 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white">
                        <X className="w-5 h-5" />
                    </button>
                </div>
            </div>

            <div className="flex-1 flex overflow-hidden">
                <div className="w-80 bg-slate-950 border-r border-white/10 flex flex-col">
                    <div className="p-3 border-b border-white/10 bg-slate-950 sticky top-0 z-10">
                        <div className="relative">
                            <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-500" />
                            <input
                                type="text"
                                placeholder="Search files..."
                                className="w-full pl-9 pr-4 py-2 bg-white/5 border border-white/10 rounded-lg text-sm text-white placeholder:text-slate-500 focus:ring-1 focus:ring-indigo-500 outline-none"
                                value={fileSearch}
                                onChange={e => setFileSearch(e.target.value)}
                            />
                        </div>
                    </div>

                    <div className="flex-1 overflow-y-auto p-2 custom-scrollbar">
                        {fileSearch ? (
                            <div>
                                {filteredFiles.length === 0 && (
                                    <div className="p-4 text-center text-xs text-slate-500 italic">No files found</div>
                                )}
                                {filteredFiles.map((file, idx) => (
                                    <button
                                        key={idx}
                                        onClick={() => loadInspectorFile(file)}
                                        className={`w-full text-left px-3 py-2 rounded-lg text-xs font-mono truncate flex items-center gap-2 transition-colors ${inspectorFile === file ? 'bg-indigo-500/20 text-indigo-300' : 'text-slate-400 hover:bg-white/5 hover:text-white'}`}
                                    >
                                        <FileCode className="w-3 h-3 shrink-0" />
                                        {file}
                                    </button>
                                ))}
                            </div>
                        ) : (
                            <div>
                                {fileTree.map(node => (
                                    <FileTreeNode
                                        key={node.path}
                                        node={node}
                                        depth={0}
                                        expanded={expandedFolders}
                                        toggleFolder={toggleFolder}
                                        selectedFile={inspectorFile}
                                        onSelectFile={loadInspectorFile}
                                    />
                                ))}
                            </div>
                        )}
                    </div>
                </div>

                <div className="flex-1 bg-[#0d1117] overflow-hidden flex flex-col">
                    {loadingFile ? (
                        <div className="flex h-full items-center justify-center text-slate-500 gap-2">
                            <div className="animate-spin w-4 h-4 border-2 border-indigo-500 border-t-transparent rounded-full"></div>
                            Decompiling...
                        </div>
                    ) : inspectorFile ? (
                        <CodeViewer
                            content={inspectorContent}
                            filename={inspectorFile}
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
    );
};