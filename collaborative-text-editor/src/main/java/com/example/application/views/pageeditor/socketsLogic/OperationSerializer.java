package com.example.application.views.pageeditor.socketsLogic;
import com.example.application.views.pageeditor.CRDT.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class OperationSerializer {
    private static final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    public static String serialize(TextOperation operation) throws JsonProcessingException {
        return mapper.writeValueAsString(operation);
    }

    public static TextOperation deserialize(String json) throws JsonProcessingException {
        return mapper.readValue(json, TextOperation.class);
    }
}