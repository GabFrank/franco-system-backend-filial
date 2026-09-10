package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CapturaCuponRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.ocr.CuponOcrService;
import com.franco.dev.service.financiero.ocr.MotorOcr;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Coordina la captura de la foto del cupon: crea el token que viaja en el QR, recibe la imagen
 * del telefono y le pasa el OCR.
 */
@Slf4j
@Service
public class CapturaCuponService extends CrudService<CapturaCupon, CapturaCuponRepository> {

    /** Suficiente para que el cajero saque la foto sin que el QR quede vivo toda la tarde. */
    private static final int MINUTOS_VALIDEZ = 10;

    /** 32 bytes de entropia real: el token es la unica credencial del telefono. */
    private static final int BYTES_TOKEN = 24;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter CARPETA = DateTimeFormatter.ofPattern("yyyy/MM");

    private final CapturaCuponRepository repository;
    private final CuponOcrService ocr;
    private final PdvCajaService cajas;

    /**
     * Relativa al directorio de trabajo del servicio (/opt/frc-filial en produccion). Se crea
     * sola en el primer guardado: no hay nada que provisionar antes del despliegue.
     */
    private final String rutaImagenes;

    @Autowired
    public CapturaCuponService(CapturaCuponRepository repository,
                               CuponOcrService ocr,
                               PdvCajaService cajas,
                               @Value("${frc.captura.ruta-imagenes:cupones}") String rutaImagenes) {
        this.repository = repository;
        this.ocr = ocr;
        this.cajas = cajas;
        this.rutaImagenes = rutaImagenes;
    }

    @Override
    public CapturaCuponRepository getRepository() {
        return repository;
    }

    @Transactional
    public CapturaCupon crear(Long cajaId, Long sucursalId, Usuario usuario) {
        byte[] bytes = new byte[BYTES_TOKEN];
        RANDOM.nextBytes(bytes);

        CapturaCupon c = new CapturaCupon();
        c.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        c.setCajaId(cajaId);
        c.setSucursalId(sucursalId);
        c.setUsuario(usuario);
        c.setEstado(CapturaCupon.ESPERANDO);
        c.setExpiraEn(LocalDateTime.now().plusMinutes(MINUTOS_VALIDEZ));
        return repository.save(c);
    }

    public Optional<CapturaCupon> porToken(String token) {
        return repository.findByToken(token);
    }

    /**
     * Recibe la foto, guarda la imagen y corre el OCR.
     *
     * <p>Consume el token en el mismo paso: aunque el OCR falle, el token no vuelve a servir. La
     * imagen se guarda igual, porque es la evidencia y permite completar a mano despues.
     *
     * @return la captura ya actualizada, con el texto o con el error
     */
    @Transactional
    public CapturaCupon procesar(String token, byte[] jpeg, BigDecimal nitidez) {
        CapturaCupon c = repository.lockByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("codigo desconocido"));

        // Reintento sobre algo que ya salio bien: se devuelve el resultado en vez de un error.
        // Sin esto, perder la RESPUESTA --no la request-- deja al cajero trabado con un "ya se
        // uso" aunque del lado del servidor todo haya funcionado.
        if (CapturaCupon.LISTO.equals(c.getEstado())) return c;

        if (c.yaSeUso())     throw new IllegalStateException("este codigo ya se uso");
        if (c.estaVencida()) throw new IllegalStateException("el codigo vencio; pedi uno nuevo desde la caja");

        // La caja pudo cerrarse en los minutos que el telefono tuvo la pagina abierta.
        Optional<PdvCaja> caja = cajas.findById(c.getCajaId());
        if (!caja.isPresent() || caja.get().getEstado() != PdvCajaEstado.EN_PROCESO) {
            throw new IllegalStateException("la caja ya no esta abierta");
        }

        if (!ocr.disponible()) {
            throw new IllegalStateException("el lector no esta disponible; carga el cupon a mano");
        }

        c.setIntentos(c.getIntentos() == null ? 1 : c.getIntentos() + 1);
        c.setNitidez(nitidez);
        c.setEstado(CapturaCupon.PROCESANDO);
        c.setError(null);

        try {
            c.setImagenUrl(guardar(c, jpeg));
        } catch (Exception e) {
            // Que no se pueda archivar la evidencia no tiene por que impedir leer el cupon.
            log.error("no se pudo guardar la imagen de la captura {}", c.getId(), e);
        }

        try {
            MotorOcr.Resultado r = ocr.leer(jpeg);
            if (r.lineas.isEmpty()) {
                // Leyo cero: casi siempre es que la foto no es del cupon, o esta ilegible.
                // NO se consume el token: el cajero saca otra sin volver a la caja.
                c.setEstado(CapturaCupon.ERROR);
                c.setError("no se leyo nada; asegurate de que la foto sea del cupon");
            } else {
                c.setTextoOcr(r.lineas.stream().map(l -> l.texto).collect(Collectors.joining("\n")));
                c.setMsOcr((int) r.msTotal);
                c.setEstado(CapturaCupon.LISTO);
                c.setUsadoEn(LocalDateTime.now());   // el token se consume RECIEN con un resultado bueno
            }
        } catch (Exception e) {
            log.error("fallo el OCR de la captura {}", c.getId(), e);
            c.setEstado(CapturaCupon.ERROR);
            c.setError(recortar(e.getMessage()));
        }
        return repository.save(c);
    }

    /** {@code <ruta>/yyyy/MM/<id>.jpg}, con el directorio creado al vuelo. */
    private String guardar(CapturaCupon c, byte[] jpeg) throws Exception {
        Path dir = Paths.get(rutaImagenes, LocalDateTime.now().format(CARPETA));
        Files.createDirectories(dir);
        Path destino = dir.resolve(c.getId() + ".jpg");
        Files.write(destino, jpeg);
        return destino.toString();
    }

    private static String recortar(String s) {
        if (s == null) return "error sin detalle";
        return s.length() > 480 ? s.substring(0, 480) : s;
    }
}
