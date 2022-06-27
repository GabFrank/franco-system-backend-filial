package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Combo;
import com.franco.dev.domain.productos.ComboItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ComboItemRepository extends HelperRepository<ComboItem, Long> {

    default Class<ComboItem> getEntityClass() {
        return ComboItem.class;
    }

    public List<ComboItem> findByComboId(Long id);

    public List<ComboItem> findByProductoId(Long id);

}
