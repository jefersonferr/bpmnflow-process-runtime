package org.bpmnflow.runtime.model.entity;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("VariableType — validate and convert")
class VariableTypeTest {

    // ---------------------------------------------------------------
    // STRING
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("STRING")
    class StringType {

        @Test
        @DisplayName("accepts any non-null value")
        void acceptsAnyString() {
            assertThatNoException().isThrownBy(() -> VariableType.STRING.validate("hello"));
            assertThatNoException().isThrownBy(() -> VariableType.STRING.validate(""));
            assertThatNoException().isThrownBy(() -> VariableType.STRING.validate("123"));
        }

        @Test
        @DisplayName("accepts null without throwing")
        void acceptsNull() {
            assertThatNoException().isThrownBy(() -> VariableType.STRING.validate(null));
        }

        @Test
        @DisplayName("convert returns value as-is")
        void convertReturnsAsIs() {
            assertThat(VariableType.STRING.convert("hello")).isEqualTo("hello");
            assertThat(VariableType.STRING.convert(null)).isNull();
        }
    }

    // ---------------------------------------------------------------
    // INTEGER
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("INTEGER")
    class IntegerType {

        @Test
        @DisplayName("accepts valid long values")
        void acceptsValidLong() {
            assertThatNoException().isThrownBy(() -> VariableType.INTEGER.validate("42"));
            assertThatNoException().isThrownBy(() -> VariableType.INTEGER.validate("-100"));
            assertThatNoException().isThrownBy(() -> VariableType.INTEGER.validate("  7  "));
        }

        @Test
        @DisplayName("accepts null without throwing")
        void acceptsNull() {
            assertThatNoException().isThrownBy(() -> VariableType.INTEGER.validate(null));
        }

        @Test
        @DisplayName("rejects non-numeric value")
        void rejectsNonNumeric() {
            assertThatThrownBy(() -> VariableType.INTEGER.validate("abc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INTEGER");
        }

        @Test
        @DisplayName("rejects decimal value")
        void rejectsDecimal() {
            assertThatThrownBy(() -> VariableType.INTEGER.validate("3.14"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INTEGER");
        }

        @Test
        @DisplayName("convert returns Long")
        void convertReturnsLong() {
            assertThat(VariableType.INTEGER.convert("42")).isEqualTo(42L);
            assertThat(VariableType.INTEGER.convert("-10")).isEqualTo(-10L);
        }

        @Test
        @DisplayName("convert returns null for null input")
        void convertNullReturnsNull() {
            assertThat(VariableType.INTEGER.convert(null)).isNull();
        }
    }

    // ---------------------------------------------------------------
    // FLOAT
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("FLOAT")
    class FloatType {

        @Test
        @DisplayName("accepts valid decimal values")
        void acceptsValidDecimal() {
            assertThatNoException().isThrownBy(() -> VariableType.FLOAT.validate("3.14"));
            assertThatNoException().isThrownBy(() -> VariableType.FLOAT.validate("42"));
            assertThatNoException().isThrownBy(() -> VariableType.FLOAT.validate("-0.5"));
            assertThatNoException().isThrownBy(() -> VariableType.FLOAT.validate("  1.0  "));
        }

        @Test
        @DisplayName("accepts null without throwing")
        void acceptsNull() {
            assertThatNoException().isThrownBy(() -> VariableType.FLOAT.validate(null));
        }

        @Test
        @DisplayName("rejects non-numeric value")
        void rejectsNonNumeric() {
            assertThatThrownBy(() -> VariableType.FLOAT.validate("not-a-number"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("FLOAT");
        }

        @Test
        @DisplayName("convert returns Double")
        void convertReturnsDouble() {
            assertThat(VariableType.FLOAT.convert("3.14")).isEqualTo(3.14);
            assertThat(VariableType.FLOAT.convert("42")).isEqualTo(42.0);
        }

        @Test
        @DisplayName("convert returns null for null input")
        void convertNullReturnsNull() {
            assertThat(VariableType.FLOAT.convert(null)).isNull();
        }
    }

    // ---------------------------------------------------------------
    // BOOLEAN
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("BOOLEAN")
    class BooleanType {

        @Test
        @DisplayName("accepts all true variants")
        void acceptsTrueVariants() {
            for (String v : new String[]{"true", "1", "yes", "TRUE", "YES"}) {
                assertThatNoException()
                        .as("should accept: " + v)
                        .isThrownBy(() -> VariableType.BOOLEAN.validate(v));
            }
        }

        @Test
        @DisplayName("accepts all false variants")
        void acceptsFalseVariants() {
            for (String v : new String[]{"false", "0", "no", "FALSE", "NO"}) {
                assertThatNoException()
                        .as("should accept: " + v)
                        .isThrownBy(() -> VariableType.BOOLEAN.validate(v));
            }
        }

        @Test
        @DisplayName("accepts null without throwing")
        void acceptsNull() {
            assertThatNoException().isThrownBy(() -> VariableType.BOOLEAN.validate(null));
        }

        @Test
        @DisplayName("rejects invalid value")
        void rejectsInvalidValue() {
            assertThatThrownBy(() -> VariableType.BOOLEAN.validate("maybe"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BOOLEAN");
        }

        @Test
        @DisplayName("convert returns true for true variants")
        void convertTrueVariants() {
            assertThat(VariableType.BOOLEAN.convert("true")).isEqualTo(true);
            assertThat(VariableType.BOOLEAN.convert("1")).isEqualTo(true);
            assertThat(VariableType.BOOLEAN.convert("YES")).isEqualTo(true);
        }

        @Test
        @DisplayName("convert returns false for false variants")
        void convertFalseVariants() {
            assertThat(VariableType.BOOLEAN.convert("false")).isEqualTo(false);
            assertThat(VariableType.BOOLEAN.convert("0")).isEqualTo(false);
            assertThat(VariableType.BOOLEAN.convert("no")).isEqualTo(false);
        }

        @Test
        @DisplayName("convert returns null for null input")
        void convertNullReturnsNull() {
            assertThat(VariableType.BOOLEAN.convert(null)).isNull();
        }
    }

    // ---------------------------------------------------------------
    // DATE
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("DATE")
    class DateType {

        @Test
        @DisplayName("accepts valid yyyy-MM-dd dates")
        void acceptsValidDate() {
            assertThatNoException().isThrownBy(() -> VariableType.DATE.validate("2025-12-31"));
            assertThatNoException().isThrownBy(() -> VariableType.DATE.validate("2000-01-01"));
            assertThatNoException().isThrownBy(() -> VariableType.DATE.validate("  2024-06-15  "));
        }

        @Test
        @DisplayName("accepts null without throwing")
        void acceptsNull() {
            assertThatNoException().isThrownBy(() -> VariableType.DATE.validate(null));
        }

        @Test
        @DisplayName("rejects dd/MM/yyyy format")
        void rejectsBrazilianFormat() {
            assertThatThrownBy(() -> VariableType.DATE.validate("31/12/2025"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DATE")
                    .hasMessageContaining("yyyy-MM-dd");
        }

        @Test
        @DisplayName("rejects free-text date")
        void rejectsFreeTextDate() {
            assertThatThrownBy(() -> VariableType.DATE.validate("December 31, 2025"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DATE");
        }

        @Test
        @DisplayName("convert returns LocalDate")
        void convertReturnsLocalDate() {
            Object result = VariableType.DATE.convert("2025-12-31");
            assertThat(result).isInstanceOf(LocalDate.class);
            assertThat((LocalDate) result).isEqualTo(LocalDate.of(2025, 12, 31));
        }

        @Test
        @DisplayName("convert returns null for null input")
        void convertNullReturnsNull() {
            assertThat(VariableType.DATE.convert(null)).isNull();
        }
    }

    // ---------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("JSON")
    class JsonType {

        @Test
        @DisplayName("accepts valid JSON object")
        void acceptsJsonObject() {
            assertThatNoException().isThrownBy(() -> VariableType.JSON.validate("{\"key\":\"value\"}"));
        }

        @Test
        @DisplayName("accepts valid JSON array")
        void acceptsJsonArray() {
            assertThatNoException().isThrownBy(() -> VariableType.JSON.validate("[1, 2, 3]"));
        }

        @Test
        @DisplayName("accepts JSON boolean and number primitives")
        void acceptsJsonPrimitives() {
            assertThatNoException().isThrownBy(() -> VariableType.JSON.validate("true"));
            assertThatNoException().isThrownBy(() -> VariableType.JSON.validate("42"));
        }

        @Test
        @DisplayName("accepts null without throwing")
        void acceptsNull() {
            assertThatNoException().isThrownBy(() -> VariableType.JSON.validate(null));
        }

        @Test
        @DisplayName("rejects malformed JSON")
        void rejectsMalformedJson() {
            assertThatThrownBy(() -> VariableType.JSON.validate("{key without quotes}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("JSON");
        }

        @Test
        @DisplayName("convert returns JsonNode for valid JSON")
        void convertReturnsJsonNode() {
            Object result = VariableType.JSON.convert("{\"ok\":true}");
            assertThat(result).isNotNull();
            assertThat(result.toString()).contains("true");
        }

        @Test
        @DisplayName("convert returns JsonNode for JSON boolean")
        void convertReturnsJsonBoolean() {
            Object result = VariableType.JSON.convert("true");
            assertThat(result).isInstanceOf(BooleanNode.class);
        }

        @Test
        @DisplayName("convert returns JsonNode for JSON number")
        void convertReturnsJsonNumber() {
            Object result = VariableType.JSON.convert("99");
            assertThat(result).isInstanceOf(IntNode.class);
        }

        @Test
        @DisplayName("convert returns null for null input")
        void convertNullReturnsNull() {
            assertThat(VariableType.JSON.convert(null)).isNull();
        }
    }
}
