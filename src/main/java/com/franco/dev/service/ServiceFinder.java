package com.franco.dev.service;

import com.franco.dev.repository.HelperRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

@Service
public class ServiceFinder {

    private final ApplicationContext applicationContext;

    @Autowired
    public ServiceFinder(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Object findServiceByName(String serviceName) {
        if(applicationContext!=null){
            Map<String, Object> services = applicationContext.getBeansWithAnnotation(Service.class);
            return services.get(serviceName);
        }
        return null;
    }

    public static <T, Repository extends HelperRepository<T, Long>> CrudService<T, Repository> getCrudService(Class<T> entityClass) {
        try {
            // Get the generic type of the CrudService class
            Type[] genericTypes = CrudService.class.getGenericInterfaces();
            ParameterizedType type = (ParameterizedType) genericTypes[0];
            // Get the actual types of the generic parameters
            Class<T> serviceType = (Class<T>) ((ParameterizedType)type.getActualTypeArguments()[0]).getRawType();
            Class<Repository> repositoryType = (Class<Repository>) type.getActualTypeArguments()[1];
            // Get the CrudService implementation for the given entity class
            Class<?> serviceImpl = Class.forName(serviceType.getName() + "Service");
            // Create an instance of the services
            return (CrudService<T, Repository>) serviceImpl.newInstance();
        } catch (Exception e) {
            // Handle exception
            e.printStackTrace();
            return null;
        }
    }

}