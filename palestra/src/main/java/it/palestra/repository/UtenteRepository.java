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
    // @EntityGraph carica corsi (frequentati), ruoli e corsiInsegnati nella STESSA query con delle JOIN, invece di
    // una query per ogni utente (problema "N+1"). In Eloquent: Utente::with(['corsi', 'ruoli', 'corsiInsegnati']).
    @EntityGraph(attributePaths = {"corsi", "ruoli", "corsiInsegnati"})
    List<Utente> findAllByOrderByCognomeAscNomeAsc();

    // Un utente per id, con corsi frequentati, ruoli e corsi tenuti già caricati.
    // Optional = "forse c'è, forse no" (niente null).
    @EntityGraph(attributePaths = {"corsi", "ruoli", "corsiInsegnati"})
    Optional<Utente> findWithRelazioniById(Long id);

    // Tutti gli utenti che hanno un dato ruolo, ordinati per cognome e nome: serve a riempire la tendina
    // "Istruttore" nel form dei corsi (solo chi ha il ruolo ISTRUTTORE). "RuoliNome" = campo ruoli, poi nome.
    List<Utente> findByRuoliNomeOrderByCognomeAscNomeAsc(String nomeRuolo);

    // true se l'utente è iscritto (come allievo) al corso: serve a impedire di nominare istruttore di un corso
    // uno che lo frequenta già come iscritto. "CorsiId" = campo corsi, poi id.
    boolean existsByIdAndCorsiId(Long utenteId, Long corsoId);

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
