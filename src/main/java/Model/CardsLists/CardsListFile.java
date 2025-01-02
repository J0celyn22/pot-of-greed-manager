package Model.CardsLists;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface CardsListFile {
    /**
     * Reads the content of the file at the given path and returns it as a list
     * of strings, one for each line in the file.
     *
     * @param filePath the path to the file to read
     * @return a list of strings, one for each line in the file
     * @throws IOException if an error occurs while reading the file
     */
    static List<String> readFile(String filePath) throws IOException {
        return Files.readAllLines(Paths.get(filePath));
    }

    /**
     * Reads all files in the specified directory and returns a map where the keys
     * are the file names and the values are lists of strings representing the content
     * of each file.
     *
     * @param dirPath the path to the directory containing the files to read
     * @return a map with file names as keys and lists of strings (file contents) as values
     * @throws IOException if an I/O error occurs accessing the directory or reading a file
     */
    static Map<String, List<String>> readFilesInDirectory(String dirPath) throws IOException {
        Map<String, List<String>> filesContent = new HashMap<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(dirPath))) {
            for (Path path : directoryStream) {
                if (!Files.isDirectory(path)) {
                    List<String> lines = Files.readAllLines(path);
                    filesContent.put(path.getFileName().toString(), lines);
                }
            }
        }
        return filesContent;
    }
}
