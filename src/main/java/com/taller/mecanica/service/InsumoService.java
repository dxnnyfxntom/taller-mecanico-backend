package com.taller.mecanica.service;

import com.taller.mecanica.exception.BusinessException;
import com.taller.mecanica.exception.ResourceNotFoundException;
import com.taller.mecanica.model.entity.Insumo;
import com.taller.mecanica.model.enums.CategoriaInsumo;
import com.taller.mecanica.repository.ConsumoInsumoRepository;
import com.taller.mecanica.repository.InsumoRepository;
import com.taller.mecanica.dto.request.AjusteStockRequest;
import com.taller.mecanica.model.entity.AjusteStock;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsumoService {

    private final InsumoRepository insumoRepository;
    private final ConsumoInsumoRepository consumoRepository;
    private final FileStorageService fileStorageService;
    private final EntityManager entityManager;

    public List<Insumo> listar(CategoriaInsumo categoria, String q) {
        if (q != null && !q.isBlank()) {
            String texto = q.trim().toLowerCase();
            return insumoRepository.findAllByOrderByNombreAsc().stream()
                    .filter(i -> i.getNombre().toLowerCase().contains(texto)
                            || i.getCodigo().toLowerCase().contains(texto))
                    .toList();
        }
        if (categoria != null) {
            return insumoRepository.findByCategoriaOrderByNombreAsc(categoria);
        }
        return insumoRepository.findAllByOrderByNombreAsc();
    }

    public Insumo obtener(Long id) {
        return insumoRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Insumo", id));
    }

    public List<Insumo> alertasStock() {
        return insumoRepository.findConStockBajo();
    }

    @Transactional
    public Insumo crear(Insumo datos) {
        normalizar(datos);
        if (insumoRepository.existsByCodigoIgnoreCase(datos.getCodigo())) {
            throw new BusinessException("Ya existe un insumo con el codigo " + datos.getCodigo());
        }
        datos.setId(null);
        return insumoRepository.save(datos);
    }

    @Transactional
    public Insumo actualizar(Long id, Insumo datos) {
        Insumo insumo = bloquear(id);
        normalizar(datos);
        if (insumoRepository.existsByCodigoIgnoreCaseAndIdNot(datos.getCodigo(), id)) {
            throw new BusinessException("Ya existe otro insumo con el codigo " + datos.getCodigo());
        }
        insumo.setCodigo(datos.getCodigo());
        insumo.setNombre(datos.getNombre());
        insumo.setCategoria(datos.getCategoria());
        insumo.setStockMinimo(datos.getStockMinimo());
        insumo.setPrecioVenta(datos.getPrecioVenta());
        return insumo;
    }

    @Transactional
    public Insumo ajustarStock(Long id, AjusteStockRequest request) {
        Insumo insumo = bloquear(id);
        if (!insumo.getStockActual().equals(request.stockEsperado())) {
            throw new BusinessException("El stock cambio. Actualice el inventario antes de ajustar");
        }
        if (insumo.getStockActual().equals(request.stockNuevo())) {
            return insumo;
        }
        entityManager.persist(new AjusteStock(id, insumo.getStockActual(), request.stockNuevo(), request.motivo().trim()));
        insumo.setStockActual(request.stockNuevo());
        return insumo;
    }

    @Transactional
    public Insumo subirFichaTecnica(Long id, MultipartFile archivo) {
        Insumo insumo = bloquear(id);
        insumo.setUrlFichaTecnicaPdf(fileStorageService.guardarFichaTecnica("insumos", id, archivo));
        return insumo;
    }

    @Transactional
    public void eliminar(Long id) {
        Insumo insumo = bloquear(id);
        if (consumoRepository.existsByInsumoId(id)) {
            throw new BusinessException("El insumo tiene consumos registrados en ordenes y no puede eliminarse");
        }
        insumoRepository.delete(insumo);
    }

    private Insumo bloquear(Long id) {
        return insumoRepository.buscarParaActualizar(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Insumo", id));
    }

    private void normalizar(Insumo insumo) {
        if (insumo.getCodigo() != null) {
            insumo.setCodigo(insumo.getCodigo().trim().toUpperCase());
        }
        if (insumo.getNombre() != null) {
            insumo.setNombre(insumo.getNombre().trim());
        }
    }
}
