package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.CajaBalance;
import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.CajaObservacion;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.GastoDetalleService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.operaciones.VentaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.franco.dev.service.seguridad.RolSesionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class CajaResolver implements GraphQLResolver<PdvCaja> {

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private SucursalService sucursalService;

    private final Logger log = LoggerFactory.getLogger(CajaResolver.class);



//    @Autowired
//    private CajaDetalleService cajaDetalleService;

//    public List<CajaDetalle> cajaDetalleList(Caja e){
//        return cajaDetalleService.findByCajaId(e.getId());
//    }

//    totalGs: Float
//    totalRs: Float
//    totalDs: Float
//    totalTarjeta: Float
//    totalDescuento: Float
//    totalAumento: Float
//    totalRetiro: Float
//    totalGasto: Float

    public List<CajaObservacion> cajaObservacionList(PdvCaja caja) {
        return Collections.emptyList();
    }

    @Autowired
    private RolSesionService rolSesionService;

    /** null (no error) a quien no es dueño ni tiene el rol: un error haria que el proxy del central descarte la caja. */
    public CajaBalance balance(PdvCaja e){
        if (!rolSesionService.esDuenioOTieneRol(PdvCajaService.duenioId(e), RolSesionService.ANALISIS_DE_CAJA)) {
            return null;
        }
        return pdvCajaService.getBalance(e.getId());
    }

    /** Mismo criterio que balance: null a quien no es dueño ni tiene el rol. Idem en PdvCajaResolver. */
    public Conteo conteoApertura(PdvCaja e) {
        return puedeVerDetalle(e) ? e.getConteoApertura() : null;
    }

    public Conteo conteoCierre(PdvCaja e) {
        return puedeVerDetalle(e) ? e.getConteoCierre() : null;
    }

    private boolean puedeVerDetalle(PdvCaja e) {
        if (rolSesionService.esDuenioOTieneRol(PdvCajaService.duenioId(e), RolSesionService.ANALISIS_DE_CAJA)) return true;
        log.warn("[PdvCaja] conteos de la caja {} ocultos: la sesion no es del dueño ni tiene el rol", e.getId());
        return false;
    }

    public Sucursal sucursal(PdvCaja e) { return sucursalService.findById(e.getSucursalId()).orElse(null);}

}
