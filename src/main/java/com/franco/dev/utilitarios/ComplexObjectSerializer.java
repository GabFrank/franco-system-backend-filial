//package com.franco.dev.utilitarios;
//
//import com.fasterxml.jackson.core.JsonGenerator;
//import com.fasterxml.jackson.databind.JsonSerializer;
//import com.fasterxml.jackson.databind.SerializerProvider;
//
//import java.io.IOException;
//
//public class ComplexObjectSerializer<T> extends JsonSerializer<T> {
//    @Override
//    public void serialize(T value, JsonGenerator gen, SerializerProvider serializers) {
//            try {
//                    gen.writeStartObject();
//                    gen.writeNumberField("id", value.x);
//                    gen.writeEndObject();
//            } catch (IOException e) {
//                    e.printStackTrace();
//            }
//
//    }
//}
