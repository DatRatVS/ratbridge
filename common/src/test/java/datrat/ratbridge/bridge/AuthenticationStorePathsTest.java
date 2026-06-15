package datrat.ratbridge.bridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuthenticationStorePathsTest {
    @TempDir
    Path tempDir;

    @Test
    void storesAuthenticationUsersInWorldDataFolder() {
        assertEquals(
                tempDir.resolve("data").resolve("ratbridge").resolve("authentication-users.toml"),
                AuthenticationStorePaths.worldDataPath(tempDir)
        );
    }

    @Test
    void migratesLegacyConfigStoreOnlyWhenTargetDoesNotExist() throws Exception {
        Path legacy = tempDir.resolve("config").resolve("ratbridge").resolve("authentication-users.toml");
        Path target = AuthenticationStorePaths.worldDataPath(tempDir.resolve("world"));
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "legacy");

        assertTrue(AuthenticationStorePaths.migrateLegacyConfigPath(legacy, target));
        assertFalse(Files.exists(legacy));
        assertEquals("legacy", Files.readString(target));

        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "new legacy");
        assertFalse(AuthenticationStorePaths.migrateLegacyConfigPath(legacy, target));
        assertEquals("legacy", Files.readString(target));
        assertEquals("new legacy", Files.readString(legacy));
    }
}
