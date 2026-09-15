package com.franco.dev.controller;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.graphql.financiero.publisher.CapturaCuponPublisher;
import com.franco.dev.graphql.financiero.publisher.CapturaCuponUpdate;
import com.franco.dev.service.financiero.CapturaCuponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * La captura de la foto del cupon, del lado del telefono.
 *
 * <p><b>Por que REST y no GraphQL.</b> El ciclo de implementacion pide que los endpoints nuevos
 * vayan en {@code graphql/}, y esa regla vale para la API de los clientes Apollo. Acá del otro
 * lado hay un navegador pelado: carga una pagina y sube un archivo. No hay cliente GraphQL, no
 * hay token de sesion, no hay Apollo. Es una excepcion deliberada, no un olvido.
 *
 * <p><b>Por que cuelga de {@code /public}.</b> El telefono no tiene login ni rol, asi que la ruta
 * tiene que quedar fuera de la autenticacion. {@code /public/**} ya es el espacio {@code
 * permitAll} de {@code SecurityConfig}: se reusa en vez de agregar una regla nueva.
 *
 * <p><b>Quien autoriza entonces.</b> El token del QR, y nada mas. Solo lo puede mostrar un
 * desktop que ya paso las puertas --caja abierta, rol de venta, flujo habilitado--, y es de un
 * solo uso, expira en minutos y esta atado a una caja.
 *
 * <p>El filial sirve esto por <b>HTTP plano en la LAN</b>. Es a proposito: origen privado hacia
 * privado, sin contenido mixto y sin permiso de red local, asi funciona igual en Safari que en
 * Chrome sin ningun certificado. Ver §2.8 de FASE-2-TICKET-FISICO.md.
 */
@Slf4j
@RestController
@RequestMapping("/public/captura")
public class CapturaCuponController {

    private static final String PAGINA = "captura/captura.html";
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private final CapturaCuponService service;
    private final CapturaCuponPublisher publisher;

    public CapturaCuponController(CapturaCuponService service, CapturaCuponPublisher publisher) {
        this.service = service;
        this.publisher = publisher;
    }

    /** La pagina que abre el telefono al escanear el QR. */
    @GetMapping(value = "/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> pagina(@PathVariable String token) {
        Optional<CapturaCupon> captura = service.porToken(token);
        if (!captura.isPresent() || captura.get().yaSeUso() || captura.get().estaVencida()) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .contentType(MediaType.TEXT_HTML)
                    .body(aviso("Este código ya no sirve",
                            "Pedí uno nuevo desde la caja y volvé a escanear."));
        }
        try (InputStream in = new ClassPathResource(PAGINA).getInputStream()) {
            return ResponseEntity.ok(new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("no se pudo servir la pagina de captura", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(aviso("No se pudo abrir", "Avisá al soporte."));
        }
    }

    /** La foto. El cuerpo es el JPEG crudo: el telefono ya lo enderezo y lo escalo. */
    @PostMapping(value = "/{token}", consumes = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<?> subir(@PathVariable String token,
                                   @RequestHeader(value = "X-Nitidez", required = false) String nitidez,
                                   HttpServletRequest request) {
        // ⚠️ EL TOKEN SE VALIDA ANTES DE LEER UN SOLO BYTE DEL CUERPO.
        //
        // Este endpoint cuelga de /public, o sea sin autenticacion, y el token es toda la
        // credencial. Si primero se leyera la foto, cualquiera en la LAN podria hacer que el
        // filial bufferee megabytes mandando POSTs con un token inventado.
        Optional<CapturaCupon> previa = service.porToken(token);
        if (!previa.isPresent()) {
            return ResponseEntity.status(HttpStatus.GONE).body("codigo desconocido");
        }

        byte[] jpeg;
        try {
            jpeg = leerAcotado(request.getInputStream());
        } catch (CuerpoDemasiadoGrande e) {
            return ResponseEntity.badRequest().body("la foto es demasiado grande");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("no se pudo leer la foto");
        }

        // Sigue contestando el handler y no el framework: con `@RequestBody(required = true)`
        // Spring rechazaba ANTES del handler y respondia el JSON de error con el stack trace
        // completo, que el telefono mostraba tal cual. Verificado el 2026-09-10 con un POST vacio.
        if (jpeg.length == 0) {
            return ResponseEntity.badRequest().body("la foto llego vacia");
        }
        try {
            CapturaCupon c = service.procesar(token, jpeg, parseNitidez(nitidez));

            // Se avisa al desktop DESPUES de que procesar volvio, no adentro del servicio: para
            // entonces la transaccion ya commiteo. Si se publicara antes, el desktop podria
            // reaccionar al aviso, consultar por token y leer el estado viejo.
            avisar(c);

            // ERROR no es 500: es un desenlace previsto y REINTENTABLE --el token sigue vivo--.
            // El telefono muestra el motivo y ofrece sacar otra foto sin volver a la caja.
            if (CapturaCupon.ERROR.equals(c.getEstado())) {
                return ResponseEntity.unprocessableEntity().body(c.getError());
            }

            Map<String, Object> r = new HashMap<>();
            r.put("estado", c.getEstado());
            r.put("lineas", c.getTextoOcr() == null ? 0 : c.getTextoOcr().split("\n").length);
            r.put("ms", c.getMsOcr());
            return ResponseEntity.ok(r);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Mensajes pensados para que los lea un cajero con el cliente enfrente.
            return ResponseEntity.status(HttpStatus.GONE).body(e.getMessage());
        } catch (Exception e) {
            log.error("fallo la subida de la captura", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("no se pudo procesar la foto");
        }
    }

    /**
     * Toca el timbre para que el desktop vaya a buscar el resultado.
     *
     * <p>Va sin el texto del cupon <b>y sin el token</b>: la subscription del filial es anonima
     * --por WebSocket no hay sesion-- y el token es la credencial con la que se pide el contenido.
     * Lo unico que viaja es de que caja es la novedad. Ver {@code CapturaCuponUpdate}.
     *
     * <p>Nunca hace fallar la subida: el telefono ya cumplio, y si el aviso se pierde el desktop
     * lo va a ver igual cuando consulte por token.
     */
    private void avisar(CapturaCupon c) {
        try {
            CapturaCuponUpdate u = new CapturaCuponUpdate();
            u.setCajaId(c.getCajaId());
            u.setEstado(c.getEstado());
            publisher.publish(u);
        } catch (Exception e) {
            log.error("no se pudo avisar al desktop de la captura {}", c.getId(), e);
        }
    }

    private static java.math.BigDecimal parseNitidez(String v) {
        if (v == null || v.isEmpty()) return null;
        try {
            return new java.math.BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;   // dato de telemetria: si viene mal, no vale frenar la captura
        }
    }

    /** El cuerpo se paso del tope. Se corta la lectura y se contesta, sin retener lo leido. */
    private static final class CuerpoDemasiadoGrande extends IOException {
    }

    /**
     * Lee el cuerpo hasta el tope y aborta apenas lo pasa.
     *
     * <p><b>Por que a mano y no con {@code @RequestBody byte[]}.</b> Ese binding hace que Spring
     * bufferee el cuerpo ENTERO en memoria antes de que el handler corra, asi que un chequeo de
     * tamano dentro del metodo llega tarde: para cuando se ejecuta, los bytes ya estan en el heap.
     * En un endpoint sin autenticacion eso es un camino directo a tumbar el proceso mandando
     * cuerpos de cientos de MB.
     *
     * <p>Y no alcanza con configurar el limite del contenedor:
     * {@code spring.servlet.multipart.max-request-size} no aplica —esto no es multipart, es un
     * {@code image/jpeg} crudo—.
     *
     * <p>Leyendo de a bloques y cortando en el tope, lo maximo que se retiene son los 8 MB del
     * limite mas un bloque.
     */
    private static byte[] leerAcotado(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int leidos;
        int total = 0;
        while ((leidos = in.read(buffer)) != -1) {
            total += leidos;
            if (total > MAX_BYTES) throw new CuerpoDemasiadoGrande();
            out.write(buffer, 0, leidos);
        }
        return out.toByteArray();
    }

    private static String aviso(String titulo, String detalle) {
        return "<!doctype html><meta charset=\"utf-8\">"
             + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
             + "<title>" + titulo + "</title>"
             + "<body style=\"font:16px/1.5 -apple-system,system-ui,sans-serif;"
             + "margin:0;padding:48px 24px;text-align:center;color:#1c1b19;background:#f6f5f3\">"
             + "<h1 style=\"font-size:20px;margin:0 0 8px\">" + titulo + "</h1>"
             + "<p style=\"color:#6d6a64;margin:0\">" + detalle + "</p>";
    }
}
