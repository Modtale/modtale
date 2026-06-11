// @vitest-environment node
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import * as ts from 'typescript';
import react from '@vitejs/plugin-react';
import { createServer, type InlineConfig, type ViteDevServer } from 'vite';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

interface LazyImportTarget {
    sourceFile: string;
    specifier: string;
    viteSpecifier: string;
    exportName: string;
}

const srcRoot = path.resolve(process.cwd(), 'src');

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

const normalizeLazySpecifier = (sourceFile: string, specifier: string) => {
    if (specifier.startsWith('@/')) {
        return {
            specifier,
            viteSpecifier: `/src/${specifier.slice(2)}`
        };
    }

    if (!specifier.startsWith('.')) {
        return null;
    }

    const resolvedPath = path.resolve(path.dirname(sourceFile), specifier);
    const relativeToSrc = path.relative(srcRoot, resolvedPath).split(path.sep).join('/');

    if (relativeToSrc.startsWith('..')) {
        return null;
    }

    return {
        specifier: `@/${relativeToSrc}`,
        viteSpecifier: `/src/${relativeToSrc}`
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
                const exportMatch = callText.match(/default:\s*(?:module|m)\.([A-Za-z0-9_]+)/);

                if (specifierMatch) {
                    const normalized = normalizeLazySpecifier(filePath, specifierMatch[1]);

                    if (normalized) {
                        targets.push({
                            sourceFile: path.relative(srcRoot, filePath).split(path.sep).join('/'),
                            specifier: normalized.specifier,
                            viteSpecifier: normalized.viteSpecifier,
                            exportName: exportMatch?.[1] ?? 'default'
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
                `${target.sourceFile}:${target.specifier}:${target.exportName}`,
                target
            ])
        ).values()
    );
};

const lazyImports = collectLazyImports();
let viteServer: ViteDevServer;

const loadAstroViteConfig = async (): Promise<InlineConfig> => {
    const astroConfigPath = pathToFileURL(path.resolve(process.cwd(), 'astro.config.mjs')).href;
    const astroConfigModule = await import(astroConfigPath);
    const astroViteConfig = astroConfigModule.default?.vite ?? {};

    return {
        root: process.cwd(),
        configFile: false,
        appType: 'custom',
        plugins: [react()],
        resolve: {
            alias: {
                '@': srcRoot
            }
        },
        define: astroViteConfig.define,
        ssr: astroViteConfig.ssr,
        server: {
            middlewareMode: true
        }
    };
};

describe('lazy-loaded module integrity', () => {
    beforeAll(async () => {
        viteServer = await createServer(await loadAstroViteConfig());
    }, 60_000);

    afterAll(async () => {
        await viteServer?.close();
    });

    it.each(lazyImports)(
        'resolves %s from %s through the Vite module loader',
        async ({ sourceFile, specifier, viteSpecifier, exportName }) => {
            const module = await viteServer.ssrLoadModule(viteSpecifier);
            const exportedValue = module[exportName as keyof typeof module];

            expect(
                exportedValue,
                `${sourceFile} lazy import ${specifier} expected export "${exportName}" to exist after Vite transformed ${viteSpecifier}`
            ).toBeDefined();
        }
    );
});
