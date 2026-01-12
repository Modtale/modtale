import React, { useMemo, useState, useCallback } from 'react';
import { Search, FileCode, Terminal, FileText, X, Folder, FolderOpen, ChevronRight, ChevronDown, File } from 'lucide-react';
import { api } from '../../utils/api';

interface SourceInspectorProps {
    modId: string;
    version: string;
    structure: string[];
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

    // Icon Selection
    let Icon = FileText;
    if (node.type === 'folder') Icon = isExpanded ? FolderOpen : Folder;
    else if (node.name.endsWith('.class') || node.name.endsWith('.java')) Icon = FileCode;
    else if (node.name.endsWith('.json') || node.name.endsWith('.yml')) Icon = File;

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
                {node.type === 'file' && <span className="w-3" />} {/* Spacer for alignment */}

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

const CodeViewer: React.FC<{ content: any; filename: string }> = ({ content, filename }) => {
    let ext = filename.split('.').pop()?.toLowerCase();
    if (ext === 'class') ext = 'java';

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
            src = src.replace(/('(\\.|[^'\\])*')/g, (m) => saveToken(m, 'text-emerald-400'));
            src = src.replace(/(\/\/.*)/g, (m) => saveToken(m, 'text-slate-500 italic'));
            src = src.replace(/(\/\*[\s\S]*?\*\/)/g, (m) => saveToken(m, 'text-slate-500 italic'));

            src = src.replace(/(@\w+)/g, (m) => saveToken(m, 'text-yellow-400'));

            src = src.replace(/\b(0x[0-9a-fA-F]+|\d+L?|\d*\.\d+f?)\b/g, (m) => saveToken(m, 'text-blue-400'));

            const kwControl = "if|else|switch|case|default|break|continue|return|do|while|for|throw|try|catch|finally";
            const kwModifiers = "public|private|protected|static|final|abstract|synchronized|volatile|transient|native|strictfp";
            const kwTypes = "int|long|short|byte|float|double|char|boolean|void|class|interface|enum|var";
            const kwContext = "this|super|new|instanceof|extends|implements|import|package|throws";
            const kwConst = "null|true|false";

            const replaceKw = (text: string, pattern: string, style: string) => {
                return text.replace(new RegExp(`\\b(${pattern})\\b`, 'g'), (m) => saveToken(m, style));
            };

            src = replaceKw(src, kwControl, 'text-purple-400 font-bold');
            src = replaceKw(src, kwModifiers, 'text-blue-400 italic');
            src = replaceKw(src, kwTypes, 'text-orange-400');
            src = replaceKw(src, kwContext, 'text-cyan-400');
            src = replaceKw(src, kwConst, 'text-red-400 font-bold');

            src = src.replace(/\b([A-Z]\w*)\b/g, (m) => saveToken(m, 'text-yellow-200'));
        }
        else if (ext === 'json') {
            src = src.replace(/: (&quot;[^&]*&quot;)/g, (match, val) => `: ${saveToken(val, 'text-emerald-400')}`);
            src = src.replace(/(&quot;[^&]+&quot;)\s*:/g, (match, key) => `${saveToken(key, 'text-indigo-400')}:`);
            src = src.replace(/\b(true|false|null)\b/g, (m) => saveToken(m, 'text-red-400 font-bold'));
            src = src.replace(/\b(\d+(\.\d+)?)\b/g, (m) => saveToken(m, 'text-blue-400'));
        }
        else if (ext === 'yaml' || ext === 'yml') {
            src = src.replace(/(#.*)/g, (m) => saveToken(m, 'text-slate-500 italic'));
            src = src.replace(/^(\s*)([\w.-]+):/gm, (match, space, key) => `${space}${saveToken(key, 'text-indigo-400')}:`);
            src = src.replace(/: (.+)/g, (match, val) => `: ${saveToken(val, 'text-emerald-400')}`);
        }

        tokens.forEach((html, i) => {
            src = src.split(`___TOKEN${i}___`).join(html);
        });

        return src;
    }, [safeContent, ext]);

    return (
        <>
            <style>{scrollbarStyles}</style>
            <pre className="font-mono text-xs text-slate-300 leading-relaxed whitespace-pre tab-4 p-4 overflow-auto h-full custom-scrollbar">
                <code dangerouslySetInnerHTML={{ __html: highlightedCode || safeContent }} />
            </pre>
        </>
    );
};

export const SourceInspector: React.FC<SourceInspectorProps> = ({ modId, version, structure, onClose }) => {
    const [inspectorFile, setInspectorFile] = useState<string | null>(null);
    const [inspectorContent, setInspectorContent] = useState<any>('');
    const [loadingFile, setLoadingFile] = useState(false);
    const [fileSearch, setFileSearch] = useState('');
    const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());

    const fileTree = useMemo(() => buildFileTree(structure), [structure]);

    const toggleFolder = useCallback((path: string) => {
        setExpandedFolders(prev => {
            const next = new Set(prev);
            if (next.has(path)) next.delete(path);
            else next.add(path);
            return next;
        });
    }, []);

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

    const filteredFiles = useMemo(() => {
        if (!fileSearch) return [];
        return structure.filter(f => f.toLowerCase().includes(fileSearch.toLowerCase()));
    }, [structure, fileSearch]);

    return (
        <div className="fixed inset-0 z-[160] bg-slate-950/90 backdrop-blur-md flex flex-col animate-in fade-in duration-200">
            <div className="h-14 border-b border-white/10 bg-slate-900 flex items-center justify-between px-4 shrink-0">
                <div className="flex items-center gap-4">
                    <FileCode className="w-5 h-5 text-indigo-400" />
                    <div>
                        <h3 className="text-sm font-bold text-white">Source Inspector</h3>
                        <p className="text-[10px] text-slate-400 font-mono">{modId} @ {version}</p>
                    </div>
                </div>
                <button onClick={onClose} className="p-2 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white">
                    <X className="w-5 h-5" />
                </button>
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
                        <CodeViewer content={inspectorContent} filename={inspectorFile} />
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