package com.franco.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.franco.dev.graphql.configuraciones.publisher.SincronizacionStatusPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import javax.annotation.PostConstruct;
import javax.servlet.Filter;
import java.io.IOException;
import java.util.Collections;

@EnableRetry
@SpringBootApplication
public class FrancoSystemsApplication {

    public final static String SFG_MESSAGE_QUEUE = "test-queue";

    private Logger log = LoggerFactory.getLogger(FrancoSystemsApplication.class);

    @Autowired
    private ObjectMapper objectMapper;

    public static void main(String[] args) throws IOException {
        System.out.println("Iniciando sistema");
        SpringApplication.run(FrancoSystemsApplication.class, args);
    }

    @Bean
    public RestTemplate getResTemplate() {
        return new RestTemplate();
    }

    @Bean
    public SincronizacionStatusPublisher getSinPublisher() {
        return new SincronizacionStatusPublisher();
    }

    /**
     * Register the {@link OpenEntityManagerInViewFilter} so that the
     * GraphQL-Servlet can handle lazy loads during execution.
     *
     * @return
     */
    @Bean
    public Filter OpenFilter() {
        return new OpenEntityManagerInViewFilter();
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> simpleCorsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Collections.singletonList("*"));
        config.setAllowedMethods(Collections.singletonList("*"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @PostConstruct
    public void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
//        propagacionService.verificarResourcesExists();
    }

//	@Bean
//	public void setPrecios(){
//		List<Producto> productoList = this.productoService.findAll2();
//		for(Producto p : productoList){
//			List<Codigo> codigoList = this.codigoService.findByProductoId(p.getId());
//			for(Codigo c : codigoList){
//				List<PrecioPorSucursal> precioPorSucursalList = this.precioPorSucursalService.findByCodigoId(Long.valueOf(1));
//				for(PrecioPorSucursal precioPorSucursal : precioPorSucursalList){
//					if(precioPorSucursal.getSucursal().getId()==1){
//						c.setPrecio(precioPorSucursal.getPrecio());
//						log.info(c.toString());
//						codigoService.save(c);
//					}
//				}
//			}
//		}
//	}

}
