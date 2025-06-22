package tsystems.janus.sourcecodeconverter.infrastructure.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class JsonHelper {

    public static boolean isValidJson(String json) {
        try {
            new ObjectMapper().readTree(json);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static <T> T parseJson(String json, Class<T> valueType) {
        try {
            return new ObjectMapper().readValue(json, valueType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }

    public static <T> List<T> parseJsonArray(String json, JavaType valueType) {
        try {
            return new ObjectMapper().readValue(json, valueType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JSON array: " + json, e);
        }
    }
}
