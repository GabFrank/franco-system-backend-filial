package com.franco.dev.utilitarios;

import com.franco.dev.rabbit.enums.TipoEntidad;

public class EnumUtils {

    public static TipoEntidad getTipoEntidadByClassFullName(String entidad) {
        String enumValue = entidad.substring(entidad.lastIndexOf(".") + 1);
        enumValue = enumValue.toUpperCase().replaceAll("[^A-Za-z]", "_");
        try {
            return TipoEntidad.valueOf(enumValue);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
