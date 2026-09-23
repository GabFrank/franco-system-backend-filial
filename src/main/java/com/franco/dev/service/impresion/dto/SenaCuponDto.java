package com.franco.dev.service.impresion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * La seña que se imprime por cada cobro con tarjeta que quedó sin cupón.
 *
 * Es un comprobante INTERNO: el cajero lo grapa al cupón que escupe la terminal y, al conciliar,
 * escanea su QR para caer exactamente en la fila que le corresponde. Sin esto, dos cobros del mismo
 * monto a horas parecidas son indistinguibles en la pantalla de conciliación.
 *
 * A diferencia de {@link RetiroDto}, acá no viajan entidades sino valores planos: el registro lo
 * crea el DESKTOP (`venta-touch.component.ts`, `registrarPagosConTarjeta`) y los ids sólo existen
 * dentro de ese método. Pedirle al filial que los vuelva a buscar sería releer lo que el cliente ya
 * tiene en la mano, y encima con la venta recién guardada.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SenaCuponDto {
    Long ventaId;
    /** El id de la `venta_tarjeta`. Es lo ÚNICO que separa dos cobros con tarjeta de la misma venta. */
    Long ventaTarjetaId;
    Long cajaId;
    String cajero;
    String terminal;
    Double monto;
    String monedaSimbolo;
    /** Decimales de la moneda: Gs. no lleva ninguno y R$ lleva dos. */
    Integer decimales;
    /**
     * El QR ya codificado, tal cual lo va a leer el lector.
     *
     * Lo arma el desktop con `codificarQr()` y viaja hecho a propósito: el contrato de esa cadena
     * (prefijo `frc-`, campos unidos con `-`, `data` posicional con `|` adentro) vive en el
     * frontend y lo consume también el mobile. Reimplementarlo en Java sería un segundo lugar donde
     * se puede desincronizar sin que nada lo avise.
     */
    String qr;
}
