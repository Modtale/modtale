// @vitest-environment node
import path from 'node:path';
import net from 'node:net';
import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import * as ts from 'typescript';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

interface LazyImportTarget {
    sourceFile: string;
    specifier: string;
    clientUrl: string;
}

const srcRoot = path.resolve(process.cwd(), 'src');
const startupTimeoutMs = 60_000;
const readinessPollMs = 200;

const collectSourceFiles = (dir: string): string[] => {
    const entries = readdirSync(dir);
    const files: string[] = [];

    for (const entry of entries) {
        const fullPath = path.join(dir, entry);
        const stats = statSync(fullPath);

        if (stats.isDirectory()) {
            files.push(...collectSourceFiles(fullPath));
            continue;
        }

        if (/\.(ts|tsx)$/.test(entry)) {
            files.push(fullPath);
        }
    }

    return files;
};

const resolveModuleFile = (candidatePath: string) => {
    const candidates = [
        candidatePath,
        `${candidatePath}.ts`,
        `${candidatePath}.tsx`,
        `${candidatePath}.js`,
        `${candidatePath}.jsx`,
        path.join(candidatePath, 'index.ts'),
        path.join(candidatePath, 'index.tsx'),
        path.join(candidatePath, 'index.js'),
        path.join(candidatePath, 'index.jsx'),
    ];

    for (const filePath of candidates) {
        try {
            if (statSync(filePath).isFile()) {
                return filePath;
            }
        } catch {
            // Try the next candidate.
        }
    }

    return null;
};

const normalizeLazySpecifier = (sourceFile: string, specifier: string) => {
    const resolvedBasePath = specifier.startsWith('@/') ? path.resolve(srcRoot, specifier.slice(2)) : specifier.startsWith('.') ? path.resolve(path.dirname(sourceFile), specifier) : null;

    if (!resolvedBasePath) {
        return null;
    }

    const resolvedFilePath = resolveModuleFile(resolvedBasePath);
    if (!resolvedFilePath) {
        return null;
    }

    const relativeToSrc = path.relative(srcRoot, resolvedFilePath).split(path.sep).join('/');
    if (relativeToSrc.startsWith('..')) {
        return null;
    }

    return {
        specifier,
        clientUrl: `/src/${relativeToSrc}`,
    };
};

const collectLazyImports = (): LazyImportTarget[] => {
    const targets: LazyImportTarget[] = [];

    for (const filePath of collectSourceFiles(srcRoot)) {
        const source = readFileSync(filePath, 'utf8');
        if (!source.includes('lazy(') || !source.includes('import(')) continue;

        const sourceFile = ts.createSourceFile(
            filePath,
            source,
            ts.ScriptTarget.Latest,
            true,
            filePath.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS
        );

        const visit = (node: ts.Node) => {
            if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'lazy') {
                const callText = node.getText(sourceFile);
                const specifierMatch = callText.match(/import\(\s*['"]([^'"]+)['"]\s*\)/);

                if (specifierMatch) {
                    const normalized = normalizeLazySpecifier(filePath, specifierMatch[1]);

                    if (normalized) {
                        targets.push({
                            sourceFile: path.relative(srcRoot, filePath).split(path.sep).join('/'),
                            specifier: normalized.specifier,
                            clientUrl: normalized.clientUrl,
                        });
                    }
                }
            }

            ts.forEachChild(node, visit);
        };

        visit(sourceFile);
    }

    return Array.from(
        new Map(
            targets.map((target) => [
                `${target.sourceFile}:${target.specifier}:${target.clientUrl}`,
                target
            ])
        ).values()
    );
};

const lazyImports = collectLazyImports();

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const reservePort = async () => {
    const server = net.createServer();

    await new Promise<void>((resolve, reject) => {
        server.once('error', reject);
        server.listen(0, '127.0.0.1', () => resolve());
    });

    const address = server.address();
    const port = typeof address === 'object' && address ? address.port : 0;

    await new Promise<void>((resolve, reject) => {
        server.close((error) => {
            if (error) reject(error);
            else resolve();
        });
    });

    return port;
};

const waitForServerReady = async (origin: string, child: ChildProcessWithoutNullStreams, getLogs: () => string) => {
    const start = Date.now();

    while (Date.now() - start < startupTimeoutMs) {
        if (child.exitCode !== null) {
            throw new Error(`Astro dev server exited before it became ready.\n${getLogs()}`);
        }

        try {
            const response = await fetch(origin);
            if (response.ok) {
                return;
            }
        } catch {
            // Keep polling until the server comes up.
        }

        await sleep(readinessPollMs);
    }

    throw new Error(`Timed out waiting for Astro dev server at ${origin}.\n${getLogs()}`);
};

const stopServer = async (child: ChildProcessWithoutNullStreams | null) => {
    if (!child || child.exitCode !== null) {
        return;
    }

    child.kill('SIGTERM');

    await Promise.race([
        new Promise<void>((resolve) => {
            child.once('exit', () => resolve());
        }),
        sleep(5_000).then(() => {
            if (child.exitCode === null) {
                child.kill('SIGKILL');
            }
        }),
    ]);
};

const extractImportSpecifiers = (code: string) => {
    const matches = new Set<string>();
    const patterns = [
        /\bimport\s+(?:[^'"()]*?\s+from\s+)?['"]([^'"]+)['"]/g,
        /\bexport\s+(?:[^'"()]*?\s+from\s+)?['"]([^'"]+)['"]/g,
        /\bimport\(\s*['"]([^'"]+)['"]\s*\)/g,
    ];

    for (const pattern of patterns) {
        for (const match of code.matchAll(pattern)) {
            matches.add(match[1]);
        }
    }

    return Array.from(matches);
};

const fetchText = async (url: string, label: string) => {
    const response = await fetch(url);
    const body = await response.text();

    expect(response.status, `${label} should load over HTTP, but ${url} responded with ${response.status}.\n${body.slice(0, 500)}`).toBe(200);

    return body;
};

describe('lazy-loaded module integrity', () => {
    let devServer: ChildProcessWithoutNullStreams | null = null;
    let origin = '';
    let stdout = '';
    let stderr = '';

    beforeAll(async () => {
        const port = await reservePort();
        origin = `http://127.0.0.1:${port}`;

        devServer = spawn('npm', ['run', 'dev', '--', '--host', '127.0.0.1', '--port', String(port)], {
            cwd: process.cwd(),
            env: process.env,
            stdio: ['ignore', 'pipe', 'pipe'],
        });

        devServer.stdout.on('data', (chunk) => {
            stdout += chunk.toString();
        });

        devServer.stderr.on('data', (chunk) => {
            stderr += chunk.toString();
        });

        await waitForServerReady(origin, devServer, () => `stdout:\n${stdout}\n\nstderr:\n${stderr}`);
    }, startupTimeoutMs);

    afterAll(async () => {
        await stopServer(devServer);
    });

    it.each(lazyImports)(
        'serves %s from %s with direct browser imports that all resolve',
        async ({ sourceFile, specifier, clientUrl }) => {
            const moduleUrl = new URL(clientUrl, origin).href;
            const moduleCode = await fetchText(moduleUrl, `${sourceFile} lazy import ${specifier}`);
            const directImports = extractImportSpecifiers(moduleCode);

            expect(
                directImports.length,
                `${sourceFile} lazy import ${specifier} should expose at least one direct browser import from ${clientUrl}`
            ).toBeGreaterThan(0);

            await Promise.all(
                directImports.map(async (importSpecifier) => {
                    if (/^(?:https?:|data:)/.test(importSpecifier)) {
                        return;
                    }

                    const dependencyUrl = new URL(importSpecifier, moduleUrl).href;
                    await fetchText(
                        dependencyUrl,
                        `${sourceFile} lazy import ${specifier} dependency ${importSpecifier}`
                    );
                })
            );
        }
    );
});
