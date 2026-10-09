package com.franco.dev.graphql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * El tipo Usuario no declara el campo password.
 *
 * Lo tuvo desde el principio y cualquier operacion que devolviera un Usuario podia pedirlo. Se
 * retiro cuando los clientes dejaron de seleccionarlo (issue #344 del central). El input
 * UsuarioInput lo conserva: es el camino de alta, reseteo y cambio de contrasena.
 *
 * Volver a declararlo no rompe nada a la vista -- el getter de la entidad sigue ahi y el schema
 * arranca igual --, por eso se fija aca.
 */
class TipoUsuarioSinPasswordTest {

    private static final Pattern BLOQUE_USUARIO =
            Pattern.compile("(?:extend\\s+)?type\\s+Usuario\\s*\\{");
    private static final Pattern CAMPO_PASSWORD = Pattern.compile("^password\\s*[:(]");

    @Test
    void elTipoUsuarioNoDeclaraPassword() {
        List<String> bloques = new ArrayList<>();
        List<String> conPassword = new ArrayList<>();

        for (Path archivo : archivos(Paths.get("src", "main", "resources", "graphql"))) {
            String texto = leer(archivo);
            Matcher m = BLOQUE_USUARIO.matcher(texto);
            while (m.find()) {
                String cuerpo = cuerpoDesde(texto, m.end());
                bloques.add(archivo.toString());
                for (String linea : cuerpo.split("\n")) {
                    if (CAMPO_PASSWORD.matcher(linea.trim()).find()) {
                        conPassword.add(archivo.toString());
                    }
                }
            }
        }

        // Sin este chequeo, renombrar el tipo dejaria el test en verde sin haber mirado nada.
        assertFalse(bloques.isEmpty(), "No se encontro ningun bloque 'type Usuario' en el schema");
        if (!conPassword.isEmpty()) {
            fail("El tipo Usuario vuelve a declarar el campo password en: " + conPassword
                    + ". Ningun cliente lo necesita; para escribirlo esta UsuarioInput.");
        }
    }

    /** El cuerpo del bloque, desde despues de la llave que abre hasta la que cierra. */
    private static String cuerpoDesde(String texto, int inicio) {
        int profundidad = 1;
        int i = inicio;
        while (i < texto.length() && profundidad > 0) {
            char c = texto.charAt(i);
            if (c == '{') {
                profundidad++;
            } else if (c == '}') {
                profundidad--;
            }
            i++;
        }
        return texto.substring(inicio, Math.max(inicio, i - 1));
    }

    private static List<Path> archivos(Path raiz) {
        try (Stream<Path> s = Files.walk(raiz)) {
            return s.filter(p -> p.toString().endsWith(".graphqls")).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String leer(Path archivo) {
        try {
            return new String(Files.readAllBytes(archivo), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
