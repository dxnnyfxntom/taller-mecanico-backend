package com.taller.mecanica.repository;

import com.taller.mecanica.model.entity.EvidenciaFotografica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvidenciaFotograficaRepository extends JpaRepository<EvidenciaFotografica, Long> {

    @org.springframework.data.jpa.repository.Query("select e.informe.id from EvidenciaFotografica e where e.id = :id")
    java.util.Optional<Long> buscarInformeId(@org.springframework.data.repository.query.Param("id") Long id);

    List<EvidenciaFotografica> findByInformeIdOrderByFechaSubidaDesc(Long informeId);

    Optional<EvidenciaFotografica> findByIdAndInformeId(Long id, Long informeId);
}
