package Katedra.Server.repository;

import Katedra.Server.model.Temario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemarioRepository extends JpaRepository<Temario, String> {
    List<Temario> findByUsuarioIdOrderByCreatedAtAsc(String usuarioId);
}
