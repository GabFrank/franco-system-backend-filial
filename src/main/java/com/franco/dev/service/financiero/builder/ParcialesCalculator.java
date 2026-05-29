package com.franco.dev.service.financiero.builder;

import java.util.List;

/**
 * Calculo de parciales (total_parcial_0/5/10), iva_parcial_5/10 y total_final
 * para FacturaLegal segun la fórmula correcta:
 *
 *   sumaBruta = sum(item.total) por banda IVA (sin descuento)
 *   porcentajeDescuento = descuento / totalBruto
 *   total_parcial_X = sumaBruta_X * (1 - porcentajeDescuento)
 *   iva_parcial_5  = total_parcial_5  / 21.0   (IVA incluido)
 *   iva_parcial_10 = total_parcial_10 / 11.0
 *   total_final = total_parcial_0 + total_parcial_5 + total_parcial_10
 *
 * Reemplaza las 3 implementaciones divergentes que existian en
 * FacturaLegalGraphQL, FacturaLegalApiService y FacturaService (la ultima
 * distribuia descuento; las otras dos no).
 */
public final class ParcialesCalculator {

    private ParcialesCalculator() {
    }

    public static class ItemIvaTuple {
        private final int iva;
        private final double total;

        public ItemIvaTuple(int iva, double total) {
            this.iva = iva;
            this.total = total;
        }

        public int getIva() {
            return iva;
        }

        public double getTotal() {
            return total;
        }
    }

    public static class Resultado {
        private final double totalParcial0;
        private final double totalParcial5;
        private final double totalParcial10;
        private final double ivaParcial5;
        private final double ivaParcial10;
        private final double totalFinal;

        public Resultado(double totalParcial0, double totalParcial5, double totalParcial10,
                         double ivaParcial5, double ivaParcial10, double totalFinal) {
            this.totalParcial0 = totalParcial0;
            this.totalParcial5 = totalParcial5;
            this.totalParcial10 = totalParcial10;
            this.ivaParcial5 = ivaParcial5;
            this.ivaParcial10 = ivaParcial10;
            this.totalFinal = totalFinal;
        }

        public double getTotalParcial0() { return totalParcial0; }
        public double getTotalParcial5() { return totalParcial5; }
        public double getTotalParcial10() { return totalParcial10; }
        public double getIvaParcial5() { return ivaParcial5; }
        public double getIvaParcial10() { return ivaParcial10; }
        public double getTotalFinal() { return totalFinal; }
    }

    public static Resultado calcular(List<ItemIvaTuple> items, double descuento) {
        double bruto0 = 0.0, bruto5 = 0.0, bruto10 = 0.0;
        for (ItemIvaTuple t : items) {
            switch (t.getIva()) {
                case 5:
                    bruto5 += t.getTotal();
                    break;
                case 10:
                    bruto10 += t.getTotal();
                    break;
                case 0:
                default:
                    // iva 0 o cualquier valor no soportado cae en exento
                    bruto0 += t.getTotal();
                    break;
            }
        }
        double brutoTotal = bruto0 + bruto5 + bruto10;

        double p0 = bruto0, p5 = bruto5, p10 = bruto10;
        // descuento puede ser positivo (descuento) o negativo (aumento, ej. recargo).
        if (descuento != 0.0 && brutoTotal > 0) {
            double porcentaje = Math.abs(descuento) / brutoTotal;
            // si descuento positivo >= brutoTotal, no devolver negativos
            if (descuento > 0 && porcentaje > 1.0) {
                porcentaje = 1.0;
            }
            double factor = descuento > 0 ? (1.0 - porcentaje) : (1.0 + porcentaje);
            p0 = bruto0 * factor;
            p5 = bruto5 * factor;
            p10 = bruto10 * factor;
        }

        double iva5 = p5 / 21.0;
        double iva10 = p10 / 11.0;
        double totalFinal = p0 + p5 + p10;

        return new Resultado(p0, p5, p10, iva5, iva10, totalFinal);
    }
}
