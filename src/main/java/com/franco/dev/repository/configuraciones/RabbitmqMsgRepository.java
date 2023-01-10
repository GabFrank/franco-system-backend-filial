package com.franco.dev.repository.configuraciones;

import com.franco.dev.rabbit.dto.RabbitmqMsg;
import com.franco.dev.repository.HelperRepository;

public interface RabbitmqMsgRepository extends HelperRepository<RabbitmqMsg, Long> {

    default Class<RabbitmqMsg> getEntityClass() {
        return RabbitmqMsg.class;
    }
}