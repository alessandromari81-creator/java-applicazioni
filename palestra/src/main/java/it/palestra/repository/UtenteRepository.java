/*
 * FILE: UtenteRepository.java
 * A COSA SERVE: accesso al database per gli utenti. Oltre ai metodi base (save, findAll, deleteById...)
 * aggiunge query personalizzate DEFINITE SOLO DAL NOME del metodo: Spring legge il nome e costruisce la query.
 */
package it.palestra.repository;

import it.palestra.model.Utente;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UtenteRepository extends JpaRepository<Utente, Long> {

    // LISTA PAGINATA, PASSO 1: una pagina di utenti SENZA caricare le collezioni (corsi, ruoli, corsiInsegnati).
    // Così il database può usare davvero LIMIT/OFFSET e restituisce esattamente gli utenti della pagina. Se qui si
    // caricassero anche le collezioni con una JOIN, ogni utente avrebbe più righe e Hibernate dovrebbe leggere TUTTO e
    // paginare in memoria. findAll(Pageable) esiste già (JpaRepository); sotto c'è la versione con ricerca.
    // La ricerca: "contiene" il testo in nome, cognome o email, senza distinguere maiuscole (Spring Data protegge da solo
    // i caratteri speciali % e _ del testo cercato).
    Page<Utente> findByNomeContainingIgnoreCaseOrCognomeContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String nome, String cognome, String email, Pageable pageable);

    // LISTA PAGINATA, PASSO 2: carica gli utenti di cui si conoscono gli id (quelli della pagina) CON corsi, ruoli e corsi
    // tenuti, in una sola query (WHERE id IN (...)). Sono al massimo 100 utenti, quindi il carico è piccolo.
    // In Eloquent: Utente::with(['corsi','ruoli','corsiInsegnati'])->whereIn('id', $ids)->get().
    @EntityGraph(attributePaths = {"corsi", "ruoli", "corsiInsegnati"})
    @Query("select distinct u from Utente u where u.id in :ids")
    List<Utente> findConRelazioniByIdIn(@Param("ids") Collection<Long> ids);

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
