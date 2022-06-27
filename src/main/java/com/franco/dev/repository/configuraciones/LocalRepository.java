package com.franco.dev.repository.configuraciones;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LocalRepository extends HelperRepository<Local, Long> {

    default Class<Cargo> getEntityClass() {
        return Cargo.class;
    }
}