package com.franco.dev.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada @Scheduled tiene que tener su hilo: con uno solo, una tarea trabada (SIFEN, pg_dump) frena
 * a todas las demas, incluida la limpieza que libera ventas colgadas en el PDV (#140).
 */
class SchedulerPoolTest {

    @Test
    void elPoolDelSchedulerAlcanzaParaTodasLasTareas() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        // Lee el bytecode sin cargar las clases: Class.forName dispararia los estaticos de jsifenlib/Drive.
        MetadataReaderFactory lector = new CachingMetadataReaderFactory(resolver);
        int tareas = 0;
        for (Resource clase : resolver.getResources("classpath*:com/franco/dev/**/*.class")) {
            AnnotationMetadata meta = lector.getMetadataReader(clase).getAnnotationMetadata();
            tareas += meta.getAnnotatedMethods(Scheduled.class.getName()).size()
                    + meta.getAnnotatedMethods(Schedules.class.getName()).size();
        }

        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        int pool = Integer.parseInt(props.getProperty("spring.task.scheduling.pool.size", "1").trim());

        // Piso del escaneo: si diera 0, el test pasaria con cualquier pool.
        assertEquals(4, tareas, "cambio la cantidad de @Scheduled: revisar spring.task.scheduling.pool.size");
        assertTrue(pool >= tareas, "spring.task.scheduling.pool.size=" + pool + " < " + tareas + " tareas @Scheduled");
    }
}
