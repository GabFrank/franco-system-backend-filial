package com.franco.dev.utilitarios;

/**
 * FUNCIÓN PARA EL CÁLCULO DEL DÍGITO VERIFICADOR EN JAVA
 * Basada en la función PL/SQL original
 */
public class CalcularVerificadorCDC {
    
    /**
     * Calcula el dígito verificador de un número dado
     * @param numero El número para calcular el DV
     * @param basemax La base máxima para el cálculo (por defecto 11)
     * @return El dígito verificador calculado
     */
    public static int calcularDigitoVerificador(String numero, int basemax) {
        StringBuilder numeroAl = new StringBuilder();
        
        // Cambia la ultima letra por ascii en caso que la cedula termine en letra
        for (int i = 0; i < numero.length(); i++) {
            char caracter = Character.toUpperCase(numero.charAt(i));
            int ascii = (int) caracter;
            
            if (ascii < 48 || ascii > 57) { // de 0 a 9
                numeroAl.append(ascii);
            } else {
                numeroAl.append(caracter);
            }
        }
        
        // Calcula el DV
        int k = 2;
        int total = 0;
        
        for (int i = numeroAl.length() - 1; i >= 0; i--) {
            if (k > basemax) {
                k = 2;
            }
            int numeroAux = Character.getNumericValue(numeroAl.charAt(i));
            total += (numeroAux * k);
            k++;
        }
        
        int resto = total % 11;
        int digito;
        
        if (resto > 1) {
            digito = 11 - resto;
        } else {
            digito = 0;
        }
        
        return digito;
    }
    
    /**
     * Sobrecarga del método con base máxima por defecto 11
     * @param numero El número para calcular el DV
     * @return El dígito verificador calculado
     */
    public static int calcularDigitoVerificador(String numero) {
        return calcularDigitoVerificador(numero, 11);
    }

    public static void main(String[] args) {
        System.out.println(calcularDigitoVerificador("0144444401700100100145282201701251587326098"));
    }
}
