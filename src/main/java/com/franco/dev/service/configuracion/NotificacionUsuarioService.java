package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.repository.configuraciones.NotificacionUsuarioRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacionUsuarioService {

    @Autowired
    private NotificacionUsuarioRepository notificacionUsuarioRepository;

    @Transactional
    public boolean marcarComoLeida(Long notificacionUsuarioId) {
        return notificacionUsuarioRepository.findById(notificacionUsuarioId)
                .map(nu -> {
                    nu.setLeida(Boolean.TRUE);
                    nu.setFechaLeida(LocalDateTime.now());
                    notificacionUsuarioRepository.save(nu);
                    return true;
                }).orElse(Boolean.FALSE);
    }

    @Transactional
    public boolean registrarInteraccion(Long notificacionUsuarioId, String accion) {
        return notificacionUsuarioRepository.findById(notificacionUsuarioId)
                .map(nu -> {
                    nu.setInteractuada(Boolean.TRUE);
                    nu.setFechaInteraccion(LocalDateTime.now());
                    nu.setAccionRealizada(accion);
                    notificacionUsuarioRepository.save(nu);
                    return true;
                }).orElse(Boolean.FALSE);
    }

    public Page<NotificacionUsuario> findByUsuarioId(Long usuarioId, String tokenFcm, Pageable pageable) {
        if (tokenFcm != null && !tokenFcm.isEmpty()) {
            return notificacionUsuarioRepository.findAllByUsuarioIdAndTokenFcm(usuarioId, tokenFcm, pageable);
        }
        return notificacionUsuarioRepository.findAllByUsuarioId(usuarioId, pageable);
    }
}
