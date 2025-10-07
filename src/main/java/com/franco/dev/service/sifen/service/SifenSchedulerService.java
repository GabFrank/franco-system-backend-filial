package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.LoteDE;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@EnableScheduling
public class SifenSchedulerService {

    @Value("${sifen.scheduler.enabled:true}")
    private Boolean schedulerEnabled;

    private final SifenService sifenService;

    public SifenSchedulerService(SifenService sifenService) {
        this.sifenService = sifenService;
    }

    
}
