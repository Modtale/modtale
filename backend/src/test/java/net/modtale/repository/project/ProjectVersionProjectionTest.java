package net.modtale.repository.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bson.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.repository.Query;

class ProjectVersionProjectionTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "findViewerDetailById", "findViewerDetailBySlug",
            "findViewerDetailsPayloadById", "findViewerDetailsPayloadBySlug",
            "findPublicDetailById", "findPublicDetailBySlug",
            "findPublicDetailsPayloadById", "findPublicDetailsPayloadBySlug",
            "findPublicVersionsById", "findPublicVersionsBySlug",
            "findViewerVersionsById", "findViewerVersionsBySlug"
    })
    void versionPayloadsRetainConfigInstallationMetadata(String method) throws Exception {
        Query query = ProjectRepository.class.getMethod(method, String.class).getAnnotation(Query.class);
        Document fields = Document.parse(query.fields());
        assertEquals(1, fields.getInteger("versions.manifestId"), "Config destinations need the manifest identity");
        assertEquals(1, fields.getInteger("versions.modpackConfigs"), "Bundled configs must survive version reads");
    }
}
