package software.spool.provisioning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** The Terraform of a descriptor, in Terraform's JSON syntax: the resources in one file and the variables in another. */
public record GeneratedTerraform(Map<String, Object> main, Map<String, Object> variables) {
    public static final String MAIN_FILE = "main.tf.json";
    public static final String VARIABLES_FILE = "variables.tf.json";
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** The content of each file by its name. A file with nothing to say is left out. */
    public Map<String, String> files() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(MAIN_FILE, json(main));
        if (!variables.isEmpty()) files.put(VARIABLES_FILE, json(variables));
        return files;
    }

    /** Writes the files into the folder, creating it when it is missing, and returns it. */
    public Path writeTo(Path folder) throws IOException {
        Files.createDirectories(folder);
        for (Map.Entry<String, String> file : files().entrySet()) {
            Files.writeString(folder.resolve(file.getKey()), file.getValue());
        }
        return folder;
    }

    private static String json(Map<String, Object> content) {
        try {
            return JSON.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
