package com.franco.dev.controller;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.service.financiero.CapturaCuponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

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

    public CapturaCuponController(CapturaCuponService service) {
        this.service = service;
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
                                   @RequestBody byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) {
            return ResponseEntity.badRequest().body("la foto llego vacia");
        }
        if (jpeg.length > MAX_BYTES) {
            return ResponseEntity.badRequest().body("la foto es demasiado grande");
        }
        try {
            CapturaCupon c = service.procesar(token, jpeg, parseNitidez(nitidez));

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

    private static java.math.BigDecimal parseNitidez(String v) {
        if (v == null || v.isEmpty()) return null;
        try {
            return new java.math.BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;   // dato de telemetria: si viene mal, no vale frenar la captura
        }
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
