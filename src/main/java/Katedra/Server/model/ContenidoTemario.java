package Katedra.Server.model;

import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "contenido_temario")
public class ContenidoTemario {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "temario_id", nullable = false, unique = true)
    private Temario temario;

    @Column(name = "teoria", columnDefinition = "LONGTEXT")
    private String teoria;

    @Column(name = "ejercicios", columnDefinition = "LONGTEXT")
    private String ejercicios;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluacion", columnDefinition = "json")
    private List<EvaluacionPreguntaDTO> evaluacion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diapositivas", columnDefinition = "json")
    private List<DiapositivaDTO> diapositivas;

    @Column(name = "modelo", length = 50)
    private String modelo;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ContenidoTemario() {}

    public ContenidoTemario(Temario temario) {
        this.temario = temario;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Temario getTemario() { return temario; }
    public void setTemario(Temario temario) { this.temario = temario; }

    public String getTeoria() { return teoria; }
    public void setTeoria(String teoria) { this.teoria = teoria; }

    public String getEjercicios() { return ejercicios; }
    public void setEjercicios(String ejercicios) { this.ejercicios = ejercicios; }

    public List<EvaluacionPreguntaDTO> getEvaluacion() { return evaluacion; }
    public void setEvaluacion(List<EvaluacionPreguntaDTO> evaluacion) { this.evaluacion = evaluacion; }

    public List<DiapositivaDTO> getDiapositivas() { return diapositivas; }
    public void setDiapositivas(List<DiapositivaDTO> diapositivas) { this.diapositivas = diapositivas; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
