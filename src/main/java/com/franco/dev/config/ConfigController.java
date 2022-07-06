package com.franco.dev.config;


import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.configuracion.LocalService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.Serializable;
import java.util.List;

@RestController
@RequestMapping("/config")
@CrossOrigin
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SucursalService service;

    @Autowired
    private Environment env;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private LocalService localService;


    @PostMapping
    @RequestMapping(value = "/verificar")
    public Boolean verficar() {
        Boolean verificado = false;
//        if (env.getProperty("ipServidorCentral") != null) {
//            String url = "http://" + env.getProperty("ipServidorCentral") + "/config/verificar";
//            log.info("Verificando conexion");
//            try {
//                verificado = restTemplate.getForObject(url, Boolean.class);
//            } catch (RestClientException e) {
//                verificado = false;
//            }
//        }
        return propagacionService.initDb();
    }

    @PostMapping
    @RequestMapping(value = "/sucursales")
    public List<Sucursal> getSucursales() {
        String url = "http://" + env.getProperty("ipServidorCentral") + "/config/sucursales";
        log.info("solicitando sucursales a " + url);
        SucursalesDto sucursales = restTemplate.getForObject(url, SucursalesDto.class);
        return sucursales.getSucursalList();
    }

    @PostMapping
    @RequestMapping(value = "/solicitardb")
    public Boolean solicitarDb() {
        String url = "http://" + env.getProperty("ipServidorCentral") + "/config/solicitardb";
        log.info("solicitando base de datos a " + url);
        propagacionService.solicitarDB();
        return true;
    }

    @PostMapping
    @RequestMapping(value = "/isconfigured")
    public ResponseEntity<Boolean> isConfigured() {
        List<Local> foundLocales = localService.findAll();
        Boolean isLocal = false;
        if (foundLocales.size() > 0) {
            isLocal = true;
        }
        return ResponseEntity.ok(isLocal);
    }
}

@Data
class SucursalesDto implements Serializable {
    private List<Sucursal> sucursalList;
}
