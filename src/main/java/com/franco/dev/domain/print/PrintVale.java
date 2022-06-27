package com.franco.dev.domain.print;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrintVale {
    private Integer cantidad;
    private Integer vale;
    private String nombre;
}
