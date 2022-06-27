package com.franco.dev.security;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.language.SourceLocation;

import java.util.List;

public class UnauthenticatedAccessException extends GraphQLException implements GraphQLError {

    public UnauthenticatedAccessException(String message){
        super(message);
    }

    @Override
    public String getMessage() {
        return null;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorClassification getErrorType() {
        return null;
    }
}