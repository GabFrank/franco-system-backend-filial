package com.franco.dev.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbebedPrimaryKey implements Serializable {
    private Long id;
    private Long sucursalId;
}
