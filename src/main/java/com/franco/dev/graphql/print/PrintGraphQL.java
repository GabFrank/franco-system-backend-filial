package com.franco.dev.graphql.print;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.print.PrintVale;
import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.enums.UnidadMedida;
import com.franco.dev.graphql.productos.input.FamiliaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.service.utils.TicketCajaData;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import javax.print.PrintService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class PrintGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(PrintGraphQL.class);

    @Autowired
    private PrintingService printingService;

    private PrintService printService;
    private PrinterOutputStream printerOutputStream;

    @Autowired
    private SucursalService sucursalService;

    public Boolean print(String image){
        return printingService.printTicketCaja58mm(image, "bematech");
    }

    public Boolean printVale(PrintVale vale){
        log.warn("imprimiendo...");
        log.warn(vale.toString());
            printingService.printTicketCaja58mm(null, "TICKET1");
        return true;
    }

}
