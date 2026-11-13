package com.taller.mecanica.repository;

import com.taller.mecanica.model.entity.ConsumoInsumo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsumoInsumoRepository extends JpaRepository<ConsumoInsumo, Long> {

    List<ConsumoInsumo> findByInformeIdAndRevertidoEnIsNullOrderByIdAsc(Long informeId);

    Optional<ConsumoInsumo> findByInformeIdAndClaveOperacion(Long informeId, String claveOperacion);

    @org.springframework.data.jpa.repository.Query("select x.informe.id from ConsumoInsumo x where x.id = :id")
    Optional<Long> buscarInformeId(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<ConsumoInsumo> findByIdAndInformeId(Long id, Long informeId);

    boolean existsByInsumoId(Long insumoId);
}
