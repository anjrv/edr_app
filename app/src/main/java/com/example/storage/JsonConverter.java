package com.example.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class JsonConverter {
    public static String convert(Object o) throws JsonProcessingException {
        ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();

        return writer.writeValueAsString(o);
    }
}
