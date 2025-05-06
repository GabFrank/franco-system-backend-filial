package com.franco.dev.utilitarios;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import org.hibernate.annotations.JoinColumnsOrFormulas;

import javax.persistence.JoinColumn;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

class CustomSerializer extends BeanSerializer {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public CustomSerializer(BeanSerializer serializer) {
        super(serializer);
    }


    @Override
    protected void serializeFields(Object bean, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (bean == null) {
            return;
        } else {
            Iterator<PropertyWriter> properties = this.properties();
            while (properties.hasNext()) {
                try {
                    BeanPropertyWriter property = (BeanPropertyWriter) properties.next();
                    if (property.getAnnotation(JoinColumn.class) != null || property.getAnnotation(JoinColumnsOrFormulas.class) != null) {
                        // property is annotated with @JoinColumn
                        Object entity = property.get(bean); //// get the value of the field
                        Field idField = entity.getClass().getDeclaredField("id") != null ? entity.getClass().getDeclaredField("id") : null; // get the "id" field
                        if (idField != null) {
                            idField.setAccessible(true); // make the field accessible
                            Object idValue = idField.get(entity); // get the value of the "id" field
                            gen.writeObjectFieldStart(property.getName());
                            gen.writeNumberField("id", (Long) idValue);
                        }
                        try {
                            Field sucField = entity.getClass().getDeclaredField("sucursalId") != null ? entity.getClass().getDeclaredField("sucursalId") : null; // get the "sucursalId" field
                            if (sucField != null) {
                                sucField.setAccessible(true); // make the field accessible
                                Object sucIdValue = sucField.get(entity); // get the value of the "id" field
                                gen.writeNumberField("sucursalId", (Long) sucIdValue);
                            }
                        } catch (Exception e) {

                        }
                        gen.writeEndObject();
                    } else if (property.get(bean) instanceof LocalDateTime) {
                        LocalDateTime value = (LocalDateTime) property.get(bean);
                        gen.writeStringField(property.getName(), value.format(FORMATTER));
                        property.serializeAsField(bean, gen, provider);
                    } else {
                        // not annotated with @JoinColumn, write as usual
                        property.serializeAsField(bean, gen, provider);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}


