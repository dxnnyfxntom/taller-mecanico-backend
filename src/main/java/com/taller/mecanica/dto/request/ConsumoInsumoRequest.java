package com.taller.mecanica.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConsumoInsumoRequest(
        @NotNull(message = "La clave de operacion es obligatoria") UUID claveOperacion,

        @NotNull(message = "El insumo es obligatorio")
        Long insumoId,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        Integer cantidad
) {
}
