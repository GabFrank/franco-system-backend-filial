package com.franco.dev.service;

import com.franco.dev.repository.HelperRepository;

public interface ICrudService <R extends HelperRepository>{

    R getRepository();

}
