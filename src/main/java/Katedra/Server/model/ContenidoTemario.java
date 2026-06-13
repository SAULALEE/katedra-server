package Katedra.Server.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "teoria", columnDefinition = "json")
    private Object teoria;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ejercicios", columnDefinition = "json")
    private Object ejercicios;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluacion", columnDefinition = "json")
    private Object evaluacion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diapositivas", columnDefinition = "json")
    private Object diapositivas;

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

    public Object getTeoria() { return teoria; }
    public void setTeoria(Object teoria) { this.teoria = teoria; }

    public Object getEjercicios() { return ejercicios; }
    public void setEjercicios(Object ejercicios) { this.ejercicios = ejercicios; }

    public Object getEvaluacion() { return evaluacion; }
    public void setEvaluacion(Object evaluacion) { this.evaluacion = evaluacion; }

    public Object getDiapositivas() { return diapositivas; }
    public void setDiapositivas(Object diapositivas) { this.diapositivas = diapositivas; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
