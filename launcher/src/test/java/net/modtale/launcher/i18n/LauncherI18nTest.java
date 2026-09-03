package net.modtale.launcher.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class LauncherI18nTest {

    @Test
    void normalizesBcp47AndLegacyLocaleTags() {
        assertEquals("pt-BR", LauncherI18n.normalize("pt_BR").toLanguageTag());
        assertEquals("en", LauncherI18n.normalize(" ").toLanguageTag());
    }

    @Test
    void unsupportedLocalesFallBackToEnglish() {
        LauncherI18n i18n = new LauncherI18n(LauncherI18n.get().supportedLocales());
        i18n.setLocale(Locale.JAPANESE);
        assertEquals("en", i18n.localeTag());
        assertEquals("Save Settings", i18n.text("action.saveSettings"));
    }

    @Test
    void formatsArgumentsAndPluralFormsWithTheActiveLocale() {
        LauncherI18n i18n = new LauncherI18n(LauncherI18n.get().supportedLocales());
        assertEquals("Cleared 1 cached launcher item.", i18n.plural("cache.cleared", 1));
        assertEquals("Cleared 2 cached launcher items.", i18n.plural("cache.cleared", 2));
        assertTrue(i18n.number(12_345).contains("12"));
    }

    @Test
    void makesMissingCatalogKeysVisibleDuringDevelopment() {
        assertEquals("!missing.key!", LauncherI18n.get().text("missing.key"));
    }

    @Test
    void everyRegisteredLocaleHasTheCompleteEnglishCatalog() {
        ResourceBundle english = ResourceBundle.getBundle("net.modtale.launcher.i18n.messages", Locale.ENGLISH);
        for (LauncherI18n.LocaleOption option : LauncherI18n.get().supportedLocales()) {
            ResourceBundle candidate = ResourceBundle.getBundle("net.modtale.launcher.i18n.messages", option.locale());
            assertTrue(candidate.keySet().containsAll(english.keySet()), option.locale().toLanguageTag());
        }
    }
}
