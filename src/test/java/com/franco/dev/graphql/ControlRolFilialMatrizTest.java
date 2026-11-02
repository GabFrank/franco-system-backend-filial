package com.franco.dev.graphql;

import com.franco.dev.graphql.configuraciones.LocalGraphQL;
import com.franco.dev.graphql.empresarial.CargoGraphQL;
import com.franco.dev.graphql.empresarial.SectorGraphQL;
import com.franco.dev.graphql.empresarial.ZonaGraphQL;
import com.franco.dev.graphql.financiero.BancoGraphQL;
import com.franco.dev.graphql.financiero.FormaPagoGraphQL;
import com.franco.dev.graphql.financiero.MonedaBilletesGraphQL;
import com.franco.dev.graphql.financiero.MonedaGraphQL;
import com.franco.dev.graphql.financiero.TipoGastoGraphQL;
import com.franco.dev.graphql.general.BarrioGraphQL;
import com.franco.dev.graphql.general.CiudadGraphQL;
import com.franco.dev.graphql.general.PaisGraphQL;
import com.franco.dev.graphql.impresion.CupsAdminGraphQL;
import com.franco.dev.graphql.impresion.PrintRouterGraphQL;
import com.franco.dev.graphql.operaciones.MotivoDiferenciaPedidoGraphQL;
import com.franco.dev.graphql.operaciones.TransferenciaGraphQL;
import com.franco.dev.graphql.operaciones.TransferenciaItemGraphQL;
import com.franco.dev.graphql.personas.ProveedorDiasVisitaGraphQL;
import com.franco.dev.graphql.personas.ProveedorGraphQL;
import com.franco.dev.graphql.personas.RoleGraphQL;
import com.franco.dev.graphql.personas.UsuarioGraphQL;
import com.franco.dev.graphql.personas.UsuarioRoleGraphQL;
import com.franco.dev.graphql.personas.VendedorGraphQL;
import com.franco.dev.graphql.personas.VendedorProveedorGraphQL;
import com.franco.dev.graphql.productos.FamiliaGraphQL;
import com.franco.dev.graphql.productos.SubfamiliaGraphQL;
import com.franco.dev.graphql.productos.TipoPrecioGraphQL;
import com.franco.dev.graphql.productos.TipoPresentacionGraphQL;
import com.franco.dev.graphql.productos.pdv.PdvCategoriaGraphQL;
import com.franco.dev.graphql.productos.pdv.PdvGrupoGraphQL;
import com.franco.dev.graphql.productos.pdv.PdvGruposProductosGraphQL;
import com.franco.dev.service.seguridad.RolSesionService;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cada mutation protegida del filial llama al control de rol antes que nada, con los roles esperados.
 * La logica del control (ADMIN, roles de la base, sin sesion) la cubre {@code RolSesionServiceTest}.
 */
class ControlRolFilialMatrizTest {

    static final String[] ADMIN = {};

    /** clase -> (mutation -> roles; vacio = solo ADMIN). */
    static Map<Class<?>, Map<String, String[]>> matriz() {
        Map<Class<?>, Map<String, String[]>> m = new LinkedHashMap<>();
        soloAdmin(m, RoleGraphQL.class, "saveRole", "deleteRole");
        soloAdmin(m, UsuarioRoleGraphQL.class, "saveUsuarioRole", "deleteUsuarioRole");
        soloAdmin(m, UsuarioGraphQL.class, "saveUsuario", "deleteUsuario", "saveUsuarioImage");
        soloAdmin(m, TransferenciaGraphQL.class, "saveTransferencia", "deleteTransferencia", "avanzarEtapaTransferencia");
        soloAdmin(m, TransferenciaItemGraphQL.class, "saveTransferenciaItem", "deleteTransferenciaItem");
        Object[][] catalogos = {
                {SectorGraphQL.class, "Sector"}, {ZonaGraphQL.class, "Zona"}, {CargoGraphQL.class, "Cargo"},
                {TipoGastoGraphQL.class, "TipoGasto"}, {BarrioGraphQL.class, "Barrio"}, {CiudadGraphQL.class, "Ciudad"},
                {PaisGraphQL.class, "Pais"}, {PdvCategoriaGraphQL.class, "PdvCategoria"}, {PdvGrupoGraphQL.class, "PdvGrupo"},
                {PdvGruposProductosGraphQL.class, "PdvGruposProductos"}, {LocalGraphQL.class, "Local"},
                {BancoGraphQL.class, "Banco"}, {MonedaGraphQL.class, "Moneda"}, {MonedaBilletesGraphQL.class, "MonedaBillete"},
                {FormaPagoGraphQL.class, "FormaPago"}, {FamiliaGraphQL.class, "Familia"}, {SubfamiliaGraphQL.class, "Subfamilia"},
                {TipoPrecioGraphQL.class, "TipoPrecio"}, {TipoPresentacionGraphQL.class, "TipoPresentacion"},
                {MotivoDiferenciaPedidoGraphQL.class, "MotivoDiferenciaPedido"}, {ProveedorGraphQL.class, "Proveedor"},
                {VendedorGraphQL.class, "Vendedor"}, {VendedorProveedorGraphQL.class, "VendedorProveedor"},
                {ProveedorDiasVisitaGraphQL.class, "ProveedorDiasVisita"}};
        for (Object[] c : catalogos) {
            soloAdmin(m, (Class<?>) c[0], "save" + c[1], "delete" + c[1]);
        }
        Map<String, String[]> cups = new LinkedHashMap<>();
        for (String s : new String[]{"instalarImpresoraCups", "instalarImpresoraSmb", "compartirColaCups", "reactivarCola"}) {
            cups.put(s, new String[]{"CONFIGURACION"});
        }
        m.put(CupsAdminGraphQL.class, cups);
        Map<String, String[]> print = new LinkedHashMap<>();
        print.put("imprimirEnDestino", new String[]{"ANALISIS DE CAJA", "CONFIGURACION"});
        print.put("imprimirEnImpresora", ADMIN);
        print.put("imprimirPruebaEnImpresora", new String[]{"CONFIGURACION"});
        m.put(PrintRouterGraphQL.class, print);
        return m;
    }

    static void soloAdmin(Map<Class<?>, Map<String, String[]>> m, Class<?> c, String... metodos) {
        Map<String, String[]> fila = m.computeIfAbsent(c, k -> new LinkedHashMap<>());
        for (String s : metodos) fila.put(s, ADMIN);
    }

    /** Registra los roles pedidos y corta, para que el resto del metodo no corra. */
    static class GuardEspia extends RolSesionService {
        String[] pedidos;

        @Override
        public void exigirRol(String... roles) {
            pedidos = roles;
            throw new Corte();
        }
    }

    static class Corte extends RuntimeException {
    }

    @TestFactory
    Stream<DynamicTest> cadaMutationPideSusRolesAntesQueNada() {
        List<DynamicTest> tests = new ArrayList<>();
        matriz().forEach((clase, metodos) -> metodos.forEach((nombre, roles) ->
                tests.add(DynamicTest.dynamicTest(clase.getSimpleName() + "." + nombre, () -> {
                    Object resolver = new ObjenesisStd().newInstance(clase);
                    GuardEspia guard = new GuardEspia();
                    ReflectionTestUtils.setField(resolver, "rolSesionService", guard);
                    Method metodo = unico(clase, nombre);
                    Throwable t = assertThrows(InvocationTargetException.class,
                            () -> metodo.invoke(resolver, argumentos(metodo))).getCause();
                    assertTrue(t instanceof Corte, "no llamo al control primero: " + t);
                    assertArrayEquals(roles, guard.pedidos);
                }))));
        return tests.stream();
    }

    /** Una mutation nueva en una clase protegida no puede quedar fuera de la matriz. */
    @Test
    void todaMutationDeUnaClaseProtegidaEstaEnLaMatriz() throws IOException {
        Set<String> mutations = mutationsDelEsquema();
        assertTrue(mutations.size() > 100, "no se leyo el esquema: " + mutations.size());
        List<String> faltan = new ArrayList<>();
        matriz().forEach((clase, metodos) -> {
            for (Method m : clase.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers()) && mutations.contains(m.getName())
                        && !metodos.containsKey(m.getName())) {
                    faltan.add(clase.getSimpleName() + "." + m.getName());
                }
            }
        });
        assertEquals(new ArrayList<>(), faltan);
    }

    static Set<String> mutationsDelEsquema() throws IOException {
        Pattern bloque = Pattern.compile("type\\s+Mutation\\s*\\{(.*?)}", Pattern.DOTALL);
        Pattern campo = Pattern.compile("^\\s*(\\w+)\\s*[(:]", Pattern.MULTILINE);
        Set<String> out = new HashSet<>();
        List<Path> archivos;
        try (Stream<Path> s = Files.walk(Paths.get("src/main/resources/graphql"))) {
            archivos = s.filter(p -> p.toString().endsWith(".graphqls")).collect(Collectors.toList());
        }
        for (Path p : archivos) {
            Matcher b = bloque.matcher(new String(Files.readAllBytes(p), "UTF-8"));
            while (b.find()) {
                // Los argumentos pueden ir en varias lineas: se sacan antes de buscar los campos.
                Matcher c = campo.matcher(b.group(1).replaceAll("\\([^)]*\\)", "()"));
                while (c.find()) out.add(c.group(1));
            }
        }
        return out;
    }

    static Method unico(Class<?> clase, String nombre) {
        List<Method> ms = Arrays.stream(clase.getDeclaredMethods())
                .filter(m -> m.getName().equals(nombre) && Modifier.isPublic(m.getModifiers()))
                .collect(Collectors.toList());
        assertEquals(1, ms.size(), clase.getSimpleName() + "." + nombre);
        return ms.get(0);
    }

    static Object[] argumentos(Method m) {
        return Arrays.stream(m.getParameterTypes()).map(t -> {
            if (!t.isPrimitive()) return null;
            if (t == boolean.class) return false;
            if (t == long.class) return 0L;
            if (t == double.class) return 0d;
            if (t == float.class) return 0f;
            if (t == char.class) return (char) 0;
            if (t == byte.class) return (byte) 0;
            if (t == short.class) return (short) 0;
            return 0;
        }).toArray();
    }
}
