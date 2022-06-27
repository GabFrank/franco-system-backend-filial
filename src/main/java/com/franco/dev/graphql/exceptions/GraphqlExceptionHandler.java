package com.franco.dev.graphql.exceptions;

import graphql.GraphQLException;
import graphql.GraphqlErrorException;
import graphql.kickstart.spring.error.ThrowableGraphQLError;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Component
public class GraphqlExceptionHandler {

    @ExceptionHandler(GraphQLException.class)
    public ThrowableGraphQLError handle(GraphQLException e){
        return new ThrowableGraphQLError(e);
    }

//    @ExceptionHandler(RuntimeException.class)
//    public ThrowableGraphQLError handle(RuntimeException e){
//        GraphQLException error = new GraphQLException(e.getMessage());
//        return new ThrowableGraphQLError(error);
//    }
}
