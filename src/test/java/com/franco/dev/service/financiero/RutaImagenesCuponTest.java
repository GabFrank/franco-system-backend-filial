package com.franco.dev.service.financiero;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La captura guarda y la purga borra en la MISMA carpeta (#150).
 *
 * <p>Cada clase tenia su propio default para {@code frc.captura.ruta-imagenes} --{@code cupones}
 * y {@code capturas}--, la propiedad no se configura en ningun lado, y la purga descartaba toda
 * candidata por "fuera de la carpeta": la proteccion contra disco lleno no hacia nada.
 */
public class RutaImagenesCuponTest {

    private static String rutaImagenesDe(Class<?> clase) {
        for (Constructor<?> c : clase.getConstructors()) {
            for (Parameter p : c.getParameters()) {
                Value v = p.getAnnotation(Value.class);
                if (v != null && v.value().contains("frc.captura.ruta-imagenes")) return v.value();
            }
        }
        return fail(clase.getSimpleName() + " no inyecta frc.captura.ruta-imagenes");
    }

    @Test
    public void captura_y_purga_resuelven_la_misma_carpeta() {
        assertEquals(rutaImagenesDe(CapturaCuponService.class),
                rutaImagenesDe(PurgaImagenesCuponService.class));
    }

    @Test
    public void el_default_es_donde_ya_estan_las_fotos() {
        // Cambiarlo dejaria las fotos ya guardadas en cupones/ fuera del alcance de la purga.
        assertEquals("${frc.captura.ruta-imagenes:cupones}", rutaImagenesDe(CapturaCuponService.class));
    }
}
