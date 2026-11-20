package Katedra.Server.student.repository;

import Katedra.Server.student.model.StudentSessionRevocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface StudentSessionRevocationRepository extends JpaRepository<StudentSessionRevocation, String> {

    boolean existsBySessionKeyHashAndExpiresAtAfter(String sessionKeyHash, LocalDateTime now);

    @Modifying
    @Transactional
    @Query("delete from StudentSessionRevocation revocation where revocation.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO student_session_revocation
                (id, usuario_id, session_key_hash, expires_at, revoked_at)
            VALUES (:id, :userId, :sessionKeyHash, :expiresAt, :revokedAt)
            ON CONFLICT (session_key_hash) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") String id,
            @Param("userId") String userId,
            @Param("sessionKeyHash") String sessionKeyHash,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("revokedAt") LocalDateTime revokedAt
    );
}
