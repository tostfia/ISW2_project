package org.apache.utilities;
import org.json.JSONObject;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

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

}

