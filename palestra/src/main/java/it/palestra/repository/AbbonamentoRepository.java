/*
 * FILE: AbbonamentoRepository.java
 * A COSA SERVE: accesso al database per gli abbonamenti. Oltre ai metodi base ci sono due query scritte a mano
 * in JPQL con @Query (il linguaggio di query di JPA: come SQL, ma parla di CLASSI e CAMPI Java, non di tabelle
 * e colonne). Servono quando il nome del metodo da solo diventerebbe troppo lungo o poco leggibile.
 */
package it.palestra.repository;

import it.palestra.model.Abbonamento;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AbbonamentoRepository extends JpaRepository<Abbonamento, Long> {

    // Tutti gli abbonamenti, i più recenti per fine in alto (a parità, l'ultimo inserito). @EntityGraph carica
    // l'utente nella stessa query (JOIN), così la lista non fa una query per riga (problema "N+1").
    @EntityGraph(attributePaths = "utente")
    List<Abbonamento> findAllByOrderByDataFineDescIdDesc();

    // Lo storico di UN utente, dal più recente (per data di inizio) al più vecchio: serve alla sua pagina e alla API.
    // @EntityGraph carica anche l'utente: la API lo mostra in ogni abbonamento e, fuori da una transazione, non si
    // potrebbe più leggere.
    @EntityGraph(attributePaths = "utente")
    List<Abbonamento> findByUtenteIdOrderByDataInizioDesc(Long utenteId);

    // Un abbonamento per id con l'utente già caricato (per mostrarlo nella API senza leggere il database dopo).
    @EntityGraph(attributePaths = "utente")
    Optional<Abbonamento> findConUtenteById(Long id);

    // Gli abbonamenti dello stesso utente che SI SOVRAPPONGONO al periodo [inizio, fine] (estremi inclusi).
    // DUE PERIODI SI SOVRAPPONGONO quando ciascuno inizia prima (o nel giorno) in cui finisce l'altro:
    //     esistente.inizio <= nuovo.fine   E   esistente.fine >= nuovo.inizio
    // Esempi con il nuovo periodo 10-20: un esistente 1-10 SI sovrappone (condivide il giorno 10); 1-9 no;
    // 21-30 no; 15-18 sì (è contenuto). Un rinnovo che parte il giorno DOPO la fine non si sovrappone.
    // ":nome" sono parametri con nome: il valore viene passato dopo, in modo sicuro (niente SQL injection).
    @Query("select a from Abbonamento a "
            + "where a.utente.id = :utenteId and a.dataInizio <= :fine and a.dataFine >= :inizio "
            + "order by a.dataInizio")
    List<Abbonamento> trovaSovrapposti(@Param("utenteId") Long utenteId,
                                       @Param("inizio") LocalDate inizio,
                                       @Param("fine") LocalDate fine);

    // L'ultimo giorno coperto da un abbonamento di questo utente (la fine più avanti nel tempo), oppure null se
    // non ne ha. Serve a proporre la data di inizio di un rinnovo: il giorno dopo.
    @Query("select max(a.dataFine) from Abbonamento a where a.utente.id = :utenteId")
    LocalDate ultimaDataFine(@Param("utenteId") Long utenteId);

    // Quanti abbonamenti ha registrato l'utente (nome del metodo -> SELECT COUNT(*) ... WHERE utente_id = ?).
    // Serve a non eliminare un utente, né togliergli il ruolo ISCRITTO, finché ha uno storico di abbonamenti.
    long countByUtenteId(Long utenteId);
}
