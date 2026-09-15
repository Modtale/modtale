export const editableProjectMetadata = [
    'title', 'about', 'description', 'imageUrl', 'bannerUrl', 'repositoryUrl', 'license', 'hmWikiSlug',
    'customLicenseOpenSource', 'allowModpacks', 'allowComments', 'hmWikiEnabled', 'galleryCarouselEnabled',
    'tags', 'galleryImages', 'links', 'galleryImageCaptions',
] as const;

export function projectMetadataForRepair(project: Partial<Record<typeof editableProjectMetadata[number], unknown>>): Record<string, unknown> {
    return Object.fromEntries(editableProjectMetadata
        .filter(field => project[field] !== undefined && project[field] !== null)
        .map(field => [field, project[field]]));
}
