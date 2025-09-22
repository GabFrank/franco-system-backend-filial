package com.franco.dev.utilitarios.print;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;

public class QRCodeImageGenerator {

    /**
     * Generates a QR Code image from a given text.
     *
     * @param text The text to encode in the QR Code.
     * @param width The desired width of the QR Code image.
     * @param height The desired height of the QR Code image.
     * @return A BufferedImage representing the QR Code.
     * @throws WriterException if there is an error during QR Code generation.
     */
    public static BufferedImage generateQRCodeImage(String text, int width, int height) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }
}
