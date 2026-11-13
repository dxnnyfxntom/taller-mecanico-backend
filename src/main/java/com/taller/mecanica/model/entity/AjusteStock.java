package com.taller.mecanica.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "ajustes_stock")
public class AjusteStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long insumoId;
    @Column(nullable = false)
    private int stockAnterior;
    @Column(nullable = false)
    private int stockNuevo;
    @Column(nullable = false, length = 250)
    private String motivo;
    @Column(nullable = false)
    private LocalDateTime fecha;

    protected AjusteStock() {
    }

    public AjusteStock(Long insumoId, int stockAnterior, int stockNuevo, String motivo) {
        this.insumoId = insumoId;
        this.stockAnterior = stockAnterior;
        this.stockNuevo = stockNuevo;
        this.motivo = motivo;
        this.fecha = LocalDateTime.now();
    }
}
