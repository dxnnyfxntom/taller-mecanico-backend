package com.taller.mecanica.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AjusteStockRequest(
        @NotNull @Min(0) Integer stockEsperado,
        @NotNull @Min(0) Integer stockNuevo,
        @NotBlank @Size(max = 250) String motivo
) {
}
