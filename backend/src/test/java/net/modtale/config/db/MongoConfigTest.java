package net.modtale.config.db;

import java.util.Locale;
import net.modtale.model.user.OAuthProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoConfigTest {
    @Test
    void providerConversionDoesNotDependOnServerLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(OAuthProvider.DISCORD, new MongoConfig.StringToOAuthProviderConverter().convert("discord"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
