package net.modtale.service.admin.project;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ProjectMetadataRepair {
    private ProjectMetadataRepair() {}
    private static final Set<String> STRINGS = Set.of("title", "about", "description", "imageUrl", "bannerUrl",
            "repositoryUrl", "license", "hmWikiSlug");
    private static final Set<String> FLAGS = Set.of("customLicenseOpenSource", "allowModpacks", "allowComments",
            "hmWikiEnabled", "galleryCarouselEnabled");
    private static final Set<String> LISTS = Set.of("tags", "galleryImages");
    private static final Set<String> MAPS = Set.of("links", "galleryImageCaptions");
    public static void validate(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty() || metadata.size() > 17) throw invalid();
        long characters = 0;
        for (var entry : metadata.entrySet()) {
            String field = entry.getKey(); Object value = entry.getValue();
            if (field == null) throw invalid();
            if (STRINGS.contains(field)) {
                if (value != null && !(value instanceof String)) throw invalid();
                if (field.equals("title") && (!(value instanceof String text) || text.isBlank())) throw invalid();
                characters += textSize(value, field.equals("description") ? 100_000 : 4_096);
            } else if (FLAGS.contains(field)) {
                if (!(value instanceof Boolean)) throw invalid();
            } else if (LISTS.contains(field)) {
                if (!(value instanceof List<?> values) || values.size() > 1000) throw invalid();
                for (Object item : values) {
                    if (!(item instanceof String)) throw invalid();
                    characters += textSize(item, 4096);
                }
            } else if (MAPS.contains(field)) {
                if (!(value instanceof Map<?, ?> values) || values.size() > 1000) throw invalid();
                for (var pair : values.entrySet()) {
                    if (!(pair.getKey() instanceof String) || !(pair.getValue() instanceof String)) throw invalid();
                    characters += textSize(pair.getKey(), 4096) + textSize(pair.getValue(), 4096);
                }
            } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only editable project metadata is accepted. Use the dedicated workflow for versions, access, and review decisions.");
            if (characters > 200_000) throw invalid();
        }
    }
    private static int textSize(Object value, int maximum) {
        if (value == null) return 0;
        int size = ((String) value).length();
        if (size > maximum) throw invalid();
        return size;
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project metadata has invalid fields, types, or size.");
    }
}
