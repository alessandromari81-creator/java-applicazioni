/*
 * FILE: CorsoRepository.java
 * A COSA SERVE: accesso al database per i corsi. Scrivi solo questa interfaccia e Spring genera da solo
 * l'implementazione con findAll(), findById(), save(), deleteById()...
 * Le query stanno in una classe a parte (pattern "Repository").
 */
package it.palestra.repository;

import it.palestra.model.Corso;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// JpaRepository<Corso, Long>: "gestisco entità Corso la cui chiave primaria è di tipo Long".
public interface CorsoRepository extends JpaRepository<Corso, Long> {

    // Tutti i corsi ordinati per nome: la query si ricava dal NOME del metodo
    // (findAll + OrderBy + Nome + Asc -> SELECT ... ORDER BY nome ASC).
    // Se il nome non corrisponde a un campo di Corso l'app non parte: l'errore si vede subito,
    // mentre con un testo libero come Sort.by("nome") si scoprirebbe solo a runtime (era l'avviso di VS Code).
    // @EntityGraph carica anche l'istruttore nella stessa query (JOIN): la lista lo mostra per ogni corso e,
    // senza, partirebbe una query per ogni corso (problema "N+1").
    @EntityGraph(attributePaths = "istruttore")
    List<Corso> findAllByOrderByNomeAsc();

    // LISTA PAGINATA dei corsi. L'istruttore (relazione "a uno") si carica con una JOIN nella stessa query: con la
    // paginazione va bene perché ogni corso ha al massimo UN istruttore, quindi le righe restano una per corso.
    // @Override: ridefinisce findAll(Pageable) di JpaRepository per aggiungergli @EntityGraph.
    @Override
    @EntityGraph(attributePaths = "istruttore")
    Page<Corso> findAll(Pageable pageable);

    // Come sopra, con la ricerca: il nome del corso CONTIENE il testo (senza distinguere maiuscole).
    @EntityGraph(attributePaths = "istruttore")
    Page<Corso> findByNomeContainingIgnoreCase(String nome, Pageable pageable);

    // Un corso per id con l'istruttore già caricato (serve al form di modifica).
    @EntityGraph(attributePaths = "istruttore")
    Optional<Corso> findWithIstruttoreById(Long id);

    // I corsi a cui l'utente è iscritto, in ordine alfabetico, con l'istruttore già caricato (la pagina «Corsi» dell'utente lo mostra: una sola query).
    @EntityGraph(attributePaths = "istruttore")
    @Query("select c from Corso c where c.id in (select uc.id from Utente u join u.corsi uc where u.id = :utenteId) order by c.nome, c.id")
    List<Corso> iscrittoA(@Param("utenteId") Long utenteId);

    // UNA PAGINA dei corsi a cui l'utente PUÒ essere iscritto: tutti tranne quelli a cui è già iscritto e quelli che tiene lui come istruttore
    // (un istruttore non può frequentare il corso che tiene). "modello" è il testo cercato già pronto per LIKE (vedi IscrizioneService):
    // «%» per non filtrare, oppure «%testo%» con i caratteri speciali protetti dal carattere di escape «!». Il confronto è su upper() come nelle altre
    // ricerche. L'ordine (e lo spareggio sull'id) arriva da Pageable. La query di conteggio è scritta a parte perché con la sottoquery non si può derivare.
    @EntityGraph(attributePaths = "istruttore")
    @Query(value = "select c from Corso c where (c.istruttore is null or c.istruttore.id <> :utenteId) "
            + "and c.id not in (select uc.id from Utente u join u.corsi uc where u.id = :utenteId) "
            + "and upper(c.nome) like upper(:modello) escape '!'",
            countQuery = "select count(c) from Corso c where (c.istruttore is null or c.istruttore.id <> :utenteId) "
            + "and c.id not in (select uc.id from Utente u join u.corsi uc where u.id = :utenteId) "
            + "and upper(c.nome) like upper(:modello) escape '!'")
    Page<Corso> disponibiliPer(@Param("utenteId") Long utenteId, @Param("modello") String modello, Pageable pageable);
}
