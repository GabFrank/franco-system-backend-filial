package com.franco.dev.service.financiero.ocr;

import ai.onnxruntime.OrtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lee un cupon de tarjeta desde la foto que sube el cajero.
 *
 * PP-OCRv4 sobre ONNX Runtime, dentro de esta misma JVM: no hay servicio Python
 * al lado, ni binarios que provisionar en el disco de la filial. Los modelos y
 * el diccionario viajan como recursos del classpath.
 *
 * Una sola instancia para toda la aplicacion: crear las sesiones de ORT cuesta
 * ~100 ms y son seguras de reusar entre hilos. El motor no guarda estado entre
 * lecturas.
 */
@Service
public class CuponOcrService {

    private static final Logger log = LoggerFactory.getLogger(CuponOcrService.class);

    private static final String BASE = "ocr/";
    private static final String DET  = BASE + "ch_PP-OCRv4_det_infer.onnx";
    private static final String CLS  = BASE + "ch_ppocr_mobile_v2.0_cls_infer.onnx";
    private static final String REC  = BASE + "ch_PP-OCRv4_rec_infer.onnx";
    private static final String DIC  = BASE + "ppocr_keys.txt";

    private volatile MotorOcr motor;

    @PostConstruct
    public void iniciar() {
        long t0 = System.nanoTime();
        try {
            motor = new MotorOcr(recurso(DET), recurso(CLS), recurso(REC), diccionario());
            log.info("OCR de cupon listo en {} ms", (System.nanoTime() - t0) / 1_000_000);
        } catch (Throwable e) {
            // Throwable y no Exception, y no es exceso de celo: el modo de falla mas probable de
            // esto es que la libreria nativa de ONNX no cargue --arquitectura sin binario, glibc
            // vieja, jar podado sin la plataforma-- y eso llega como UnsatisfiedLinkError, que es
            // un Error. Con `catch (Exception)` el guard no se activaba y la excepcion subia por
            // el @PostConstruct tumbando TODO el contexto de Spring: el filial entero no
            // arrancaba, la sucursal no vendia, por un lector de cupones que es opcional.
            // Verificado el 2026-09-10 arrancando en macOS ARM, donde el jar slim no trae nativo.
            motor = null;
            log.error("OCR de cupon NO disponible — la captura por foto va a caer al fallback manual", e);
        }
    }

    public boolean disponible() {
        return motor != null;
    }

    /**
     * @param jpeg la foto ya orientada y escalada por el telefono
     * @return las lineas leidas, en orden de lectura, con su confianza
     */
    public MotorOcr.Resultado leer(byte[] jpeg) throws OrtException, IOException {
        MotorOcr m = motor;
        if (m == null) throw new IllegalStateException("el motor de OCR no esta disponible");
        try (InputStream in = new ByteArrayInputStream(jpeg)) {
            return m.reconocer(Imagen.leer(in));
        }
    }

    private static byte[] recurso(String ruta) throws IOException {
        try (InputStream in = new ClassPathResource(ruta).getInputStream()) {
            return StreamUtils.copyToByteArray(in);
        }
    }

    /**
     * El diccionario NO se lee de la metadata del modelo aunque este ahi: el binding
     * Java de ORT la expone via JNI NewStringUTF, que usa Modified UTF-8 y no admite
     * secuencias de 4 bytes. El unico caracter fuera del BMP (U+231C9, linea 6137)
     * vuelve como 4 caracteres sueltos, corre todos los indices y rompe la
     * decodificacion en silencio — se manifiesta como "faltan los espacios".
     * MotorOcr verifica el largo contra la dimension de salida del modelo y aborta.
     */
    private static List<String> diccionario() throws IOException {
        try (InputStream in = new ClassPathResource(DIC).getInputStream()) {
            String txt = new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8);
            List<String> lineas = new ArrayList<>();
            Collections.addAll(lineas, txt.split("\n", -1));
            while (!lineas.isEmpty() && lineas.get(lineas.size() - 1).isEmpty())
                lineas.remove(lineas.size() - 1);
            return lineas;
        }
    }
}
