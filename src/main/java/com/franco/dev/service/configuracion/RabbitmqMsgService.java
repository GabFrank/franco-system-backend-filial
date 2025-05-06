package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.rabbit.dto.RabbitmqMsg;
import com.franco.dev.repository.configuraciones.LocalRepository;
import com.franco.dev.repository.configuraciones.RabbitmqMsgRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class RabbitmqMsgService extends CrudService<RabbitmqMsg, RabbitmqMsgRepository> {

    private final RabbitmqMsgRepository repository;

    @Override
    public RabbitmqMsgRepository getRepository() {
        return repository;
    }

    public List<RabbitmqMsg> findAll(){
        return repository.findAllByOrderByIdAsc();
    }

    @Override
    public RabbitmqMsg save(RabbitmqMsg entity) {
        RabbitmqMsg e = super.save(entity);
        return e;
    }
}