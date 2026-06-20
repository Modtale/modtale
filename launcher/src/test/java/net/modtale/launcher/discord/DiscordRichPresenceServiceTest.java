package net.modtale.launcher.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DiscordRichPresenceServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void launcherActivityDescribesLauncherUse() {
        ObjectNode activity = DiscordRichPresenceService.launcherActivity(mapper, 1234);

        assertEquals("Browsing Modtale", activity.get("details").asText());
        assertEquals("Managing Hytale mods", activity.get("state").asText());
        assertEquals(1234, activity.path("timestamps").path("start").asLong());
        assertEquals("https://modtale.net/assets/favicon.svg", activity.path("assets").path("large_image").asText());
        assertEquals("Modtale", activity.path("assets").path("large_text").asText());
        assertEquals("Open Modtale", activity.path("buttons").get(0).path("label").asText());
    }

    @Test
    void hytaleActivityUsesBuildLabelWhenAvailable() {
        ObjectNode activity = DiscordRichPresenceService.hytaleActivity(mapper, "0.1.0 - Build 42", 5678);

        assertEquals("Playing Hytale", activity.get("details").asText());
        assertEquals("0.1.0 - Build 42", activity.get("state").asText());
        assertEquals(5678, activity.path("timestamps").path("start").asLong());
    }

    @Test
    void hytaleActivityFallsBackWhenBuildIsUnset() {
        ObjectNode activity = DiscordRichPresenceService.hytaleActivity(mapper, "Unset", 5678);

        assertEquals("Launched from Modtale", activity.get("state").asText());
    }

    @Test
    void setActivityCommandIncludesPidActivityAndNonce() {
        ObjectNode activity = DiscordRichPresenceService.launcherActivity(mapper, 1234);
        ObjectNode command = DiscordRichPresenceService.setActivityCommand(mapper, 99, activity, "nonce-1");

        assertEquals("SET_ACTIVITY", command.get("cmd").asText());
        assertEquals(99, command.path("args").path("pid").asLong());
        assertEquals(activity, command.path("args").path("activity"));
        assertEquals("nonce-1", command.get("nonce").asText());
    }

    @Test
    void frameUsesDiscordLittleEndianHeader() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] frame = DiscordRichPresenceService.frame(1, payload);
        ByteBuffer header = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(1, header.getInt());
        assertEquals(payload.length, header.getInt());
        assertEquals('{', frame[8]);
        assertEquals('}', frame[9]);
    }
}
