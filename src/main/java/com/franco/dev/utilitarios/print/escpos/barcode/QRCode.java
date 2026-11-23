package com.franco.dev.utilitarios.print.escpos.barcode;

import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.image.BitImageWrapper;
import com.franco.dev.utilitarios.print.escpos.image.BitonalThreshold;
import com.franco.dev.utilitarios.print.escpos.image.CoffeeImageImpl;
import com.franco.dev.utilitarios.print.escpos.image.EscPosImage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;


/**
 * QR para las térmicas ESC/POS, impreso como imagen ({@code ESC *}), no con el comando nativo {@code GS ( k}.
 *
 * <p>La OLIVETTI PRT80 no interpreta {@code GS ( k}: imprime los datos como texto (#259, #282). La imagen
 * {@code ESC *} es la misma que ya usan el logo y el QR de la factura electrónica, que esa impresora --y todas
 * las del parque-- imprimen bien. Así el QR deja de depender de la marca de la impresora.
 */
public class QRCode implements EscPosConst, BarCodeWrapperInterface{

    /**
     * Ancho máximo del QR en puntos: el ancho útil de un rollo de 58 mm. Una imagen {@code ESC *} más ancha que
     * el papel sale rota; en 80 mm un dato largo sale un poco más chico, pero se lee.
     */
    static final int ANCHO_MAXIMO = 384;

    /** Zona en blanco alrededor del QR, en módulos. */
    static final int MARGEN = 2;

    /**
     * Ya no se usa: zxing genera siempre el modelo 2, el que las impresoras usaban por defecto.
     * Queda para no romper a quien lo llame.
     * @see #setModel(QRModel)
     */
    public enum QRModel{
        _1(49),
        _2(50),
        ;
        public int value;
        private QRModel(int value){
            this.value = value;
        }
    }

    /**
     * Values for QR Error Correction Level.
     *
     */
    public enum QRErrorCorrectionLevel{
        QR_ECLEVEL_L(48, ErrorCorrectionLevel.L), // 7%
        QR_ECLEVEL_M_Default(49, ErrorCorrectionLevel.M), //15%
        QR_ECLEVEL_Q(50, ErrorCorrectionLevel.Q), //25%
        QR_ECLEVEL_H(51, ErrorCorrectionLevel.H)  //30%
        ;
        public int value;
        final ErrorCorrectionLevel zxing;
        private QRErrorCorrectionLevel(int value, ErrorCorrectionLevel zxing){
            this.value = value;
            this.zxing = zxing;
        }
    }

    protected Justification justification;
    protected QRModel model;
    protected int size;
    protected QRErrorCorrectionLevel errorCorrectionLevel;

    public QRCode(){
        justification = Justification.Left_Default;
        model = null;
        size = 3;
        errorCorrectionLevel = QRErrorCorrectionLevel.QR_ECLEVEL_M_Default;
    }



    /**
     * Set horizontal justification.
     * @param justification left, center or right.
     * @return this object.
     *
     */
    public QRCode setJustification(Justification justification) {
        this.justification = justification;
        return this;
    }


    /**
     * Sin efecto: el QR sale como imagen y zxing usa siempre el modelo 2 (#282).
     *
     * @param model ignorado
     * @return this object.
     */
    public QRCode setModel(QRModel model) {
        this.model = model;
        return this;
    }

    /**
     * Set the size of module in dots. Es el mismo tamaño físico que tenía el QR nativo; si el QR no entra en
     * {@link #ANCHO_MAXIMO}, el módulo se achica hasta que entre.
     * @param size module size in dots
     * @return this object.
     * @throws IllegalArgumentException when size is not between 1 and 16.
     */
    public QRCode setSize(int size) throws IllegalArgumentException{
        if(size < 1 || size > 16){
            throw new IllegalArgumentException("size must be between 1 and 16" );
        }
        this.size = size;
        return this;
    }

    /**
     * Select the error correction level.<p>
     * @param errorCorrectionLevel nivel de corrección del QR
     * @return this object
     * @see #getBytes(java.lang.String)
     */
    public QRCode setErrorCorrectionLevel(QRErrorCorrectionLevel errorCorrectionLevel) {
        this.errorCorrectionLevel = errorCorrectionLevel;
        return this;
    }

    /**
     * El QR como imagen {@code ESC *}, justificado.
     *
     * <p>Nunca lanza: el QR del ticket de venta va antes de los totales y del corte, y una excepción acá dejaba
     * al cliente sin papel. Si no se puede generar --dato vacío, demasiado largo-- sale el dato como texto, que
     * es lo mismo que imprimía la PRT80 con el comando nativo.
     *
     * @param data to be printed in barcode
     * @return bytes of ESC/POS commands to be sent to the printer
     */
    @Override
    public byte[] getBytes(String data){
        try {
            return new BitImageWrapper()
                    .setJustification(justification)
                    .getBytes(new EscPosImage(new CoffeeImageImpl(imagen(data)), new BitonalThreshold()));
        } catch (Exception e) {
            return comoTexto(data);
        }
    }

    /**
     * El QR en blanco y negro, {@code size} puntos por módulo (menos si no entra en {@link #ANCHO_MAXIMO}).
     * Se pinta con {@code setRGB}, sin {@code Graphics}: anda en un servidor sin pantalla.
     */
    BufferedImage imagen(String data) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel.zxing);
        hints.put(EncodeHintType.MARGIN, MARGEN);
        // Ancho y alto 0: zxing devuelve la matriz mínima, un elemento por módulo.
        BitMatrix matriz = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0, hints);
        int modulos = matriz.getWidth();
        int punto = Math.min(size, ANCHO_MAXIMO / modulos);
        if (punto < 1) {
            throw new IllegalArgumentException("el QR no entra en " + ANCHO_MAXIMO + " puntos");
        }
        int lado = modulos * punto;
        BufferedImage imagen = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < lado; y++) {
            for (int x = 0; x < lado; x++) {
                imagen.setRGB(x, y, matriz.get(x / punto, y / punto) ? 0x000000 : 0xFFFFFF);
            }
        }
        return imagen;
    }

    private byte[] comoTexto(String data) {
        if (data == null || data.isEmpty()) return new byte[0];
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(ESC);
        bytes.write('a');
        bytes.write(justification.value);
        byte[] texto = data.getBytes();
        bytes.write(texto, 0, texto.length);
        bytes.write(LF);
        return bytes.toByteArray();
    }

}
