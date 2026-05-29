export class SiteRoutes {
    static home() { return '/'; }
    static upload() { return '/upload'; }
    static admin() { return '/admin'; }
    static apiDocs() { return '/api-docs'; }
    static swaggerDocs() { return '/api-docs/swagger'; }

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
        const generatedSlug = this.createSlug(project.title, project.id);
        const finalSlug = (project.slug && project.slug.trim().length > 0) ? project.slug : generatedSlug;
        return `/${this.getProjectPrefix(project.classification)}/${finalSlug}`;
    }

    static projectDownload(project: any) { return `${this.project(project)}/download`; }
    static projectChangelog(project: any) { return `${this.project(project)}/changelog`; }
    static projectGallery(project: any) { return `${this.project(project)}/gallery`; }
    static projectWiki(project: any, path?: string) { return `${this.project(project)}/wiki${path ? `/${path}` : ''}`; }
    static projectEdit(project: any) { return `${this.project(project)}/edit`; }

    static creator(username: string) {
        if (!username) return '/';
        return `/creator/${username}`;
    }

    static dashboard() { return '/dashboard'; }
    static dashboardProfile() { return '/dashboard/profile'; }
    static dashboardProjects() { return '/dashboard/projects'; }
    static dashboardOrgs() { return '/dashboard/orgs'; }
    static dashboardAnalytics() { return '/dashboard/analytics'; }
    static dashboardNotifications() { return '/dashboard/notifications'; }
    static dashboardDeveloper() { return '/dashboard/developer'; }

    static login() { return '/login'; }
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

    static createSlug(title: string, id: string) {
        if (!title) return id;
        const slug = title
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/(^-|-$)+/g, '')
            .substring(0, 30);

        if (!slug) return id;
        if (/^\d+$/.test(slug)) return `${slug}-mod-${id}`;
        return `${slug}-${id}`;
    }

    static extractId(param: string | undefined) {
        if (!param) return '';
        const match = param.match(/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i);
        return match ? match[0] : param;
    }
}
