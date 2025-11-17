package com.franco.dev.dto.factura;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearFacturaLegalRequestDTO {

    @NotNull(message = "El ID del timbrado detalle es requerido")
    private Long timbradoDetalleId;

    private Long cajaId;

    private Long clienteId;

    @NotBlank(message = "El nombre del cliente es requerido")
    @Size(max = 255, message = "El nombre no puede exceder 255 caracteres")
    private String nombre;

    @NotBlank(message = "El RUC es requerido")
    @Size(max = 20, message = "El RUC no puede exceder 20 caracteres")
    private String ruc;

    @Size(max = 500, message = "La dirección no puede exceder 500 caracteres")
    private String direccion;

    private String email;

    private Boolean viaTributaria = false;

    private Boolean credito = false;

    private LocalDateTime fecha;

    @Valid
    @NotNull(message = "La lista de items es requerida")
    @Size(min = 1, message = "Debe haber al menos un item en la factura")
    private List<FacturaLegalItemDTO> items;

    // Campos de IVA y totales
    @Min(value = 0, message = "El IVA parcial 0% no puede ser negativo")
    private Double ivaParcial0 = 0.0;

    @Min(value = 0, message = "El IVA parcial 5% no puede ser negativo")
    private Double ivaParcial5 = 0.0;

    @Min(value = 0, message = "El IVA parcial 10% no puede ser negativo")
    private Double ivaParcial10 = 0.0;

    @Min(value = 0, message = "El total parcial 0% no puede ser negativo")
    private Double totalParcial0 = 0.0;

    @Min(value = 0, message = "El total parcial 5% no puede ser negativo")
    private Double totalParcial5 = 0.0;

    @Min(value = 0, message = "El total parcial 10% no puede ser negativo")
    private Double totalParcial10 = 0.0;

    @NotNull(message = "El total final es requerido")
    @Min(value = 0, message = "El total final no puede ser negativo")
    private Double totalFinal;

    @Min(value = 0, message = "El descuento no puede ser negativo")
    private Double descuento = 0.0;

    // Campos para moneda extranjera (opcionales)
    private String monedaExtranjera;
    private Double tipoCambio;
}

