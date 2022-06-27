package com.franco.dev.graphql.productos.exceptions;

import com.franco.dev.domain.productos.Producto;
import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.List;

public class ProductoDescripcionYaExisteException extends RuntimeException implements GraphQLError {

    public ProductoDescripcionYaExisteException(String message){
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
