package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.repository.financiero.CapturaCuponRepository;
import com.franco.dev.repository.financiero.VentaTarjetaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Borra del disco las fotos de cupon que ya no hacen falta.
 *
 * <p><b>Por que hace falta.</b> Las imagenes, {@code releases/} y los datos de PostgreSQL comparten
 * disco en un filial. Un disco lleno no rompe solo el guardado de fotos: impide que Postgres
 * escriba WAL y tumba todas las ventas de esa sucursal.
 *
 * <p><b>Que protege, que es lo que faltaba definir.</b> Hasta la auditoria del plan, "purgar por
 * antiguedad" no tenia forma de distinguir una foto huerfana de la evidencia de un cobro: la
 * imagen vive en {@code captura_cupon} y no habia ninguna FK ni columna que la vinculara con
 * {@code venta_tarjeta}. Ahora {@code completar()} copia la ruta a {@code venta_tarjeta.imagen_url}
 * cuando la venta salio de una foto, asi que <b>una imagen referenciada por una venta no se
 * borra</b>, tenga la antiguedad que tenga. Se purga lo que quedo por el camino: capturas que
 * nunca terminaron en cobro.
 *
 * <p><b>Borrar archivos no es reversible.</b> Por eso arranca apagado
 * ({@code matchIfMissing = false}, la convencion de schedulers de este repo) y por eso existe el
 * modo simulacion, que loguea exactamente lo que borraria sin tocar nada.
 */
@Slf4j
@Service
public class PurgaImagenesCuponService {

    private final CapturaCuponRepository capturas;
    private final VentaTarjetaRepository ventas;
    private final ConfiguracionVentaTarjetaService configuracion;
    private final String rutaImagenes;

    public PurgaImagenesCuponService(CapturaCuponRepository capturas,
                                     VentaTarjetaRepository ventas,
                                     ConfiguracionVentaTarjetaService configuracion,
                                     @Value("${frc.captura.ruta-imagenes:capturas}") String rutaImagenes) {
        this.capturas = capturas;
        this.ventas = ventas;
        this.configuracion = configuracion;
        this.rutaImagenes = rutaImagenes;
    }

    /** Lo que una corrida hizo, o haria. */
    public static final class Resumen {
        public final int candidatas;
        public final int protegidas;
        public final int borradas;
        public final long bytesLiberados;
        public final boolean simulacion;

        Resumen(int candidatas, int protegidas, int borradas, long bytes, boolean simulacion) {
            this.candidatas = candidatas; this.protegidas = protegidas;
            this.borradas = borradas; this.bytesLiberados = bytes; this.simulacion = simulacion;
        }

        @Override
        public String toString() {
            return (simulacion ? "[SIMULACION] " : "") + candidatas + " candidatas, "
                    + protegidas + " protegidas por una venta, " + borradas + " borradas, "
                    + (bytesLiberados / 1024) + " KB";
        }
    }

    /**
     * Corre la purga.
     *
     * @param simulacion si es {@code true} no borra nada: solo calcula y loguea. Es el modo por
     *                   default de la primera corrida en cualquier filial.
     */
    @Transactional(readOnly = true)
    public Resumen purgar(boolean simulacion) {
        Integer dias = configuracion.findOrDefault().getDiasRetencionImagenes();
        if (dias == null || dias <= 0) {
            // NULL significa "no purgar nunca", y es el default. Una purga que se prende sola
            // porque alguien dejo la columna vacia seria exactamente el tipo de sorpresa que este
            // servicio no puede darse.
            log.debug("purga de imagenes: sin dias de retencion configurados, no se hace nada");
            return new Resumen(0, 0, 0, 0, simulacion);
        }

        LocalDateTime corte = LocalDateTime.now().minusDays(dias);
        List<CapturaCupon> viejas = capturas.findByCreadoEnBeforeAndImagenUrlIsNotNull(corte);

        int protegidas = 0, borradas = 0;
        long bytes = 0;
        List<String> aBorrar = new ArrayList<String>();

        for (CapturaCupon c : viejas) {
            // La foto de una venta es evidencia: no se toca, por vieja que sea.
            if (ventas.existsByImagenUrl(c.getImagenUrl())) {
                protegidas++;
                continue;
            }
            aBorrar.add(c.getImagenUrl());
        }

        for (String ruta : aBorrar) {
            try {
                Path p = Paths.get(ruta);
                // Solo dentro de la carpeta de capturas. Una ruta guardada mal --o manipulada--
                // no tiene por que poder borrar cualquier archivo del filial.
                if (!dentroDeLaCarpeta(p)) {
                    log.warn("purga: se salteo {} porque esta fuera de la carpeta de capturas", ruta);
                    continue;
                }
                File f = p.toFile();
                if (!f.exists()) continue;
                long tam = f.length();
                if (!simulacion && !f.delete()) {
                    log.warn("purga: no se pudo borrar {}", ruta);
                    continue;
                }
                borradas++;
                bytes += tam;
            } catch (Exception e) {
                log.warn("purga: fallo con {}", ruta, e);
            }
        }

        Resumen r = new Resumen(viejas.size(), protegidas, borradas, bytes, simulacion);
        log.info("purga de imagenes de cupon: {}", r);
        return r;
    }

    /**
     * Que la ruta caiga dentro de la carpeta configurada.
     *
     * <p>Se compara la ruta <b>normalizada y absoluta</b>: sin eso, un {@code ..} en el medio pasa
     * el chequeo y sale de la carpeta.
     */
    private boolean dentroDeLaCarpeta(Path p) {
        try {
            Path base = Paths.get(rutaImagenes).toAbsolutePath().normalize();
            return p.toAbsolutePath().normalize().startsWith(base);
        } catch (Exception e) {
            return false;
        }
    }
}
