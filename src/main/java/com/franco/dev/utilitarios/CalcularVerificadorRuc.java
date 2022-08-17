package com.franco.dev.utilitarios;

public class CalcularVerificadorRuc {

    public static int getDigitoVerificador(String ruc) {
        return getDigitoVerificador(ruc, 11);
    }

    public static String getDigitoVerificadorString(String ruc){
        Integer digito = getDigitoVerificador(ruc, 11);
        if(digito!=null){
            return "-" + digito;
        }
        return "";
    }

    public static Integer getDigitoVerificador(String ruc, int base) {
        if(ruc.length() < 6) return null;
        int k = 2;
        int total = 0;

        String alRevez = invertirCadena(eliminarNoDigitos(ruc));

        for (char numero : alRevez.toCharArray()) {
            total += (numero - '0') * k++;

            if (k > base)
                k = 2;
        }

        int resto = total % base;

        return resto > 1 ? base - resto : 0;
    }

    protected static String invertirCadena(String ruc) {
        // si se dispone de apache commons se puede usar
        // StringUtils.reverse(ruc);
        return new StringBuilder(ruc).reverse().toString();
    }

    /**
     * Elimina todos los no digitos de la cadena.
     *
     * @param ruc ruc con numeros, simbolos y letras.
     * @return una versión del ruc consistente de solo digitos.
     */
    protected static String eliminarNoDigitos(String ruc) {
        String toRet = "";
        for (char c : ruc.toCharArray()) {
            if (Character.isDigit(c)) {
                toRet += c;
            } else {
                toRet += (int) c;
            }
        }
        return toRet;
    }
}
