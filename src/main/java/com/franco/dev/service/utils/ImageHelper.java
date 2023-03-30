package com.franco.dev.service.utils;

import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.image.Bitonal;
import com.franco.dev.utilitarios.print.escpos.image.CoffeeImage;
import com.franco.dev.utilitarios.print.escpos.image.EscPosImage;
import com.franco.dev.utilitarios.print.escpos.image.ImageWrapperInterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImageHelper {
    final int maxWidth;
    final int maxHeight;

    /**
     * creates an ImageHelper with default values
     */
    public ImageHelper() {
        this(576, 48);
    }

    /**
     * create an ImageHelper
     *
     * @param maxWidth  read your printer documentation to discover the width max dots
     * @param maxHeight test / read your printer to discover the printer buffer size, this number should be as bigger as possible
     */
    public ImageHelper(int maxWidth, int maxHeight) {
        //maxHeight need to be multiple of 24
        if (maxHeight < 24) maxHeight = 24;
        if ((maxHeight % 24) != 0) maxHeight -= (maxHeight % 24);
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    /**
     * Slice vertically the image in maxHeight offsets
     *
     * @param coffeeImage implementation of CoffeeImage {@link java.awt.image.BufferedImage} or Bitmap (android)
     * @return a list (sliced) of the CoffeeImage
     */
    public List<CoffeeImage> sliceImage(CoffeeImage coffeeImage) {
        List<CoffeeImage> listImages = new ArrayList<>();

        int x = 0;
        int y = 0;
        int x_offset = maxWidth;
        int y_offset = maxHeight;

        while (true) {
            // safety to not run in out of bound
            if (x > (coffeeImage.getWidth() - 1)) {
                x = coffeeImage.getWidth() - 1;
            }
            if ((x + x_offset) > coffeeImage.getWidth()) {
                x_offset = coffeeImage.getWidth() - x;
            }

            if (y >= (coffeeImage.getHeight() - 1)) {
                y = coffeeImage.getHeight() - 1;
            }
            if ((y + y_offset) > coffeeImage.getHeight()) {
                y_offset = coffeeImage.getHeight() - y;
            }

            CoffeeImage tmp = coffeeImage.getSubimage(0, y, x_offset, y_offset);
            listImages.add(tmp);

            y += y_offset;
            if (y >= coffeeImage.getHeight()) break;
        }

        return listImages;
    }

    /**
     * just slice the image and print sequentially
     * with regular escpos write image
     *
     * @param escPos
     * @param image
     * @param wrapper
     * @param bitonalAlgorithm
     * @throws IOException
     */
    public void write(EscPos escPos, CoffeeImage image, ImageWrapperInterface wrapper, Bitonal bitonalAlgorithm) throws IOException {
        List<CoffeeImage> images = sliceImage(image);
        for (CoffeeImage img : images) {
            escPos.write(wrapper, new EscPosImage(img, bitonalAlgorithm));
        }
    }
}
