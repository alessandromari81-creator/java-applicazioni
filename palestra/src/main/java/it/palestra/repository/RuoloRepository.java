/*
 * FILE: RuoloRepository.java
 * A COSA SERVE: accesso al database per i ruoli (ADMIN, ISTRUTTORE, ISCRITTO).
 */
package it.palestra.repository;

import it.palestra.model.Ruolo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuoloRepository extends JpaRepository<Ruolo, Long> {

    // Tutti i ruoli in ordine di inserimento (ADMIN, ISTRUTTORE, ISCRITTO): per le checkbox del form.
    List<Ruolo> findAllByOrderByIdAsc();

    // Un ruolo dal nome, es. "ADMIN" (SELECT ... WHERE nome = ?).
    Optional<Ruolo> findByNome(String nome);

    // Un ruolo dal nome CON BLOCCO sulla riga (SELECT ... FOR UPDATE): finché la transazione non finisce, un'altra transazione che
    // chiede lo stesso blocco aspetta. Serve come "semaforo": le operazioni che riducono il numero di admin (UtenteService.modifica ed
    // elimina) lo chiedono PRIMA di contare gli admin, così due richieste insieme si mettono in fila invece di leggere entrambe
    // "ce ne sono due". Deve essere chiamato dentro una transazione (@Transactional del service).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ruolo r where r.nome = :nome")
    Optional<Ruolo> bloccaPerNome(@Param("nome") String nome);
}
