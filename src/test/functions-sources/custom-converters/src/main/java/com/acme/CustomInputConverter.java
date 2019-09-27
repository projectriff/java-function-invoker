package com.acme;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeType;

public class CustomInputConverter extends AbstractMessageConverter {


    public CustomInputConverter() {
        super(MimeType.valueOf("custom/type"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return CustomInput.class.equals(clazz);
    }

    @Override
    protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object conversionHint) {
        return  ((CustomInput)message.getPayload()).data;
    }

    @Override
    protected Object convertToInternal(Object payload, MessageHeaders headers, Object conversionHint) {
        return super.convertToInternal(payload, headers, conversionHint);
    }
}
