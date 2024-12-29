package Model.CardsLists;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface CardsListFile {
    static List<String> readFile(String filePath) throws IOException {
        return Files.readAllLines(Paths.get(filePath));
    }

    /*public static List<String> readFilesInDirectory(String dirPath) throws IOException {
        List<String> allLines = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(dirPath))) {
            for (Path path : directoryStream) {
                if (!Files.isDirectory(path)) {
                    List<String> lines = Files.readAllLines(path);
                    allLines.addAll(lines);
                }
            }
        }
        return allLines;
    }*/

    public static Map<String, List<String>> readFilesInDirectory(String dirPath) throws IOException {
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
