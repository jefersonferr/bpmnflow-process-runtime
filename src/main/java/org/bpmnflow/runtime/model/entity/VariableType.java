package org.bpmnflow.runtime.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Supported types for process instance variables.
 * Each type knows how to validate and convert its value stored as VARCHAR2.
 */
public enum VariableType {

    STRING {
        @Override
        public void validate(String value) {
            // any string is valid
        }

        @Override
        public Object convert(String value) {
            return value;
        }
    },

    INTEGER {
        @Override
        public void validate(String value) {
            if (value == null) return;
            try {
                Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for type INTEGER: \"" + value + "\"");
            }
        }

        @Override
        public Object convert(String value) {
            return value == null ? null : Long.parseLong(value.trim());
        }
    },

    FLOAT {
        @Override
        public void validate(String value) {
            if (value == null) return;
            try {
                Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid value for type FLOAT: \"" + value + "\"");
            }
        }

        @Override
        public Object convert(String value) {
            return value == null ? null : Double.parseDouble(value.trim());
        }
    },

    BOOLEAN {
        private static final java.util.Set<String> TRUE_VALUES =
                java.util.Set.of("true", "1", "yes");
        private static final java.util.Set<String> FALSE_VALUES =
                java.util.Set.of("false", "0", "no");

        @Override
        public void validate(String value) {
            if (value == null) return;
            String lower = value.trim().toLowerCase();
            if (!TRUE_VALUES.contains(lower) && !FALSE_VALUES.contains(lower)) {
                throw new IllegalArgumentException(
                        "Invalid value for type BOOLEAN: \"" + value +
                                "\". Use: true/false, 1/0, yes/no");
            }
        }

        @Override
        public Object convert(String value) {
            if (value == null) return null;
            return TRUE_VALUES.contains(value.trim().toLowerCase());
        }
    },

    DATE {
        private static final DateTimeFormatter FORMATTER =
                DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

        @Override
        public void validate(String value) {
            if (value == null) return;
            try {
                LocalDate.parse(value.trim(), FORMATTER);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid value for type DATE: \"" + value +
                                "\". Expected format: yyyy-MM-dd");
            }
        }

        @Override
        public Object convert(String value) {
            return value == null ? null : LocalDate.parse(value.trim(), FORMATTER);
        }
    },

    JSON {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public void validate(String value) {
            if (value == null) return;
            try {
                MAPPER.readTree(value);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid value for type JSON: " + e.getMessage());
            }
        }

        @Override
        public Object convert(String value) {
            if (value == null) return null;
            try {
                return MAPPER.readTree(value);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to convert JSON: " + e.getMessage(), e);
            }
        }
    };

    /**
     * Validates whether {@code value} is compatible with this type.
     * Throws {@link IllegalArgumentException} if invalid.
     */
    public abstract void validate(String value);

    /**
     * Converts {@code value} to the corresponding Java type.
     * STRING → String, INTEGER → Long, FLOAT → Double,
     * BOOLEAN → Boolean, DATE → LocalDate, JSON → JsonNode.
     */
    public abstract Object convert(String value);
}
