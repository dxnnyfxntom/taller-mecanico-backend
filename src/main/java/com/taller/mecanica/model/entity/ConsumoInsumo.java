package com.taller.mecanica.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "consumos_insumo", uniqueConstraints = @UniqueConstraint(
        name = "uk_consumos_insumo_operacion", columnNames = {"informe_id", "clave_operacion"}))
@Getter
@Setter
@NoArgsConstructor
public class ConsumoInsumo {

    @Column(name = "clave_operacion", length = 36)
    private String claveOperacion;

    @Column(name = "revertido_en")
    private LocalDateTime revertidoEn;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "informe_id", nullable = false)
    @JsonIgnore
    private InformeVehiculo informe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "url_ficha_tecnica_pdf", length = 500)
    private String urlFichaTecnicaPdf;
}
