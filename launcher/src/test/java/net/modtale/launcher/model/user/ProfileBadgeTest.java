package net.modtale.launcher.model.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProfileBadgeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void creatorProfilesAcceptLegacyAndDataDrivenBadges() throws Exception {
        CreatorProfile profile = mapper.readValue("""
                {
                  "id":"creator-1",
                  "username":"Creator",
                  "badges":[
                    "VERIFIED",
                    {"id":"jam-winner","label":"Jam Winner","tooltip":"Winner of the 2026 jam","imageUrl":"/badges/jam.png","darkImageUrl":"/badges/jam-dark.png"}
                  ]
                }
                """, CreatorProfile.class);

        assertEquals(2, profile.badges().size());
        assertTrue(profile.badges().getFirst().legacy());
        assertEquals("VERIFIED", profile.badges().getFirst().legacyType());
        assertEquals("Jam Winner", profile.badges().get(1).displayLabel());
        assertEquals("/badges/jam-dark.png", profile.badges().get(1).darkImageUrl());
    }
}
