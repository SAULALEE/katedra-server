package Katedra.Server.student.model;

import Katedra.Server.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_session_revocation")
public class StudentSessionRevocation {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "session_key_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String sessionKeyHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false, updatable = false)
    private LocalDateTime revokedAt;

    protected StudentSessionRevocation() {
    }

    public StudentSessionRevocation(Usuario usuario, String sessionKeyHash, LocalDateTime expiresAt,
                                    LocalDateTime revokedAt) {
        this.usuario = usuario;
        this.sessionKeyHash = sessionKeyHash;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public String getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public String getSessionKeyHash() { return sessionKeyHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
}
