package com.franco.dev.dto.factura;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacturaLegalItemDTO {

    private Long productoId;

    @NotBlank(message = "La descripción del item es requerida")
    private String descripcion;

    @NotNull(message = "La cantidad es requerida")
    @Positive(message = "La cantidad debe ser mayor a 0")
    private Double cantidad;

    @NotNull(message = "El precio unitario es requerido")
    @Min(value = 0, message = "El precio unitario no puede ser negativo")
    private Double precioUnitario;

    @Min(value = 0, message = "El total no puede ser negativo")
    private Double total;

    private String unidadMedida;

    @Min(value = 0, message = "El IVA no puede ser negativo")
    private Integer iva;
}

