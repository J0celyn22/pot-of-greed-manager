package Model;

import Model.Database.DataBaseUpdate;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves and exposes all filesystem paths used by the application,
 * relative to the directory containing the running JAR.
 */
public final class FilePaths {
    public static final Path jarDir;
    public static final Path databaseDir;
    public static final String outputPath;
    public static final String outputPathLists;

    static {
        try {
            File jarFile = new File(
                    DataBaseUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            jarDir = jarFile.getParentFile().toPath();
            databaseDir = jarDir.resolve(Paths.get("..", "Database"));
            outputPath = jarDir.resolve(Paths.get("..", "Output")).toString() + File.separator;
            outputPathLists = outputPath + "Lists" + File.separator;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private FilePaths() {
    }
}