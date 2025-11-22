package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuracion.NotificacionUsuario;
import java.util.List;

public class NotificacionUsuarioPage {
    private List<NotificacionUsuario> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    public NotificacionUsuarioPage() {}

    public NotificacionUsuarioPage(List<NotificacionUsuario> content, int pageNumber, int pageSize, long totalElements, int totalPages) {
        this.content = content;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<NotificacionUsuario> getContent() { return content; }
    public int getPageNumber() { return pageNumber; }
    public int getPageSize() { return pageSize; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
}
