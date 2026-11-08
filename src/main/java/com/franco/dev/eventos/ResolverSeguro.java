package com.franco.dev.eventos;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.InetNameResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Resolver de Reactor Netty que valida la IP en el momento de conectar: la misma direccion que se
 * revisa es la que se usa para abrir el socket, asi un DNS que cambia entre la validacion y la
 * conexion (rebinding) no llega a una IP interna.
 */
public class ResolverSeguro extends AddressResolverGroup<InetSocketAddress> {

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
        return new InetSocketAddressResolver(executor, new Nombres(executor));
    }

    static class Nombres extends InetNameResolver {
        Nombres(EventExecutor executor) {
            super(executor);
        }

        @Override
        protected void doResolve(String host, Promise<InetAddress> promise) {
            try {
                List<InetAddress> l = permitidas(host);
                promise.setSuccess(l.get(0));
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }

        @Override
        protected void doResolveAll(String host, Promise<List<InetAddress>> promise) {
            try {
                promise.setSuccess(permitidas(host));
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }
    }

    static List<InetAddress> permitidas(String host) throws UnknownHostException {
        // Algunas versiones de Reactor Netty pasan "host:puerto"; un IPv6 literal trae mas de un ':'
        if (host != null && host.indexOf(':') > 0 && host.indexOf(':') == host.lastIndexOf(':')) {
            host = host.substring(0, host.indexOf(':'));
        }
        List<InetAddress> l = Arrays.asList(InetAddress.getAllByName(host));
        for (InetAddress a : l) {
            if (DestinoSeguro.bloqueada(a)) {
                throw new UnknownHostException("Destino bloqueado: " + host + " resuelve a una IP interna");
            }
        }
        return l;
    }
}
