package com.franco.dev.repository.configuraciones;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.repository.HelperRepository;
import java.time.LocalDateTime;

public interface NotificacionRepository extends HelperRepository<Notificacion, Long> {

	Long deleteByCreadoEnBefore(LocalDateTime fecha);

}
