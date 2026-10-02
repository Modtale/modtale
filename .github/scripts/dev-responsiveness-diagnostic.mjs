import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { performance } from 'node:perf_hooks';
import { checkFrontendCache, validateHtmlResponse } from './check-frontend-cache.mjs';

export const ORIGIN = 'https://dev.modtale.net';
export const MAX_DOCUMENT_READS = 18;
export const MAX_BROWSER_REQUESTS_PER_PAGE = 150;
export const MAX_LIGHTHOUSE_PAGES = 4;
export const MISSING_PATH = '/mod/dev-diagnostic-deliberately-missing-fixture';
const SLUG = '[A-Za-z0-9][A-Za-z0-9-]{0,127}';
const FIXTURES = [
  ['projectPath', 'DIAGNOSTIC_PROJECT_PATH', new RegExp(`^/(mod|modpack|world)/${SLUG}$`)],
  ['wikiPath', 'DIAGNOSTIC_WIKI_PATH', new RegExp(`^/(mod|modpack|world)/${SLUG}/wiki(?:/${SLUG}){0,6}$`)],
  ['newsPath', 'DIAGNOSTIC_NEWS_PATH', new RegExp(`^/news/${SLUG}$`)],
];
const BASE_PATHS = ['/', '/mods', '/mods?q=cache-smoke', '/mods?page=1', '/terms', '/privacy', '/launcher', '/news', '/login', '/dashboard', '/reset-password?token=cache-smoke-sentinel', MISSING_PATH];
export const LIGHTHOUSE_FLAGS = Object.freeze({
  output: ['json'], logLevel: 'error',
  maxWaitForLoad: 30000, maxWaitForFcp: 30000,
  disableStorageReset: true, disableFullPageScreenshot: true, usePassiveGathering: true,
  // No BFCache navigation, input/paste audits, interactions, full-page resize,
  // CLI retries, PSI service, arbitrary config, or remote report upload.
  onlyAudits: ['metrics', 'first-contentful-paint', 'largest-contentful-paint',
    'total-blocking-time', 'cumulative-layout-shift', 'speed-index',
    'server-response-time', 'network-requests', 'errors-in-console',
    'document-title', 'meta-description', 'is-crawlable', 'http-status-code',
    'canonical', 'image-alt', 'hreflang'],
});

function assert(condition, message) {
  if (!condition) throw new Error(message);
}
export function validateInputs(env) {
  assert(['warm', 'cold'].includes(env.DIAGNOSTIC_PHASE), 'Phase must be warm or cold');
  assert(/^[a-f0-9]{40}$/.test(env.DIAGNOSTIC_EXPECTED_SHA || ''), 'Expected deployment SHA must be 40 lowercase hexadecimal characters');
  assert(!env.FRONTEND_PUBLIC_URL || env.FRONTEND_PUBLIC_URL === ORIGIN, 'Only dev.modtale.net may be diagnosed');
  const input = { phase: env.DIAGNOSTIC_PHASE, expectedSha: env.DIAGNOSTIC_EXPECTED_SHA };
  for (const [key, variable, pattern] of FIXTURES) {
    const value = env[variable] || '';
    assert(!value || (value.length <= 1024 && pattern.test(value)), `Invalid ${variable}: use an existing public fixture path without query, encoding, or traversal`);
    input[key] = value;
  }
  input.lighthousePaths = ['/', ...FIXTURES.map(([key]) => input[key]).filter(Boolean)];
  assert(input.lighthousePaths.length <= MAX_LIGHTHOUSE_PAGES, 'Too many Lighthouse pages');
  return input;
}

function header(headers, name) {
  if (typeof headers?.get === 'function') return headers.get(name);
  return Object.entries(headers || {}).find(([key]) => key.toLowerCase() === name)?.[1] ?? null;
}
export function challengeReason(url, headers, body = '') {
  if (/challenge/i.test(header(headers, 'cf-mitigated') || '')) return 'Cloudflare challenge response';
  if (/\/cdn-cgi\/challenge-platform\/|challenges\.cloudflare\.com\/turnstile\//i.test(String(url))) return 'Cloudflare challenge/Turnstile resource';
  if (/<title[^>]*>\s*(?:Just a moment|Attention Required)[^<]*<\/title>|cf-chl-(?:widget|managed|captcha)|\/cdn-cgi\/challenge-platform\//i.test(body)) return 'Challenge page markup';
  return null;
}
function stopped(message) {
  const error = new Error(message);
  error.diagnosticBlocked = true;
  return error;
}
export function browserRequestDecision({ url, method, mainDocument, headers = {} }, allowedPaths, count) {
  if (count > MAX_BROWSER_REQUESTS_PER_PAGE) return { stop: 'Browser request budget exceeded' };
  if (!['GET', 'HEAD'].includes(method)) return { block: 'Non-read request blocked by diagnostic' };
  if (header(headers, 'authorization')) return { block: 'Authenticated request blocked by diagnostic' };
  if (challengeReason(url, {})) return { stop: 'WAF challenge encountered; no bypass attempted' };
  if (mainDocument) {
    const target = new URL(url);
    if (target.origin !== ORIGIN || !allowedPaths.has(target.pathname + target.search)) return { stop: 'Unexpected document/auth/external navigation blocked' };
  }
  return { allow: true };
}
function htmlObservations(body) {
  return {
    title: body.match(/<title[^>]*>([^<]*)<\/title>/i)?.[1]?.trim() || null,
    mainPresent: /<main[\s>]/i.test(body),
    canonical: body.match(/<link[^>]*rel=["']canonical["'][^>]*href=["']([^"']*)/i)?.[1] || null,
    noindexObserved: /<meta[^>]*name=["']robots["'][^>]*content=["'][^"']*noindex/i.test(body),
  };
}
export function publicUrl(value) {
  try {
    const url = new URL(value);
    if (!['http:', 'https:'].includes(url.protocol)) return null;
    if (url.username || url.password) return '[credential URL omitted]';
    if ([...url.searchParams.keys()].some(key => /token|signature|x-amz-|x-goog-|policy|credential|expires/i.test(key))) return `${url.origin}/[signed or token URL omitted]`;
    url.search = '';
    url.hash = '';
    return url.href;
  } catch { return null; }
}
export function sanitizeReport(value) {
  if (Array.isArray(value)) return value.map(sanitizeReport);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value)
    .filter(([key]) => !/^(?:headers|requestHeaders|responseHeaders|requestBody|body|postData|cookies|authorization|cookie|set-cookie)$/i.test(key))
    .map(([key, child]) => [key, /^(?:url|requestedUrl|finalUrl|canonical)$/i.test(key) && typeof child === 'string' ? publicUrl(child) : sanitizeReport(child)]));
  if (typeof value === 'string') return value.replace(/(?:authorization|set-cookie|cookie)\s*[:=][^\n]*/gi, '[sensitive header omitted]')
    .replace(/https?:\/\/[^\s"'<>]+/g, match => publicUrl(match) || '[invalid URL omitted]');
  return value;
}
export function summarizeLighthouse(result, observedDocument = null) {
  const lhr = result.lhr;
  const audits = lhr.audits || {};
  const metric = name => Number.isFinite(audits[name]?.numericValue) ? audits[name].numericValue : null;
  const log = result.artifacts?.DevtoolsLog || result.artifacts?.devtoolsLogs?.['pageLoadError-defaultPass'];
  const requests = new Map((log || []).filter(e => e.method === 'Network.requestWillBeSent').map(e => [e.params.requestId, e.params.request?.url]));
  const consoleMessages = result.artifacts?.ConsoleMessages;
  return {
    requestedUrl: lhr.requestedUrl, finalUrl: lhr.finalDisplayedUrl || lhr.finalUrl,
    fetchTime: lhr.fetchTime, lighthouseVersion: lhr.lighthouseVersion,
    runtimeError: lhr.runtimeError ? { code: lhr.runtimeError.code || 'UNKNOWN' } : null,
    warningCount: lhr.runWarnings?.length || 0,
    metrics: { lcpMs: metric('largest-contentful-paint'), fcpMs: metric('first-contentful-paint'),
      totalBlockingMs: metric('total-blocking-time'), cls: metric('cumulative-layout-shift'),
      speedIndexMs: metric('speed-index'),
      navigationTtfbMs: audits.metrics?.details?.items?.[0]?.timeToFirstByte ?? null,
      initialServerResponseMs: metric('server-response-time') },
    document: observedDocument,
    // Console text can contain logged request bodies or credentials. Keep
    // counts/types/public locations, never arbitrary application log content.
    javascriptErrors: consoleMessages ? consoleMessages.filter(e => e.level === 'error' || e.eventType === 'exception').map(e => ({ eventType: e.eventType, level: e.level, url: e.url || null, lineNumber: e.lineNumber ?? null })) : null,
    consoleAuditErrorCount: audits['errors-in-console']?.details?.items?.length ?? null,
    networkErrors: log ? log.filter(e => e.method === 'Network.loadingFailed').map(e => ({ url: requests.get(e.params.requestId) || null, error: e.params.errorText, canceled: e.params.canceled || false })) : null,
    httpErrors: audits['network-requests']?.details?.items?.filter(e => e.statusCode >= 400).map(e => ({ url: e.url, status: e.statusCode, resourceType: e.resourceType })) ?? null,
    seoAudits: Object.fromEntries(['document-title', 'meta-description', 'is-crawlable', 'http-status-code', 'canonical', 'image-alt', 'hreflang'].map(name => [name, audits[name] ? { score: audits[name].score, displayMode: audits[name].scoreDisplayMode, explanation: audits[name].explanation || null } : { unavailable: true }])),
    measurementSettings: { formFactor: lhr.configSettings?.formFactor, throttlingMethod: lhr.configSettings?.throttlingMethod, disableStorageReset: lhr.configSettings?.disableStorageReset },
  };
}
export function diagnosticLighthouseConfig(SourceMaps) {
  class DiagnosticSourceMaps extends SourceMaps {
    async getArtifact() { return []; }
  }
  return { extends: 'lighthouse:default', artifacts: [{ id: 'SourceMaps', gatherer: DiagnosticSourceMaps }] };
}

export async function collectLighthouse(input, outputDirectory, toolRoot) {
  const tools = createRequire(path.join(toolRoot, 'package.json'));
  const cliPackage = tools.resolve('@lhci/cli/package.json');
  assert(JSON.parse(await fs.readFile(cliPackage, 'utf8')).version === '0.15.1', 'Lighthouse CI tooling must be pinned to 0.15.1');
  const requireCli = createRequire(cliPackage);
  const lighthousePackage = requireCli.resolve('lighthouse/package.json');
  assert(JSON.parse(await fs.readFile(lighthousePackage, 'utf8')).version === '12.6.1', 'Unexpected Lighthouse dependency version');
  const requireLighthouse = createRequire(lighthousePackage);
  const { default: lighthouse } = await import(pathToFileURL(requireCli.resolve('lighthouse')).href);
  const { default: puppeteer } = await import(pathToFileURL(requireLighthouse.resolve('puppeteer-core')).href);
  const { default: SourceMaps } = await import(pathToFileURL(path.join(path.dirname(lighthousePackage), 'core/gather/gatherers/source-maps.js')).href);
  const config = diagnosticLighthouseConfig(SourceMaps);
  const browser = await puppeteer.launch({ executablePath: '/usr/bin/google-chrome', headless: true,
    ignoreDefaultArgs: ['--disable-popup-blocking'] });
  const results = [];
  try {
    for (const [index, route] of input.lighthousePaths.entries()) {
      const page = await browser.newPage();
      let requestCount = 0;
      let blockReason = null;
      let observedDocument = null;
      const diagnosticBlocks = [];
      const stop = reason => {
        blockReason ||= reason;
        // Closing the owned page actually cancels navigation and traffic.
        void page.close().catch(() => {});
      };
      await page.setRequestInterception(true);
      page.on('request', request => {
        const decision = browserRequestDecision({ url: request.url(), method: request.method(), headers: request.headers(), mainDocument: request.isNavigationRequest() && request.frame() === page.mainFrame() }, new Set([route]), ++requestCount);
        if (decision.stop) { stop(decision.stop); void request.abort().catch(() => {}); }
        else if (decision.block) { diagnosticBlocks.push({ method: request.method(), url: request.url(), reason: decision.block }); void request.abort().catch(() => {}); }
        else void request.continue().catch(error => stop(`Request guard failed: ${error.message}`));
      });
      page.on('response', response => {
        const reason = challengeReason(response.url(), response.headers());
        if (reason) stop(reason);
        if (response.request().isNavigationRequest() && response.request().frame() === page.mainFrame()) {
          const headers = response.headers();
          observedDocument = { status: response.status(), cacheStatus: headers['cf-cache-status'] || null, age: headers.age || null, revision: headers['x-modtale-revision'] || null };
          if ([401, 403, 429, 503].includes(response.status())) stop(`Document HTTP ${response.status()}; responsiveness measurement invalid`);
          else if (response.status() === 200 && headers['x-modtale-revision'] !== input.expectedSha) stop('Browser document deployment SHA mismatch');
        }
      });
      page.on('dialog', () => stop('JavaScript dialog encountered; no response authorized'));
      page.on('popup', popup => { void popup.close().catch(() => {}); stop('Unexpected popup navigation blocked'); });
      let timer;
      let result;
      try {
        result = await Promise.race([
          lighthouse(ORIGIN + route, { ...LIGHTHOUSE_FLAGS }, config, page),
          new Promise((_, reject) => { timer = setTimeout(() => { stop('Lighthouse page deadline exceeded'); reject(stopped(blockReason)); }, 90000); }),
        ]);
        if (blockReason) throw stopped(blockReason);
        const markup = await page.content();
        const reason = challengeReason(ORIGIN + route, {}, markup);
        if (reason) throw stopped(reason);
        const observation = { ...observedDocument, ...htmlObservations(markup) };
        assert(observation.status === 200, `${route}: public fixture must return 200; observed ${observation.status ?? 'unavailable'}`);
        const report = sanitizeReport(summarizeLighthouse(result, observation));
        report.requestCount = requestCount;
        report.diagnosticBlockedRequests = sanitizeReport(diagnosticBlocks);
        report.errorCaveat = 'ERR_FAILED/ERR_BLOCKED_BY_CLIENT for listed diagnostic-blocked writes are not evidence of a production error';
        const prefix = `${index}-${index === 0 ? 'home' : 'public-fixture'}`;
        await fs.mkdir(path.join(outputDirectory, 'lighthouse'), { recursive: true });
        await fs.writeFile(path.join(outputDirectory, 'lighthouse', `${prefix}.metrics.json`), JSON.stringify(report, null, 2));
        results.push(report);
        assert(!report.runtimeError, `${route}: Lighthouse failed: ${report.runtimeError?.code}`);
      } catch (error) {
        const attempts = [...results, sanitizeReport({ requestedUrl: ORIGIN + route,
          status: blockReason ? 'blocked' : 'failed', validForPerformance: false,
          document: observedDocument, requestCount, metrics: null,
          diagnosticBlockedRequests: diagnosticBlocks, error: blockReason || error.message })];
        error.lighthouseResults = attempts;
        if (blockReason) throw Object.assign(stopped(blockReason), { lighthouseResults: attempts });
        throw error;
      } finally {
        clearTimeout(timer);
        await page.close().catch(() => {});
      }
    }
    return results;
  } finally {
    await browser.close();
  }
}

export async function runDiagnostic(input, { request = fetch, collect = collectLighthouse, now = () => performance.now(), outputDirectory = 'diagnostic-output', toolRoot = process.env.DIAGNOSTIC_TOOL_ROOT } = {}) {
  const allowedPaths = new Set([...BASE_PATHS, ...input.lighthousePaths]);
  const report = { origin: ORIGIN, phaseLabel: input.phase, expectedSha: input.expectedSha,
    sampledAt: new Date().toISOString(), status: 'running', firstDocumentProbe: null,
    documentReads: [], cacheSmoke: null, lighthouse: [], missingPage: null,
    authenticatedCrossUserTesting: 'unavailable: no authorized disposable accounts or credentials',
    dataReadyTiming: 'unavailable: no application-level ready signal measured',
    coldStartVerified: false,
    limitations: ['Warm/cold is an operator label; instance cold-start state is unknown',
      'First ordinary GET precedes all warming and browser navigation; it can itself warm the CDN/origin',
      'Lighthouse measures later fresh-browser mobile simulated navigation, not the first GET or real-user percentiles',
      'No cache-busting headers, purges, forced browser cache flushes, authentication, form submission or interactions',
      'Selected read-only audits only; non-GET/HEAD browser writes are blocked and may affect page behavior',
      'Source-map inspector lookups are disabled; error locations are generated public scripts, not mapped application source',
      'Request caps/write filtering cover owned-page HTTP requests; arbitrary worker/WebSocket traffic is not exercised or claimed as covered',
      'Dev noindex/robots observations may be intentional; an auth shell is not authenticated data coverage'] };
  const read = async (url, options = {}) => {
    const target = new URL(url);
    assert(target.origin === ORIGIN && allowedPaths.has(target.pathname + target.search), 'Diagnostic request outside fixed dev routes');
    assert(!options.method || options.method === 'GET', 'Only ordinary document GETs allowed');
    assert(!options.headers && !options.body, 'No custom headers, cookies, credentials, or body allowed');
    assert(report.documentReads.length < MAX_DOCUMENT_READS, 'Document request budget exceeded');
    const startedAt = new Date().toISOString();
    const start = now();
    const response = await request(target, { signal: AbortSignal.timeout(30000), redirect: 'manual' });
    const ttfbMs = now() - start;
    const body = await response.clone().text();
    const observation = { path: target.pathname + target.search, status: response.status, startedAt,
      ttfbMs: Math.round(ttfbMs), totalMs: Math.round(now() - start),
      cacheStatus: response.headers.get('cf-cache-status'), age: response.headers.get('age'),
      revision: response.headers.get('x-modtale-revision'),
      cacheControl: response.headers.get('cache-control'), cdnCacheControl: response.headers.get('cloudflare-cdn-cache-control') || response.headers.get('cdn-cache-control'),
      ...htmlObservations(body) };
    report.documentReads.push(observation);
    const reason = challengeReason(target, response.headers, body);
    if (reason) throw stopped(reason);
    if ([401, 403, 429, 503].includes(response.status)) throw stopped(`Document HTTP ${response.status}; no challenge bypass or retry`);
    return response;
  };
  const lighthouse = async () => { report.lighthouse = await collect(input, outputDirectory, toolRoot); };
  const smoke = async () => {
    const firstSmokeRead = report.documentReads.length;
    report.cacheSmoke = await checkFrontendCache({ FRONTEND_PUBLIC_URL: ORIGIN, GITHUB_SHA: input.expectedSha,
      CACHE_SMOKE_PROJECT_PATH: input.projectPath, CACHE_SMOKE_WIKI_PATH: input.wikiPath, CACHE_SMOKE_NEWS_PATH: input.newsPath }, { request: read, now });
    // The wrapper inspects the body before returning to the existing checker;
    // use our actual header/body timings instead of its wrapper-duration TTFB.
    for (const [index, item] of report.cacheSmoke.results.entries()) {
      const measured = report.documentReads[firstSmokeRead + index];
      assert(measured.path === item.path, 'Smoke measurement order changed');
      item.ttfbMs = measured.ttfbMs;
      item.totalMs = measured.totalMs;
    }
  };
  try {
    const first = await read(ORIGIN + '/');
    report.firstDocumentProbe = report.documentReads[0];
    validateHtmlResponse(first, { path: '/', cache: 'public', revision: input.expectedSha });
    if (input.phase === 'cold') { await lighthouse(); await smoke(); }
    else { await smoke(); await lighthouse(); }
    const missing = await read(ORIGIN + MISSING_PATH);
    report.missingPage = report.documentReads.at(-1);
    assert(missing.status === 404, 'Deliberately missing public path must return 404');
    assert(/no-store|private|no-cache|max-age=0/i.test(missing.headers.get('cloudflare-cdn-cache-control') || missing.headers.get('cdn-cache-control') || ''), '404 must not opt into shared HTML cache');
    report.status = 'complete';
  } catch (error) {
    report.status = error.diagnosticBlocked ? 'blocked' : 'failed';
    report.error = error.message;
    if (error.lighthouseResults) report.lighthouse = error.lighthouseResults;
  }
  return sanitizeReport(report);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const input = validateInputs(process.env);
  if (process.argv[2] === 'validate') console.log(JSON.stringify(input, null, 2));
  else {
    assert(!process.argv[2], 'No arbitrary commands accepted');
    const outputDirectory = 'diagnostic-output';
    await fs.mkdir(outputDirectory, { recursive: true });
    const report = await runDiagnostic(input, { outputDirectory });
    await fs.writeFile(path.join(outputDirectory, 'report.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
    process.exitCode = report.status === 'complete' ? 0 : 1;
  }
}
