package com.acme;

import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeType;

public class CustomConverter extends AbstractMessageConverter {


    protected CustomConverter() {
        super(MimeType.valueOf("custom/type"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return false;
    }
}
