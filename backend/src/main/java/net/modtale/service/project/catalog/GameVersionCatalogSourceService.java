package net.modtale.service.project.catalog;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.modtale.config.properties.AppGameVersionProperties;
import net.modtale.model.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

final class GameVersionCatalogSourceService {

    private static final Logger logger = LoggerFactory.getLogger(GameVersionCatalogSourceService.class);

    private final MongoTemplate mongoTemplate;
    private final AppGameVersionProperties gameVersionProperties;
    private final RestTemplate restTemplate;

    GameVersionCatalogSourceService(
            MongoTemplate mongoTemplate,
            AppGameVersionProperties gameVersionProperties,
            RestTemplate restTemplate
    ) {
        this.mongoTemplate = mongoTemplate;
        this.gameVersionProperties = gameVersionProperties;
        this.restTemplate = restTemplate;
    }

    GameVersionCatalogSource fetchCatalogSource() {
        List<String> release = fetchVersionsFromMetadata(gameVersionProperties.releaseUrl());
        List<String> preRelease = fetchVersionsFromMetadata(gameVersionProperties.preReleaseUrl());
        List<String> indexed = fetchIndexedGameVersions();
        return new GameVersionCatalogSource(release, preRelease, indexed);
    }

    private List<String> fetchIndexedGameVersions() {
        Query query = new Query(Criteria.where("deletedAt").is(null));
        List<String> distinct = mongoTemplate.findDistinct(query, "versions.gameVersions", Project.class, String.class);
        return distinct == null ? List.of() : distinct;
    }

    private List<String> fetchVersionsFromMetadata(String metadataUrl) {
        final String xml;
        try {
            xml = restTemplate.getForObject(metadataUrl, String.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Unable to download Maven metadata from " + metadataUrl, ex);
        }
        if (xml == null || xml.isBlank()) {
            return List.of();
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ex) {
            logger.debug("XML parser does not support one of the secure-processing features for {}", metadataUrl, ex);
        }

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            NodeList versionNodes = document.getElementsByTagName("version");
            List<String> versions = new ArrayList<>();
            for (int i = 0; i < versionNodes.getLength(); i++) {
                String version = versionNodes.item(i).getTextContent();
                if (version != null && !version.isBlank()) {
                    versions.add(version.trim());
                }
            }
            return versions;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Maven metadata XML from " + metadataUrl, e);
        }
    }

    record GameVersionCatalogSource(
            List<String> releaseVersions,
            List<String> preReleaseVersions,
            List<String> indexedVersions
    ) {}
}
