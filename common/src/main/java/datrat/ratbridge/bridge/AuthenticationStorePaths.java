package datrat.ratbridge.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AuthenticationStorePaths {
    public static final String FILE_NAME = "authentication-users.toml";

    private AuthenticationStorePaths() {
    }

    public static Path worldDataPath(Path worldRoot) {
        return worldRoot.resolve("data").resolve("ratbridge").resolve(FILE_NAME);
    }

    public static boolean migrateLegacyConfigPath(Path legacyPath, Path targetPath) throws IOException {
        if (Files.notExists(legacyPath) || Files.exists(targetPath)) {
            return false;
        }
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.move(legacyPath, targetPath);
        return true;
    }
}
