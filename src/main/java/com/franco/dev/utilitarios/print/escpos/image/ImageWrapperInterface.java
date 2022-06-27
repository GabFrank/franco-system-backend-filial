package com.franco.dev.utilitarios.print.escpos.image;/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */


import com.franco.dev.utilitarios.print.escpos.EscPosConst;

public interface ImageWrapperInterface<T> {
        
    public byte[] getBytes(EscPosImage image);
    public T setJustification(EscPosConst.Justification justification);


}
