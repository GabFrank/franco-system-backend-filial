package com.franco.dev.utilitarios.print.escpos.barcode;/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */


import com.franco.dev.utilitarios.print.escpos.EscPosConst;

public interface BarCodeWrapperInterface<T> {
    public byte[] getBytes(String data);
    public T setJustification(EscPosConst.Justification justification);
}
