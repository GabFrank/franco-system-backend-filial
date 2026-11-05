package com.franco.dev.service.impresion;

import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.graphql.operaciones.VentaGraphQL;
import com.franco.dev.graphql.print.PrintGraphQL;
import com.franco.dev.print.operaciones.MovimientoPrintService;
import com.franco.dev.service.financiero.FacturaService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import org.junit.jupiter.api.Test;

import javax.print.PrintService;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #212: los beans de impresión son singleton. Un stream guardado en un campo lo comparten
 * dos cajas que imprimen a la vez y los tickets se mezclan o se cortan.
 */
class ImpresionSinStreamCompartidoTest {

    @Test
    void ningunBeanDeImpresionGuardaElStreamEnUnCampo() {
        Class<?>[] beans = {FacturaLegalGraphQL.class, VentaGraphQL.class, ImpresionService.class,
                PrintingService.class, MovimientoPrintService.class, FacturaService.class, PrintGraphQL.class};
        List<String> compartidos = new ArrayList<>();
        for (Class<?> bean : beans) {
            for (Field f : bean.getDeclaredFields()) {
                Class<?> t = f.getType();
                if (!Modifier.isStatic(f.getModifiers()) && (OutputStream.class.isAssignableFrom(t)
                        || EscPos.class.isAssignableFrom(t)
                        // la impresora destino tambien es por llamada; salvo la «ultima usada» de PrintingService
                        || (PrintService.class.isAssignableFrom(t) && !f.getName().equals("selectedPrintService")))) {
                    compartidos.add(bean.getSimpleName() + "." + f.getName());
                }
            }
        }
        assertTrue(compartidos.isEmpty(), "Stream o impresora en un campo compartido: " + compartidos);
    }
}
