package com.franco.dev.service.financiero.ocr;

import ai.onnxruntime.OrtException;
import com.franco.dev.service.financiero.ConfiguracionVentaTarjetaService;
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
 *
 * <p><b>Solo se carga si el modulo de venta con tarjeta esta habilitado</b>, y si se habilita
 * despues no hace falta reiniciar: ver {@link #iniciar()}.
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
    /** Ya se intento cargar y fallo. Sin esto, cada foto reintentaria y volveria a fallar. */
    private volatile boolean fallo;

    private final ConfiguracionVentaTarjetaService configuracion;

    public CuponOcrService(ConfiguracionVentaTarjetaService configuracion) {
        this.configuracion = configuracion;
    }

    /**
     * Carga el motor al arrancar <b>solo si el modulo de venta con tarjeta esta habilitado</b>.
     *
     * <p><b>Por que condicional.</b> El modulo arranca deshabilitado y se prende cuando la empresa
     * termina de configurarlo, asi que hasta ese dia las 24 filiales estarian cargando un motor que
     * ninguna usa: ~2,5 s de arranque y las sesiones de ONNX ocupando memoria en cada sucursal,
     * para nada.
     *
     * <p><b>Por que igual se carga al arrancar cuando SI esta habilitado.</b> Es la diferencia con
     * central, donde el motor es perezoso: alla lo usa un administrador configurando un formato, y
     * 2,5 s de espera no le mueven la aguja. Aca lo usa un cajero con un cliente enfrente, y esos
     * 2,5 s caerian sobre la primera venta con tarjeta del dia.
     *
     * <p><b>Y si el modulo se habilita despues, no hace falta reiniciar nada.</b> La configuracion
     * baja por replicacion en cualquier momento; {@link #motor()} carga el motor la primera vez que
     * se lo necesite. Esa primera foto paga los 2,5 s, y nada mas: exigir un reinicio coordinado de
     * 24 sucursales para prender una perilla no seria aceptable.
     */
    @PostConstruct
    public void iniciar() {
        if (!moduloHabilitado()) {
            log.info("OCR de cupon: el modulo de venta con tarjeta esta deshabilitado, "
                    + "el motor se va a cargar la primera vez que haga falta");
            return;
        }
        cargar();
    }

    /**
     * Si el modulo esta prendido, sin dejar que la respuesta tumbe el arranque.
     *
     * <p>Se lee la configuracion en el arranque, y eso toca la base: si la fila todavia no bajo por
     * replicacion o la consulta falla, la respuesta es "no cargar", que es el lado seguro — el
     * motor se carga solo despues, cuando alguien saque una foto.
     */
    private boolean moduloHabilitado() {
        try {
            return Boolean.TRUE.equals(configuracion.findOrDefault().getHabilitado());
        } catch (Throwable e) {
            log.warn("no se pudo leer si el modulo de venta con tarjeta esta habilitado; "
                    + "el motor de OCR queda para carga diferida", e);
            return false;
        }
    }

    /**
     * El motor, cargandolo si hace falta.
     *
     * <p>Es el camino para el filial que arranco con el modulo apagado y lo vio prenderse por
     * replicacion. Sincronizado porque dos cajas pueden sacar una foto a la vez y crear dos juegos
     * de sesiones de ONNX seria pagar la memoria dos veces.
     */
    private MotorOcr motor() {
        MotorOcr m = motor;
        if (m != null) return m;
        synchronized (this) {
            if (motor != null) return motor;
            if (fallo) throw new IllegalStateException("el motor de OCR no esta disponible");
            cargar();
            if (motor == null) throw new IllegalStateException("el motor de OCR no esta disponible");
            return motor;
        }
    }

    private void cargar() {
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
            fallo = true;
            log.error("OCR de cupon NO disponible — la captura por foto va a caer al fallback manual", e);
        }
    }

    /**
     * Si se puede contar con el motor.
     *
     * <p><b>No es "ya esta cargado".</b> Con la carga condicional, un filial con el modulo apagado
     * arranca sin motor y eso no significa que no lo tenga: lo va a cargar cuando haga falta. Lo
     * que esto responde es si <b>ya se intento y fallo</b>, que es el unico caso en el que hay que
     * mandar al cajero directo a la carga a mano.
     */
    public boolean disponible() {
        return !fallo;
    }

    /**
     * @param jpeg la foto ya orientada y escalada por el telefono
     * @return las lineas leidas, en orden de lectura, con su confianza
     */
    public MotorOcr.Resultado leer(byte[] jpeg) throws OrtException, IOException {
        return leer(jpeg, null);
    }

    /**
     * Lee acotando el reconocimiento a las zonas del mapa, si el formato tiene uno.
     *
     * <p>Es la palanca de rendimiento: reconocer 6 cajas en vez de 26 baja {@code rec} de 3.841 a
     * ~900 ms. Con {@code zonas} en null se lee el cupon entero, que es lo que pasa cuando el
     * formato no tiene mapa todavia.
     */
    public MotorOcr.Resultado leer(byte[] jpeg, List<MotorOcr.Zona> zonas)
            throws OrtException, IOException {
        MotorOcr m = motor();
        try (InputStream in = new ByteArrayInputStream(jpeg)) {
            return m.reconocer(Imagen.leer(in), zonas);
        }
    }

    /** El tamano de la imagen, que la derivacion necesita para normalizar. */
    public int[] tamano(byte[] jpeg) throws IOException {
        try (InputStream in = new ByteArrayInputStream(jpeg)) {
            Imagen i = Imagen.leer(in);
            return new int[]{i.ancho, i.alto};
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
