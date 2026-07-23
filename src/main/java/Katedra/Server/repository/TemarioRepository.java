package Katedra.Server.repository;

import Katedra.Server.model.Temario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemarioRepository extends JpaRepository<Temario, String> {

    List<Temario> findByUsuarioIdOrderByCreatedAtAsc(String usuarioId);

    List<Temario> findByUsuarioIdAndAsignaturaOrderByCreatedAtAsc(String usuarioId, String asignatura);

    @Query("SELECT DISTINCT t.asignatura FROM Temario t WHERE t.usuario.id = :usuarioId AND t.asignatura IS NOT NULL ORDER BY t.asignatura ASC")
    List<String> findDistinctAsignaturasByUsuarioId(@Param("usuarioId") String usuarioId);
}
