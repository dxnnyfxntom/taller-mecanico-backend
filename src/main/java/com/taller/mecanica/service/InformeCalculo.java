package com.taller.mecanica.service;

import com.taller.mecanica.exception.BusinessException;
import com.taller.mecanica.model.entity.AdicionalOrden;
import com.taller.mecanica.model.entity.DetalleInspeccionInforme;
import com.taller.mecanica.model.entity.PagoOrden;
import com.taller.mecanica.model.enums.AccionRealizada;
import com.taller.mecanica.model.enums.CategoriaItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class InformeCalculo {

    private static final Set<AccionRealizada> ACCIONES_CONDICIONALES = Set.of(
            AccionRealizada.SOLO_VERIFICADO, AccionRealizada.RELLENADO,
            AccionRealizada.CAMBIADO, AccionRealizada.CALIBRADO, AccionRealizada.NO_REQUERIDO);

    private InformeCalculo() {
    }

    public static LocalDateTime fechaEstimadaEntrega(LocalDateTime ingreso, int horas) {
        return ingreso.plusHours(horas);
    }

    public static int kilometrajeProximo(int kilometrajeIngreso, int garantiaKm) {
        long proximo = (long) kilometrajeIngreso + garantiaKm;
        if (proximo > Integer.MAX_VALUE) {
            throw new BusinessException("El kilometraje mas la garantia supera el limite admitido");
        }
        return (int) proximo;
    }

    public static LocalDateTime fechaProximo(LocalDateTime ingreso, int meses) {
        return ingreso.plusMonths(meses);
    }

    public static BigDecimal totalAdicionales(List<AdicionalOrden> adicionales) {
        if (adicionales == null) {
            return BigDecimal.ZERO;
        }
        return adicionales.stream()
                .filter(AdicionalOrden::seCobra)
                .map(a -> cero(a.getSubtotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal totalNoCobrado(List<AdicionalOrden> adicionales) {
        if (adicionales == null) {
            return BigDecimal.ZERO;
        }
        return adicionales.stream()
                .filter(a -> Boolean.TRUE.equals(a.getAprobadoPorCliente()) && !Boolean.TRUE.equals(a.getCobraArandela()))
                .map(a -> cero(a.getSubtotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal totalOrden(BigDecimal precioPaquete, List<AdicionalOrden> adicionales) {
        return cero(precioPaquete).add(totalAdicionales(adicionales));
    }

    public static BigDecimal totalPagado(List<PagoOrden> pagos) {
        if (pagos == null) {
            return BigDecimal.ZERO;
        }
        return pagos.stream()
                .map(p -> cero(p.getMonto()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal saldoPendiente(BigDecimal totalOrden, BigDecimal totalPagado) {
        return cero(totalOrden).subtract(cero(totalPagado));
    }

    public static BigDecimal subtotal(int cantidad, BigDecimal precioUnitario) {
        return cero(precioUnitario)
                .multiply(BigDecimal.valueOf(cantidad))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean accionPermitida(DetalleInspeccionInforme detalle, AccionRealizada accion) {
        if (accion == null) {
            return false;
        }
        if (detalle.getCategoria() == CategoriaItem.CORTESIA || !Boolean.TRUE.equals(detalle.getEsCondicional())) {
            return accion == AccionRealizada.COMPLETADO;
        }
        return ACCIONES_CONDICIONALES.contains(accion);
    }

    public static void validarCierre(String escaneoFinalResultado, List<DetalleInspeccionInforme> detalles,
                                     String fichaTecnicaUrl) {
        if (escaneoFinalResultado == null || escaneoFinalResultado.isBlank()) {
            throw new BusinessException("Registre el escaneo final antes de cerrar la orden");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new BusinessException("La orden no tiene un checklist configurado");
        }
        List<String> pendientes = detalles.stream()
                .filter(d -> !accionPermitida(d, d.getAccionRealizada())
                        || (d.getAccionRealizada() != AccionRealizada.NO_REQUERIDO && d.getEstadoComponente() == null))
                .map(DetalleInspeccionInforme::getDescripcionTarea)
                .toList();
        if (!pendientes.isEmpty()) {
            throw new BusinessException("Registre un resultado para estos puntos del checklist: "
                    + String.join(" | ", pendientes));
        }
        boolean requiereFicha = detalles.stream().anyMatch(d -> Boolean.TRUE.equals(d.getRequiereFichaTecnica())
                && d.getAccionRealizada() != AccionRealizada.NO_REQUERIDO);
        if (requiereFicha && (fichaTecnicaUrl == null || fichaTecnicaUrl.isBlank())) {
            throw new BusinessException("Adjunte la ficha tecnica requerida antes de cerrar la orden");
        }
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
