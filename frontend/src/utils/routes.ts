export class SiteRoutes {
    static home() { return '/'; }
    static upload() { return '/upload'; }
    static admin() { return '/admin'; }
    static apiDocs() { return '/api-docs'; }
    static swaggerDocs() { return '/api-docs/swagger'; }
    static login(redirectTo?: string) {
        const params = new URLSearchParams();
        if (redirectTo) params.set('redirect', redirectTo);
        const query = params.toString();
        return `/login${query ? `?${query}` : ''}`;
    }

    static internalRedirect(redirectTo: string | null | undefined, fallback: string = '/'): string {
        if (!redirectTo) return fallback;
        if (!redirectTo.startsWith('/')) return fallback;
        if (redirectTo.startsWith('//')) return fallback;
        return redirectTo;
    }

    static browse(classification?: string) {
        if (classification === 'PLUGIN') return '/plugins';
        if (classification === 'MODPACK') return '/modpacks';
        if (classification === 'SAVE') return '/worlds';
        if (classification === 'ART') return '/art';
        if (classification === 'DATA') return '/data';
        return '/mods';
    }

    static project(project: { id: string; title: string; slug?: string; classification?: string } | null) {
        if (!project) return '/';
        const handle = this.projectHandle(project);
        return `/${this.getProjectPrefix(project.classification)}/${handle}`;
    }

    static projectDownload(project: any) { return `${this.project(project)}/download`; }
    static projectChangelog(project: any) { return `${this.project(project)}/changelog`; }
    static projectGallery(project: any) { return `${this.project(project)}/gallery`; }
    static projectWiki(project: any, path?: string) { return `${this.project(project)}/wiki${path ? `/${path}` : ''}`; }
    static projectEdit(project: any) { return `${this.project(project)}/edit`; }

    static creator(userId: string, username?: string) {
        if (!userId) return '/';
        const handle = username?.trim();
        if (!handle) return `/creator/${userId}`;
        return `/creator/${handle}`;
    }

    static dashboard() { return '/dashboard'; }
    static dashboardProfile() { return '/dashboard/profile'; }
    static dashboardProjects() { return '/dashboard/projects'; }
    static dashboardOrgs() { return '/dashboard/orgs'; }
    static dashboardAnalytics() { return '/dashboard/analytics'; }
    static dashboardFinance() { return '/dashboard/finance'; }
    static dashboardNotifications() { return '/dashboard/notifications'; }
    static dashboardDeveloper() { return '/dashboard/developer'; }
    static verify() { return '/verify'; }
    static resetPassword() { return '/reset-password'; }
    static mfa() { return '/mfa'; }

    static terms() { return '/terms'; }
    static privacy() { return '/privacy'; }
    static status() { return '/status'; }

    static getProjectPrefix(classification?: string) {
        if (classification === 'MODPACK') return 'modpack';
        if (classification === 'SAVE') return 'world';
        return 'mod';
    }

    static projectHandle(project: { id: string; title: string; slug?: string } | null) {
        if (!project) return '';
        const customSlug = project.slug?.trim();
        if (customSlug) return customSlug;
        return this.createSlug(project.title, project.id);
    }

    static matchesProjectRoute(project: { id: string; title: string; slug?: string } | null, routeKey?: string) {
        if (!project || !routeKey) return false;
        const normalizedRouteKey = routeKey.trim();
        if (!normalizedRouteKey) return false;

        const customSlug = project.slug?.trim();
        if (customSlug && normalizedRouteKey.toLowerCase() === customSlug.toLowerCase()) {
            return true;
        }

        const handle = this.projectHandle(project);
        if (handle && normalizedRouteKey.toLowerCase() === handle.toLowerCase()) {
            return true;
        }

        return this.extractId(normalizedRouteKey) === project.id;
    }

    static createSlug(title: string, id: string) {
        if (!title) return id;
        const slug = title
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/(^-|-$)+/g, '')
            .substring(0, 30);

        if (!slug) return id;
        if (/^\d+$/.test(slug)) return `${slug}-mod~${id}`;
        return `${slug}~${id}`;
    }

    static createHandle(label: string, id: string) {
        if (!label) return id;
        let normalized = label
            .toLowerCase()
            .replace(/[^a-z0-9_.-]+/g, '-')
            .replace(/(^-|-$)+/g, '')
            .substring(0, 30);

        if (normalized === id) return id;
        if (id) {
            const escapedId = id.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            normalized = normalized.replace(new RegExp(`(?:~|-)${escapedId}$`, 'i'), '');
        }
        if (!normalized) return id;
        return `${normalized}~${id}`;
    }

    static extractId(param: string | undefined) {
        if (!param) return '';
        const tildeIndex = param.lastIndexOf('~');
        if (tildeIndex >= 0 && tildeIndex < param.length - 1) {
            return param.slice(tildeIndex + 1);
        }
        const match = param.match(/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i);
        return match ? match[0] : param;
    }
}
