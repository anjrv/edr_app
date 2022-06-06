package com.example.storage.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Simple container class for JSON utils
 */
public class JsonConverter {
    private static final ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();

    /**
     * Converts serializable object to JSON string
     *
     * @param o the object to be converted to JSON
     * @return the JSON string representation of the object
     * @throws JsonProcessingException error object for write failure
     */
    public static String convert(Object o) throws JsonProcessingException {
        return writer.writeValueAsString(o);
    }
}
