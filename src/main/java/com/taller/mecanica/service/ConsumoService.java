package com.taller.mecanica.service;

import com.taller.mecanica.exception.BusinessException;
import com.taller.mecanica.exception.ResourceNotFoundException;
import com.taller.mecanica.model.entity.ConsumoInsumo;
import com.taller.mecanica.model.entity.InformeVehiculo;
import com.taller.mecanica.model.entity.Insumo;
import com.taller.mecanica.model.enums.CategoriaInsumo;
import com.taller.mecanica.dto.request.ConsumoInsumoRequest;
import com.taller.mecanica.dto.response.InformeDetalleResponse;
import com.taller.mecanica.dto.mapper.InformeMapper;
import com.taller.mecanica.repository.ConsumoInsumoRepository;
import com.taller.mecanica.repository.InformeVehiculoRepository;
import com.taller.mecanica.repository.InsumoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Isolation;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsumoService {

    private final InformeVehiculoRepository informeRepository;
    private final ConsumoInsumoRepository consumoRepository;
    private final InsumoRepository insumoRepository;

    public List<InformeDetalleResponse.Consumo> listar(Long informeId) {
        cargarInforme(informeId);
        return consumoRepository.findByInformeIdAndRevertidoEnIsNullOrderByIdAsc(informeId).stream()
                .map(InformeMapper::toConsumo)
                .toList();
    }

    @Transactional
    public InformeDetalleResponse.Consumo agregar(Long informeId, ConsumoInsumoRequest request) {
        InformeVehiculo informe = informeRepository.bloquear(informeId);
        String clave = request.claveOperacion().toString();
        ConsumoInsumo previo = consumoRepository.findByInformeIdAndClaveOperacion(informeId, clave).orElse(null);
        if (previo != null) {
            if (previo.getRevertidoEn() != null) {
                throw new BusinessException("El consumo de esta operacion ya fue revertido");
            }
            if (!previo.getInsumo().getId().equals(request.insumoId())
                    || !previo.getCantidad().equals(request.cantidad())) {
                throw new BusinessException("La clave ya pertenece a un consumo con otros datos");
            }
            return InformeMapper.toConsumo(previo);
        }
        InformeService.validarEditable(informe);

        Insumo insumo = insumoRepository.buscarParaActualizar(request.insumoId())
                .orElseThrow(() -> ResourceNotFoundException.de("Insumo", request.insumoId()));
        if (insumo.getStockActual() < request.cantidad()) {
            throw new BusinessException("Stock insuficiente de '" + insumo.getNombre()
                    + "'. Disponible: " + insumo.getStockActual());
        }
        insumo.setStockActual(insumo.getStockActual() - request.cantidad());
        insumoRepository.save(insumo);

        ConsumoInsumo consumo = new ConsumoInsumo();
        consumo.setInforme(informe);
        consumo.setClaveOperacion(clave);
        consumo.setInsumo(insumo);
        consumo.setCantidad(request.cantidad());
        consumo.setPrecioUnitario(insumo.getPrecioVenta());
        consumo.setUrlFichaTecnicaPdf(insumo.getUrlFichaTecnicaPdf());
        ConsumoInsumo guardado = consumoRepository.save(consumo);

        if (insumo.getCategoria() == CategoriaInsumo.ACEITE
                && (informe.getFichaTecnicaPdfUrl() == null || informe.getFichaTecnicaPdfUrl().isBlank())) {
            informe.setFichaTecnicaPdfUrl(insumo.getUrlFichaTecnicaPdf());
            informeRepository.save(informe);
        }
        return InformeMapper.toConsumo(guardado);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void eliminar(Long consumoId) {
        Long informeId = consumoRepository.buscarInformeId(consumoId)
                .orElseThrow(() -> ResourceNotFoundException.de("Consumo de insumo", consumoId));
        InformeVehiculo informe = informeRepository.bloquear(informeId);
        ConsumoInsumo consumo = consumoRepository.findById(consumoId)
                .orElseThrow(() -> ResourceNotFoundException.de("Consumo de insumo", consumoId));
        if (consumo.getRevertidoEn() != null) {
            return;
        }
        InformeService.validarEditable(informe);

        Insumo insumo = insumoRepository.buscarParaActualizar(consumo.getInsumo().getId())
                .orElseThrow(() -> ResourceNotFoundException.de("Insumo", consumo.getInsumo().getId()));
        long nuevoStock = (long) insumo.getStockActual() + consumo.getCantidad();
        if (nuevoStock > Integer.MAX_VALUE) {
            throw new BusinessException("La reposicion supera el limite de stock");
        }
        insumo.setStockActual((int) nuevoStock);
        insumoRepository.save(insumo);
        consumo.setRevertidoEn(LocalDateTime.now());
    }

    private InformeVehiculo cargarInforme(Long informeId) {
        return informeRepository.findById(informeId)
                .orElseThrow(() -> ResourceNotFoundException.de("Informe", informeId));
    }
}
