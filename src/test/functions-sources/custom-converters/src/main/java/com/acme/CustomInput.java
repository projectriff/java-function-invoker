package com.acme;

import java.nio.charset.StandardCharsets;

public class CustomInput {

     String data;

    public CustomInput(byte[] in) {
        this.data = new String(in, StandardCharsets.UTF_8);
    }
}
