package net.modtale.service.storage;

/** Server-created project namespaces provide deletion authority for new media uploads. */
public final class ProjectMediaKeys {
    private ProjectMediaKeys() {}
    public static String prefix(String projectId, String kind) {
        if (projectId == null || !projectId.matches("[a-zA-Z0-9_-]{1,128}")
                || !("images".equals(kind) || "gallery".equals(kind))) throw new IllegalArgumentException("Invalid project media namespace");
        return "project-media/" + projectId + "/" + kind;
    }
    public static String ownedKey(String projectId, String location, String publicDomain) {
        if (projectId == null || !projectId.matches("[a-zA-Z0-9_-]{1,128}") || location == null) return null;
        String key = location;
        if (key.startsWith("/api/files/proxy/")) key = key.substring("/api/files/proxy/".length());
        else if (publicDomain != null && !publicDomain.isBlank()) {
            String base = publicDomain.replaceAll("/+$", "") + "/";
            if (key.startsWith(base)) key = key.substring(base.length());
        }
        String namespace = "project-media/" + projectId + "/";
        if (!key.startsWith(namespace)) return null;
        String leaf = key.substring(namespace.length());
        return leaf.matches("(images|gallery)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-[a-zA-Z0-9._-]+") ? key : null;
    }
}
