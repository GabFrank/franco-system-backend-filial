package com.franco.dev.graphql;

import graphql.GraphQLError;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.schema.idl.errors.SchemaProblem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Arma el schema GraphQL completo de la filial con todos los .graphqls, como lo hace kickstart al
 * arrancar. Un tipo referenciado y no definido (por ejemplo un campo nuevo que apunta a un type que
 * nadie declaro) hace fallar este test en vez de tumbar el arranque del filial.
 */
public class SchemaGraphqlCompletoTest {

    private static final Path RAIZ = Paths.get("src/main/resources/graphql");

    /**
     * Todos los archivos juntos en un solo documento: hay .graphqls que son solo comentarios y,
     * parseados de a uno, no son un documento valido.
     */
    private static TypeDefinitionRegistry registro() throws IOException {
        List<Path> archivos;
        try (Stream<Path> s = Files.walk(RAIZ)) {
            archivos = s.filter(p -> p.toString().endsWith(".graphqls")).sorted().collect(Collectors.toList());
        }
        assertFalse(archivos.isEmpty(), "no se encontraron .graphqls en " + RAIZ.toAbsolutePath());
        StringBuilder todo = new StringBuilder();
        for (Path p : archivos) {
            todo.append(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)).append('\n');
        }
        return new SchemaParser().parse(todo.toString());
    }

    /**
     * Errores que el schema ya tenia antes de los sorteos (2026-10-28) y que kickstart tolera al
     * arrancar: campos y argumentos duplicados. Se comparan sin la ubicacion ([@linea:col]), que
     * cambia con cualquier edicion. Cualquier error que no este aca hace fallar el test.
     */
    private static final Set<String> ERRORES_PREEXISTENTES = new HashSet<>(Arrays.asList(
            "The type 'Query' with field 'productoPorSucursalPorProIdSucId' has declared an argument with a non unique name 'Query'",
            "The type 'ConteoMoneda' has declared a field with a non unique name 'sucursalId'",
            "The type 'Cobro' has declared a field with a non unique name 'sucursalId'"));

    @Test
    public void todosLosGraphqlsArmanUnSchemaValido() throws IOException {
        List<String> nuevos = new ArrayList<>();
        try {
            assertNotNull(UnExecutableSchemaGenerator.makeUnExecutableSchema(registro()));
        } catch (SchemaProblem problema) {
            for (GraphQLError e : problema.getErrors()) {
                String sinUbicacion = e.getMessage().replaceAll("\\s*\\[@\\d+:\\d+\\]", "").trim();
                if (!ERRORES_PREEXISTENTES.contains(sinUbicacion)) nuevos.add(e.getMessage());
            }
        }
        assertTrue(nuevos.isEmpty(), "schema GraphQL invalido: " + nuevos);
    }

    @Test
    public void ventaExponeLosTicketsDeSorteo() throws IOException {
        TypeDefinitionRegistry r = registro();
        ObjectTypeDefinition venta = r.getType("Venta", ObjectTypeDefinition.class).orElseThrow(AssertionError::new);
        assertTrue(venta.getFieldDefinitions().stream().anyMatch(f -> f.getName().equals("sorteoTickets")));
        ObjectTypeDefinition info = r.getType("SorteoTicketInfo", ObjectTypeDefinition.class)
                .orElseThrow(() -> new AssertionError("falta type SorteoTicketInfo"));
        assertTrue(info.getFieldDefinitions().stream().anyMatch(f -> f.getName().equals("nombreSorteo")));
        assertTrue(info.getFieldDefinitions().stream().anyMatch(f -> f.getName().equals("cantidad")));
        boolean argumento = r.objectTypeExtensions().getOrDefault("Mutation", Collections.emptyList()).stream()
                .flatMap(t -> t.getFieldDefinitions().stream())
                .filter(f -> f.getName().equals("saveVenta"))
                .anyMatch(f -> f.getInputValueDefinitions().stream()
                        .anyMatch(a -> a.getName().equals("sorteoCliente")));
        assertTrue(argumento, "saveVenta sin argumento sorteoCliente");
    }
}
