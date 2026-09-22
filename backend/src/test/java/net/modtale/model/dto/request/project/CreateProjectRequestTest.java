package net.modtale.model.dto.request.project;

import jakarta.validation.Validation;
import net.modtale.model.project.ProjectClassification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateProjectRequestTest {
    @Test
    void acceptsOptionalImportFieldsAndRejectsUntrustedSources() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new CreateProjectRequest();
            request.setTitle("My Mod");
            request.setClassification(ProjectClassification.PLUGIN);
            assertTrue(validator.validate(request).isEmpty());

            request.setAbout("<h2>Features</h2>");
            request.setCurseForgeUrl("https://www.curseforge.com/hytale/mods/my-mod");
            request.setImageUrl("https://media.forgecdn.net/avatars/1/icon.png");
            assertTrue(validator.validate(request).isEmpty());

            request.setCurseForgeUrl("https://www.curseforge.com/minecraft/mc-mods/my-mod");
            request.setImageUrl("https://media.forgecdn.net.evil.test/icon.png");
            request.setAbout("x".repeat(50001));
            var invalidFields = validator.validate(request).stream()
                    .map(violation -> violation.getPropertyPath().toString()).toList();
            assertTrue(invalidFields.containsAll(java.util.List.of("curseForgeUrl", "imageUrl", "about")));
        }
    }
}
