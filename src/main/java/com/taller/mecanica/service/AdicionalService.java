package com.taller.mecanica.service;

import com.taller.mecanica.exception.ResourceNotFoundException;
import com.taller.mecanica.model.entity.AdicionalOrden;
import com.taller.mecanica.model.entity.InformeVehiculo;
import com.taller.mecanica.dto.request.AdicionalRequest;
import com.taller.mecanica.dto.response.InformeDetalleResponse;
import com.taller.mecanica.dto.mapper.InformeMapper;
import com.taller.mecanica.repository.AdicionalOrdenRepository;
import com.taller.mecanica.repository.InformeVehiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.transaction.annotation.Isolation;
import com.taller.mecanica.exception.BusinessException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdicionalService {

    private final InformeVehiculoRepository informeRepository;
    private final AdicionalOrdenRepository adicionalRepository;

    public List<InformeDetalleResponse.Adicional> listar(Long informeId) {
        cargarInforme(informeId);
        return adicionalRepository.findByInformeIdOrderByIdAsc(informeId).stream()
                .map(InformeMapper::toAdicional)
                .toList();
    }

    @Transactional
    public InformeDetalleResponse.Adicional agregar(Long informeId, AdicionalRequest request) {
        InformeVehiculo informe = informeRepository.bloquear(informeId);
        InformeService.validarEditable(informe);
        AdicionalOrden adicional = new AdicionalOrden();
        adicional.setInforme(informe);
        aplicar(adicional, request);
        return InformeMapper.toAdicional(adicionalRepository.save(adicional));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InformeDetalleResponse.Adicional actualizar(Long id, AdicionalRequest request) {
        InformeVehiculo informe = bloquearOrdenDelAdicional(id);
        InformeService.validarEditable(informe);
        AdicionalOrden adicional = cargar(id);
        aplicar(adicional, request);
        validarImporte(informe, null);
        return InformeMapper.toAdicional(adicionalRepository.save(adicional));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InformeDetalleResponse.Adicional cambiarAprobacion(Long id, boolean aprobado) {
        InformeVehiculo informe = bloquearOrdenDelAdicional(id);
        InformeService.validarEditable(informe);
        AdicionalOrden adicional = cargar(id);
        adicional.setAprobadoPorCliente(aprobado);
        validarImporte(informe, null);
        return InformeMapper.toAdicional(adicionalRepository.save(adicional));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void eliminar(Long id) {
        InformeVehiculo informe = bloquearOrdenDelAdicional(id);
        InformeService.validarEditable(informe);
        AdicionalOrden adicional = cargar(id);
        validarImporte(informe, id);
        adicionalRepository.delete(adicional);
    }

    private InformeVehiculo bloquearOrdenDelAdicional(Long id) {
        Long informeId = adicionalRepository.buscarInformeId(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Adicional", id));
        return informeRepository.bloquear(informeId);
    }

    private void validarImporte(InformeVehiculo informe, Long adicionalEliminado) {
        List<AdicionalOrden> adicionales = informe.getAdicionales().stream()
                .filter(a -> !a.getId().equals(adicionalEliminado))
                .toList();
        BigDecimal total = InformeCalculo.totalOrden(informe.getPrecioPaquete(), adicionales);
        if (total.compareTo(InformeCalculo.totalPagado(informe.getPagos())) < 0) {
            throw new BusinessException("El cambio deja el total por debajo de lo pagado. Revise los pagos registrados");
        }
    }

    private void aplicar(AdicionalOrden adicional, AdicionalRequest request) {
        adicional.setDescripcion(request.descripcion().trim());
        adicional.setTipo(request.tipo());
        adicional.setCantidad(request.cantidad());
        adicional.setPrecioUnitario(request.precioUnitario());
        BigDecimal subtotal = InformeCalculo.subtotal(request.cantidad(), request.precioUnitario());
        if (subtotal.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new BusinessException("El subtotal supera el importe maximo admitido");
        }
        adicional.setSubtotal(subtotal);
        adicional.setAprobadoPorCliente(Boolean.TRUE.equals(request.aprobadoPorCliente()));
        adicional.setCobraArandela(request.cobraArandela() == null || request.cobraArandela());
    }

    private AdicionalOrden cargar(Long id) {
        return adicionalRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Adicional", id));
    }

    private InformeVehiculo cargarInforme(Long informeId) {
        return informeRepository.findById(informeId)
                .orElseThrow(() -> ResourceNotFoundException.de("Informe", informeId));
    }
}
