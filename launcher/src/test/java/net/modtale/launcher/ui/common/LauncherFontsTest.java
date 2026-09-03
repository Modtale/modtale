package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.text.Font;
import org.junit.jupiter.api.Test;

class LauncherFontsTest {

    @Test
    void loadsEveryInterWeightUsedByTheWebTypographyScale() {
        LauncherFonts.load();

        Set<String> interFonts = Font.getFontNames().stream()
                .filter(name -> name.startsWith("Inter"))
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        assertTrue(interFonts.contains("inter regular"), interFonts::toString);
        assertTrue(interFonts.contains("inter medium"), interFonts::toString);
        assertTrue(interFonts.contains("inter semibold"), interFonts::toString);
        assertTrue(interFonts.contains("inter bold"), interFonts::toString);
        assertTrue(interFonts.contains("inter extrabold"), interFonts::toString);
        assertTrue(interFonts.contains("inter black"), interFonts::toString);

        assertEquals("Inter Medium", Font.font("Inter Medium", 16).getName());
        assertEquals("Inter SemiBold", Font.font("Inter SemiBold", 16).getName());
        assertEquals("Inter ExtraBold", Font.font("Inter ExtraBold", 16).getName());
        assertEquals("Inter Black", Font.font("Inter Black", 16).getName());
    }
}
