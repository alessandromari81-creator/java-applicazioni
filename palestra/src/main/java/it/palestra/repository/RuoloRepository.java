/*
 * FILE: RuoloRepository.java
 * A COSA SERVE: accesso al database per i ruoli (ADMIN, ISTRUTTORE, ISCRITTO).
 */
package it.palestra.repository;

import it.palestra.model.Ruolo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuoloRepository extends JpaRepository<Ruolo, Long> {

    // Tutti i ruoli in ordine di inserimento (ADMIN, ISTRUTTORE, ISCRITTO): per le checkbox del form.
    List<Ruolo> findAllByOrderByIdAsc();

    // Un ruolo dal nome, es. "ADMIN" (SELECT ... WHERE nome = ?).
    Optional<Ruolo> findByNome(String nome);
}
