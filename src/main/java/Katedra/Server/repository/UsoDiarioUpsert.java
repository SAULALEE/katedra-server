package Katedra.Server.repository;

import java.time.LocalDate;

/** Creates the daily usage row without changing it when it already exists. */
public interface UsoDiarioUpsert {

    int crearFilaSiNoExiste(String id, String usuarioId, LocalDate fecha);
}
