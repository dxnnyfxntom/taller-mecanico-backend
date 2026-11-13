package com.taller.mecanica.repository;

import com.taller.mecanica.model.entity.InformeVehiculo;
import com.taller.mecanica.model.enums.EstadoInforme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.taller.mecanica.exception.ResourceNotFoundException;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InformeVehiculoRepository extends JpaRepository<InformeVehiculo, Long>, JpaSpecificationExecutor<InformeVehiculo> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InformeVehiculo i where i.id = :id")
    Optional<InformeVehiculo> buscarParaActualizar(@Param("id") Long id);

    default InformeVehiculo bloquear(Long id) {
        return buscarParaActualizar(id).orElseThrow(() -> ResourceNotFoundException.de("Informe", id));
    }

    Optional<InformeVehiculo> findByNumeroOrden(String numeroOrden);

    List<InformeVehiculo> findByVehiculoClienteIdAndDatosServicioIsNull(Long clienteId);

    List<InformeVehiculo> findByVehiculoIdAndDatosServicioIsNull(Long vehiculoId);

    List<InformeVehiculo> findByVehiculoIdOrderByFechaIngresoDesc(Long vehiculoId);

    List<InformeVehiculo> findByVehiculoPlacaIgnoreCaseOrderByFechaIngresoDesc(String placa);

    Optional<InformeVehiculo> findTopByVehiculoIdOrderByFechaIngresoDesc(Long vehiculoId);

    List<InformeVehiculo> findAllByOrderByFechaIngresoDesc();

    List<InformeVehiculo> findByFechaIngresoBetween(LocalDateTime desde, LocalDateTime hasta);

    long countByEstado(EstadoInforme estado);

    boolean existsByVehiculoId(Long vehiculoId);

    boolean existsByMecanicoId(Long mecanicoId);

    boolean existsByIdAndMecanicoId(Long id, Long mecanicoId);

    boolean existsByPaqueteId(Long paqueteId);

    boolean existsByCitaId(Long citaId);

    @Query("select max(i.numeroOrden) from InformeVehiculo i where i.numeroOrden like concat(:prefijo, '%')")
    String ultimoNumero(@Param("prefijo") String prefijo);
}
