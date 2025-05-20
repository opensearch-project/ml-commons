/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.util.Map;
import java.util.regex.Pattern;

import org.opensearch.action.ActionRequestValidationException;

public class Validator {
    public static final String SAFE_INPUT_DESCRIPTION = "can only contain letters, numbers, spaces, and basic punctuation (.,!?():@-_'/\")";
    // Regex allows letters, digits, spaces, hyphens, underscores, and dots.
    static final Pattern SAFE_INPUT_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s.,!?():@\\-_/'\"]*$");

    /**
     * Validates a map of fields to ensure that their values only contain allowed characters.
     * <p>
     * Allowed characters are: letters, digits, spaces, underscores (_), hyphens (-), dots (.), and colons (:).
     * If a value does not comply, a validation error is added.
     *
     * @param fields A map where the key is the field name (used for error messages) and the value is the text to validate.
     * @return An {@link ActionRequestValidationException} containing all validation errors, or {@code null} if all fields are valid.
     */
    public static ActionRequestValidationException validateFields(Map<String, FieldDescriptor> fields) {
        ActionRequestValidationException exception = null;

        for (Map.Entry<String, FieldDescriptor> entry : fields.entrySet()) {
            String key = entry.getKey();
            FieldDescriptor descriptor = entry.getValue();
            String value = descriptor.getValue();

            if (descriptor.isRequired()) {
                if (!isSafeText(value)) {
                    String reason = (value == null || value.isBlank()) ? "is required and cannot be null or blank" : SAFE_INPUT_DESCRIPTION;
                    exception = addValidationError(key + " " + reason, exception);
                }
            } else {
                if (value != null && !value.isBlank() && !matchesSafePattern(value)) {
                    exception = addValidationError(key + " " + SAFE_INPUT_DESCRIPTION, exception);
                }
            }
        }

        return exception;
    }

    /**
     * Checks if the input is safe (non-null, non-blank, matches safe character set).
     *
     * @param value The input string to validate
     * @return true if input is safe, false otherwise
     */
    public static boolean isSafeText(String value) {
        return value != null && !value.isBlank() && matchesSafePattern(value);
    }

    // Just checks pattern
    public static boolean matchesSafePattern(String value) {
        return SAFE_INPUT_PATTERN.matcher(value).matches();
    }
}
