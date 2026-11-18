package com.franco.dev.utilitarios;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class NumeroALetrasService {

    private final String[] UNIDADES = {"", "un ", "dos ", "tres ", "cuatro ", "cinco ", "seis ", "siete ", "ocho ", "nueve "};
    private final String[] DECENAS = {"diez ", "once ", "doce ", "trece ", "catorce ", "quince ", "dieciseis ",
            "diecisiete ", "dieciocho ", "diecinueve ", "veinte ", "treinta ", "cuarenta ",
            "cincuenta ", "sesenta ", "setenta ", "ochenta ", "noventa "};
    private final String[] CENTENAS = {"", "ciento ", "doscientos ", "trecientos ", "cuatrocientos ", "quinientos ", "seiscientos ",
            "setecientos ", "ochocientos ", "novecientos "};


    /**
     * Un entero (hasta 999.999.999.999) en letras. Hasta 9 digitos el texto es el de siempre, salvo el
     * plural de los millones (de 2 a 9 millones decia "DOS MILLON"). Desde mil millones antes devolvia
     * null y el total en letras salia vacio. Entrada que no es un entero sin signo: null.
     */
    public String converter(String numero, boolean mayusculas) {
        if (numero == null || !Pattern.matches("\\d{1,12}", numero)) {
            return null;
        }
        long valor = Long.parseLong(numero);
        String literal;
        if (valor == 0) {
            literal = "cero ";
        } else if (valor > 999999999L) {
            literal = getMilesDeMillones(String.valueOf(valor));
        } else if (valor > 999999) {
            literal = getMillones(String.valueOf(valor));
        } else if (valor > 999) {
            literal = getMiles(String.valueOf(valor));
        } else if (valor > 99) {
            literal = getCentenas(String.valueOf(valor));
        } else if (valor > 9) {
            literal = getDecenas(String.valueOf(valor));
        } else {
            literal = getUnidades(String.valueOf(valor));
        }
        return mayusculas ? literal.toUpperCase() : literal.toLowerCase();
    }

    /**
     * Un monto con los decimales de su moneda: la parte entera en letras y los decimales como fraccion
     * ("CINCO CON 85/100"). Con 0 decimales es {@link #converter} de la parte entera, redondeada.
     */
    public String enLetras(java.math.BigDecimal monto, int decimales, boolean mayusculas) {
        if (monto == null || monto.signum() < 0) return null;
        java.math.BigDecimal redondeado = monto.setScale(Math.max(decimales, 0), java.math.RoundingMode.HALF_UP);
        String entero = converter(redondeado.toBigInteger().toString(), mayusculas);
        if (entero == null || decimales <= 0) return entero;
        java.math.BigInteger fraccion = redondeado.remainder(java.math.BigDecimal.ONE).movePointRight(decimales).toBigInteger();
        String digitos = String.format("%0" + decimales + "d", fraccion);
        String denominador = java.math.BigInteger.TEN.pow(decimales).toString();
        return entero.trim() + (mayusculas ? " CON " : " con ") + digitos + "/" + denominador;
    }

    /* funciones para convertir los numeros a literales */
    private String getUnidades(String numero) {// 1 - 9
        //si tuviera algun 0 antes se lo quita -> 09 = 9 o 009=9
        String num = numero.substring(numero.length() - 1);
        return UNIDADES[Integer.parseInt(num)];
    }

    private String getDecenas(String num) {// 99
        int n = Integer.parseInt(num);
        if (n < 10) {//para casos como -> 01 - 09
            return getUnidades(num);
        } else if (n > 19) {//para 20...99
            String u = getUnidades(num);
            if (u.equals("")) { //para 20,30,40,50,60,70,80,90
                return DECENAS[Integer.parseInt(num.substring(0, 1)) + 8];
            } else {
                return DECENAS[Integer.parseInt(num.substring(0, 1)) + 8] + "y " + u;
            }
        } else {//numeros entre 11 y 19
            return DECENAS[n - 10];
        }
    }

    private String getCentenas(String num) {// 999 o 099
        if (Integer.parseInt(num) > 99) {//es centena
            if (Integer.parseInt(num) == 100) {//caso especial
                return " cien ";
            } else {
                return CENTENAS[Integer.parseInt(num.substring(0, 1))] + getDecenas(num.substring(1));
            }
        } else {//por Ej. 099
            //se quita el 0 antes de convertir a decenas
            return getDecenas(Integer.parseInt(num) + "");
        }
    }

    private String getMiles(String numero) {// 999 999
        //obtiene las centenas
        String c = numero.substring(numero.length() - 3);
        //obtiene los miles
        String m = numero.substring(0, numero.length() - 3);
        String n = "";
        //se comprueba que miles tenga valor entero
        if (Integer.parseInt(m) > 0) {
            n = getCentenas(m);
            return n + "mil " + getCentenas(c);
        } else {
            return "" + getCentenas(c);
        }

    }

    private String getMillones(String numero) { //000 000 000
        //se obtiene los miles
        String miles = numero.substring(numero.length() - 6);
        //se obtiene los millones
        String millon = numero.substring(0, numero.length() - 6);
        String n = "";
        if (Integer.parseInt(millon) > 1) {
            n = getCentenas(millon) + "millones ";
        } else {
            n = getUnidades(millon) + "millon ";
        }
        return n + getMiles(miles);
    }

    private String getMilesDeMillones(String numero) { // 999 999 999 999
        String resto = numero.substring(numero.length() - 9);
        String milesDeMillon = numero.substring(0, numero.length() - 9);
        String n = Integer.parseInt(milesDeMillon) > 1 ? getCentenas(milesDeMillon) + "mil " : "mil ";
        int millones = Integer.parseInt(resto.substring(0, 3));
        if (millones == 0) {
            return n + "millones " + getMiles(resto.substring(3));
        }
        return n + getCentenas(resto.substring(0, 3)) + "millones " + getMiles(resto.substring(3));
    }
}
