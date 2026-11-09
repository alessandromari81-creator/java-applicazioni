/*
 * FILE: UtenteRepository.java
 * A COSA SERVE: accesso al database per gli utenti. Oltre ai metodi base (save, findAll, deleteById...)
 * aggiunge query personalizzate DEFINITE SOLO DAL NOME del metodo: Spring legge il nome e costruisce la query.
 */
package it.palestra.repository;

import it.palestra.model.Utente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UtenteRepository extends JpaRepository<Utente, Long> {

    // Tutti gli utenti ordinati per cognome e nome (ORDER BY cognome ASC, nome ASC).
    // @EntityGraph carica corsi e ruoli nella STESSA query con una JOIN, invece di una query per ogni utente
    // (problema "N+1"). In Eloquent: Utente::with(['corsi', 'ruoli'])->get().
    @EntityGraph(attributePaths = {"corsi", "ruoli"})
    List<Utente> findAllByOrderByCognomeAscNomeAsc();

    // Un utente per id, con corsi e ruoli già caricati. Optional = "forse c'è, forse no" (niente null).
    @EntityGraph(attributePaths = {"corsi", "ruoli"})
    Optional<Utente> findWithCorsiERuoliById(Long id);

    // Per il LOGIN: cerca per email (ignorando maiuscole) e carica i ruoli, che servono per le autorità.
    @EntityGraph(attributePaths = "ruoli")
    Optional<Utente> findWithRuoliByEmailIgnoreCase(String email);

    // Cerca per email ignorando maiuscole/minuscole (serve per controllare i duplicati).
    Optional<Utente> findByEmailIgnoreCase(String email);

    // Quanti utenti sono iscritti a un dato corso (COUNT con JOIN sulla pivot): serve per impedire di
    // eliminare un corso che ha ancora iscritti.
    long countByCorsiId(Long corsoId);

    // Quanti utenti hanno un dato ruolo, es. countByRuoliNome("ADMIN"): serve a non eliminare l'ultimo admin.
    long countByRuoliNome(String nomeRuolo);
}
