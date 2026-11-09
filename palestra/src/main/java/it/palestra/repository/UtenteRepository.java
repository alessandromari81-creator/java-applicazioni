/*
 * FILE: UtenteRepository.java
 * A COSA SERVE: accesso al database per gli utenti. Oltre ai metodi base (save, findAll,
 * deleteById...) aggiunge query personalizzate DEFINITE SOLO DAL NOME del metodo:
 * Spring legge il nome e costruisce la query SQL.
 */
package it.palestra.repository;

import it.palestra.model.Utente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UtenteRepository extends JpaRepository<Utente, Long> {

    // Restituisce tutti gli utenti ordinati per cognome e poi nome
    // (nome del metodo -> ORDER BY cognome ASC, nome ASC).
    // @EntityGraph carica i corsi nella STESSA query con una JOIN, invece di fare una query
    // per ogni utente (problema "N+1"). In Eloquent: Utente::with('corsi')->get().
    @EntityGraph(attributePaths = "corsi")
    List<Utente> findAllByOrderByCognomeAscNomeAsc();

    // Cerca un utente per id e carica subito anche i suoi corsi.
    // Optional = "forse c'è, forse no" (evita i null; simile a findOrFail ma senza eccezione).
    @EntityGraph(attributePaths = "corsi")
    Optional<Utente> findWithCorsiById(Long id);

    // Conta quanti utenti sono iscritti a un dato corso (nome del metodo -> COUNT con JOIN sulla
    // tabella pivot: "Corsi" = campo corsi di Utente, "Id" = suo id). Serve per impedire di
    // eliminare un corso che ha ancora iscritti.
    long countByCorsiId(Long corsoId);

    // Cerca per email ignorando maiuscole/minuscole (serve per controllare i duplicati).
    Optional<Utente> findByEmailIgnoreCase(String email);
}
