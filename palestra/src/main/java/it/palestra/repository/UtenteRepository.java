/*
 * FILE: UtenteRepository.java
 * A COSA SERVE: accesso al database per gli utenti. Oltre ai metodi base (save, findAll, deleteById...)
 * aggiunge query personalizzate DEFINITE SOLO DAL NOME del metodo: Spring legge il nome e costruisce la query.
 */
package it.palestra.repository;

import it.palestra.model.Utente;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    // Versione dei token di accesso dell'utente con questa email ESATTA (quella scritta nel token: l'indice unico sull'email la trova
    // subito). Optional vuoto = l'utente non esiste più. Serve al controllo di revoca che si fa a ogni richiesta alla API.
    @Query("select u.versioneToken from Utente u where u.email = :email")
    Optional<Integer> trovaVersioneToken(@Param("email") String email);

    // Revoca i token di accesso di un utente con un UPDATE diretto (+1 atomico), quando non c'è un'entità gestita da modificare
    // (es. il rilevamento del riuso di un refresh token).
    @Modifying
    @Query("update Utente u set u.versioneToken = u.versioneToken + 1 where u.id = :id")
    int incrementaVersioneToken(@Param("id") Long id);

    // L'utente con questa email (senza distinguere maiuscole) con tutte le relazioni che servono all'area personale: ruoli, corsi frequentati e
    // corsi tenuti. Una sola query, perché fuori dalla transazione le collezioni non si possono più leggere.
    @EntityGraph(attributePaths = {"corsi", "ruoli", "corsiInsegnati"})
    Optional<Utente> findWithRelazioniByEmailIgnoreCase(String email);

    // Nome e cognome (e basta: niente email né altri dati) degli iscritti a un corso, in ordine alfabetico. Li usa l'area dell'istruttore.
    @Query("select u.nome as nome, u.cognome as cognome from Utente u join u.corsi c where c.id = :corsoId order by u.cognome, u.nome, u.id")
    List<NomeCognome> findIscrittiDelCorso(@Param("corsoId") Long corsoId);

    // L'utente con BLOCCO sulla riga (SELECT ... FOR UPDATE): finché la transazione non finisce, un'altra transazione che chiede lo stesso blocco aspetta.
    // IscrizioneService lo prende all'inizio di iscrivi e disiscrivi, così due richieste insieme sullo stesso utente si mettono in fila: la seconda legge
    // le iscrizioni già aggiornate dalla prima e risponde «è già iscritto» invece di scontrarsi con la chiave primaria di utente_corso. Non carica le
    // relazioni (con i join un FOR UPDATE non si può applicare al lato opzionale): quelle si leggono dopo, con findWithRelazioniById. Va chiamato
    // dentro una transazione (@Transactional del service).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Utente u where u.id = :id")
    Optional<Utente> bloccaPerId(@Param("id") Long id);
}
