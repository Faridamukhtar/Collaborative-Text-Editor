package com.example.application.views.pageeditor.socketsLogic;
import com.example.application.views.pageeditor.CRDT.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Utility class for serializing and deserializing text operations
 */
public class OperationSerializer {
    private static final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    /**
     * Serialize a text operation to JSON
     * @param operation The operation to serialize
     * @return The JSON string
     */
    public static String serialize(TextOperation operation) throws JsonProcessingException {
        return mapper.writeValueAsString(operation);
    }

    /**
     * Deserialize a JSON string to a text operation
     * @param json The JSON string
     * @return The text operation
     */
    public static TextOperation deserialize(String json) throws JsonProcessingException {
        return mapper.readValue(json, TextOperation.class);
    }
}
