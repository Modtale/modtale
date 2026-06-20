package net.modtale.launcher.api;

public record ProjectSearchQuery(
        String search,
        String classification,
        String gameVersion,
        String sort,
        int page,
        int size,
        String tags,
        Integer minDownloads,
        Integer minFavorites,
        String category,
        String dateRange,
        Boolean openSource
) {
    private static final String DEFAULT_SORT = "relevance";

    public ProjectSearchQuery {
        sort = sort == null || sort.isBlank() ? DEFAULT_SORT : sort;
        page = Math.max(0, page);
        size = Math.max(1, Math.min(100, size));
        minDownloads = minDownloads == null || minDownloads <= 0 ? null : minDownloads;
        minFavorites = minFavorites == null || minFavorites <= 0 ? null : minFavorites;
        dateRange = dateRange == null || dateRange.isBlank() ? null : dateRange;
        openSource = Boolean.TRUE.equals(openSource) ? Boolean.TRUE : null;
    }
}
