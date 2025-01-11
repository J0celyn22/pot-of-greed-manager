package Model;

import Model.Database.DataBaseUpdate;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class FilePaths {
    public static final Path jarDir;
    public static final Path databaseDir;
    private static final File jarFile;
    public static final String outputPath;// = ".\\Output\\";
    public static final String outputPathLists;// = outputPath + "Lists\\";

    static {
        try {
            jarFile = new File(DataBaseUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            jarDir = jarFile.getParentFile().toPath();
            databaseDir = jarDir.resolve(Paths.get("..", "Database"));
            outputPath = jarDir.resolve(Paths.get("..", "Output")) + "\\";
            outputPathLists = outputPath + "Lists\\";
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private FilePaths() {
    }
}
