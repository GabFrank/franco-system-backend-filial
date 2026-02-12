package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.graphql.administrativo.input.MarcacionInput;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class MarcacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MarcacionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private com.franco.dev.service.personas.PersonaService personaService;

    @Autowired(required = false)
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private ImpresionService impresionService;

    public Optional<Marcacion> marcacion(Long id) {
        return service.findById(id);
    }

    public List<Marcacion> marcaciones(Integer page, Integer size) {
        if (page == null)
            page = 0;
        if (size == null)
            size = 10;
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Marcacion> marcacionesPorUsuario(Long usuarioId, String fechaInicio, String fechaFin, Integer page,
            Integer size) {
        if (page == null)
            page = 0;
        if (size == null)
            size = 10;
        if (fechaInicio != null && fechaFin != null) {
            return service.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin, page, size);
        }
        return service.findByUsuarioId(usuarioId, page, size);
    }

    public Marcacion saveMarcacion(MarcacionInput input) {
        if (input.getUsuarioId() != null && input.getEmbedding() != null && !input.getEmbedding().isEmpty()) {
            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findById(input.getUsuarioId()).orElse(null);
            if (usuario != null && usuario.getPersona() != null) {
                String storedEmbeddingJson = usuario.getPersona().getEmbedding();
                if (storedEmbeddingJson != null && !storedEmbeddingJson.isEmpty()) {
                    try {
                        if (objectMapper == null)
                            objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        List<Double> storedEmbedding = objectMapper.readValue(storedEmbeddingJson,
                                new com.fasterxml.jackson.core.type.TypeReference<List<Double>>() {
                                });

                        double similarity = cosineSimilarity(input.getEmbedding(), storedEmbedding);
                        if (similarity < 0.6) {
                            throw new graphql.GraphQLException("Verificación facial fallida: El rostro no coincide ("
                                    + String.format("%.2f", similarity * 100) + "% similitud)");
                        }
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                        throw new graphql.GraphQLException("Error verificando rostro: datos biometricos corruptos.");
                    }
                } else {
                    try {
                        if (objectMapper == null)
                            objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        String newEmbeddingJson = objectMapper.writeValueAsString(input.getEmbedding());
                        usuario.getPersona().setEmbedding(newEmbeddingJson);
                        personaService.save(usuario.getPersona());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        ModelMapper m = new ModelMapper();
        m.getConfiguration().setSkipNullEnabled(true);
        Marcacion e = new Marcacion();

        if (input.getId() != null) {
            Optional<Marcacion> existing = service.findById(input.getId());
            if (existing.isPresent()) {
                e = existing.get();
            } else {
                throw new graphql.GraphQLException(
                        "No se encontró la marcación con ID " + input.getId() + ".");
            }
        }

        m.map(input, e);

        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        if (input.getSucursalEntradaId() != null) {
            e.setSucursalEntrada(sucursalService.findById(input.getSucursalEntradaId()).orElse(null));
        }

        if (input.getSucursalSalidaId() != null) {
            e.setSucursalSalida(sucursalService.findById(input.getSucursalSalidaId()).orElse(null));
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        if (input.getFechaEntrada() != null) {
            e.setFechaEntrada(LocalDateTime.parse(input.getFechaEntrada(), formatter));
        }
        if (input.getFechaSalida() != null) {
            e.setFechaSalida(LocalDateTime.parse(input.getFechaSalida(), formatter));
        }

        return service.save(e);
    }

    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }

        if (normA == 0 || normB == 0)
            return 0.0;

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public String imprimirReporteMarcaciones(Long usuarioId, String fechaInicio, String fechaFin,
            Long usuarioResponsableId) {
        com.franco.dev.domain.personas.Usuario usuarioReporte = null;

        if (usuarioResponsableId != null) {
            usuarioReporte = usuarioService.findById(usuarioResponsableId).orElse(null);
        }

        List<Marcacion> marcacionList;
        if (usuarioId != null && fechaInicio != null && fechaFin != null) {
            marcacionList = service.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin, 0,
                    Integer.MAX_VALUE);
        } else if (usuarioId != null) {
            marcacionList = service.findByUsuarioId(usuarioId, 0, Integer.MAX_VALUE);
        } else {
            marcacionList = service.findAll2();
        }

        return impresionService.imprimirMarcaciones(marcacionList, fechaInicio, fechaFin, usuarioReporte);
    }

}
