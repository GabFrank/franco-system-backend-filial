package com.franco.dev.service.personas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.ConsultaRucResponse;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

@Service
@AllArgsConstructor
public class ClienteService extends CrudService<Cliente, ClienteRepository> {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;  // For JSON parsing

    private final ClienteRepository repository;

    @Override
    public ClienteRepository getRepository() {
        return repository;
    }

    public Cliente findByPersonaId(Long id){
        return repository.findByPersonaId(id);
    }

    public Cliente findByPersonaDocumento(String texto){
        return repository.findByPersonaDocumento(texto);
    }

    public List<Cliente> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByPersona(texto.toUpperCase());
    }

    public ConsultaRucResponse consultaRuc(String ruc) {
        String apiUrl = "https://siyopude.com/ruc/?ruc=" + ruc;

        try {
            // Fetch response as plain text
            String rawResponse = restTemplate.getForObject(apiUrl, String.class);

            // Extract the JSON part by finding the first '{'
            int jsonStartIndex = rawResponse.indexOf("{");
            if (jsonStartIndex == -1) {
                throw new RuntimeException("No JSON found in the response.");
            }

            String jsonResponse = rawResponse.substring(jsonStartIndex);

            // Parse the JSON response
            ConsultaRucResponse rucResponse = objectMapper.readValue(jsonResponse, ConsultaRucResponse.class);
            return rucResponse;

        } catch (IOException e) {
            throw new RuntimeException("Error parsing JSON response: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error while fetching RUC details: " + e.getMessage(), e);
        }
    }

}


