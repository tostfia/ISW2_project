package org.apache.utilities;
import org.apache.logging.Printer;
import org.json.JSONObject;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JsonReader {
    private JsonReader() {}

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb= new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }
    public static JSONObject readJsonFromUrl(String url) throws IOException, URISyntaxException {
        InputStream input = new URI(url).toURL().openStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String jsonText = readAll(reader);
        return new JSONObject(jsonText);
    }
    public static JSONObject load(String path) {
        try{
            String jsonString= new String(Files.readAllBytes(Paths.get(path)));
            return new  JSONObject(jsonString);
        } catch (IOException e) {
            Printer.errorPrint(e.getMessage());
            return null;
        }
    }

}

