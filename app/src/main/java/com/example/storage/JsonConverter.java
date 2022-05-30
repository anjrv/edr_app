package com.example.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Simple container class for JSON utils
 */
public class JsonConverter {

    /**
     * Converts serializable object to JSON string
     *
     * @param o the object to be converted to JSON
     * @return the JSON string representation of the object
     * @throws JsonProcessingException
     */
    public static String convert(Object o) throws JsonProcessingException {
        ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();

        return writer.writeValueAsString(o);
    }
}
