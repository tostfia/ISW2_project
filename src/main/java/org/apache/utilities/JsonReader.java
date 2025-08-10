package org.apache.utilities;
import org.apache.logging.CollectLogger;
import org.json.JSONObject;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JsonReader {
    private JsonReader() {}

    private static String read(Reader rd) throws IOException {
        StringBuilder sb= new StringBuilder();
        int c;
        while ((c = rd.read()) != -1) {
            sb.append((char) c);
        }
        return sb.toString();
    }
    public static JSONObject readJsonFromUrl(String url) throws IOException, URISyntaxException {
        InputStream input = new URI(url).toURL().openStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String jsonText = read(reader);
        return new JSONObject(jsonText);
    }
    public static JSONObject load(String path) {
        try{
            String jsonString= new String(Files.readAllBytes(Paths.get(path)));
            return new  JSONObject(jsonString);
        } catch (IOException e) {
            CollectLogger.getInstance().getLogger().severe(e.getMessage());
            return null;
        }
    }

}

