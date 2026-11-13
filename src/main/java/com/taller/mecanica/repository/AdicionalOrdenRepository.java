package com.taller.mecanica.repository;

import com.taller.mecanica.model.entity.AdicionalOrden;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdicionalOrdenRepository extends JpaRepository<AdicionalOrden, Long> {

    @org.springframework.data.jpa.repository.Query("select a.informe.id from AdicionalOrden a where a.id = :id")
    java.util.Optional<Long> buscarInformeId(@org.springframework.data.repository.query.Param("id") Long id);

    List<AdicionalOrden> findByInformeIdOrderByIdAsc(Long informeId);

    Optional<AdicionalOrden> findByIdAndInformeId(Long id, Long informeId);
}
