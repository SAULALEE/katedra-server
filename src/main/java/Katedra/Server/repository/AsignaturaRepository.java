package Katedra.Server.repository;

import Katedra.Server.model.Asignatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AsignaturaRepository extends JpaRepository<Asignatura, String> {

    List<Asignatura> findByUsuarioIdOrderByNombreAsc(String usuarioId);

    Optional<Asignatura> findByIdAndUsuarioEmail(String id, String email);
}
