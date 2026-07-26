package Katedra.Server.repository;

import Katedra.Server.model.HistorialEvento;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialEventoRepository extends JpaRepository<HistorialEvento, String> {

    List<HistorialEvento> findByUsuarioIdOrderByCreatedAtDesc(String usuarioId, Pageable pageable);
}
