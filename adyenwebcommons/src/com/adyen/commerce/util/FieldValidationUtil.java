package com.adyen.commerce.util;

import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

import java.util.List;

public class FieldValidationUtil {
    public static List<String> getFieldCodesFromValidation(Errors errors) {
        return errors.getFieldErrors().stream().map(FieldError::getField).toList();
    }
}
