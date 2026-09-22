package net.modtale.launcher.hytale;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class HytaleAvatarClientTest {
    @Test void rendersLocalIconsWithoutAuthOrNetwork() throws Exception {
        var client = new HytaleAvatarClient(null, Runnable::run);
        String url = client.avatarUrl("PlayerOne").join();
        assertEquals("file", URI.create(url).getScheme());
        assertEquals(url, client.avatarUrl("PLAYERONE").join());
        assertNotEquals(url, client.avatarUrl("PlayerTwo").join());
        var icon = ImageIO.read(Path.of(URI.create(url)).toFile());
        assertEquals(128, icon.getWidth()); assertEquals(128, icon.getHeight());
        assertFalse(url.contains("PlayerOne"));
    }
    @Test void invalidNamesCannotBecomePaths() {
        for (String name : new String[]{"../secret", "", "x", "Player/Name"})
            assertThrows(IllegalArgumentException.class, () -> HytaleAvatarClient.usernameAvatarUrl(name));
    }
}
