package net.modtale.launcher.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import javafx.application.Application;
import org.junit.jupiter.api.Test;

class LauncherProtocolRequestTest {

    @Test
    void parsesInstallListProtocolUrlFromRawArguments() {
        LauncherProtocolRequest request = LauncherProtocolRequest.from(parameters(
                List.of("modtale://install-list?listId=list-123&url=https%3A%2F%2Fmodtale.test%2Flists%2Flist-123"),
                List.of(),
                Map.of()
        ));

        assertTrue(request.hasInstallList());
        assertEquals("list-123", request.installListId());
    }

    @Test
    void namedListArgumentWinsForDevelopmentLaunches() {
        LauncherProtocolRequest request = LauncherProtocolRequest.from(parameters(
                List.of("modtale://install-list?listId=raw-list"),
                List.of(),
                Map.of("listId", "named-list")
        ));

        assertEquals("named-list", request.installListId());
    }

    @Test
    void ignoresOtherProtocolActions() {
        LauncherProtocolRequest request = LauncherProtocolRequest.from(parameters(
                List.of("modtale://install?projectId=project-1"),
                List.of(),
                Map.of()
        ));

        assertFalse(request.hasInstallList());
    }

    private static Application.Parameters parameters(
            List<String> raw,
            List<String> unnamed,
            Map<String, String> named
    ) {
        return new Application.Parameters() {
            @Override
            public List<String> getRaw() {
                return raw;
            }

            @Override
            public List<String> getUnnamed() {
                return unnamed;
            }

            @Override
            public Map<String, String> getNamed() {
                return named;
            }
        };
    }
}
