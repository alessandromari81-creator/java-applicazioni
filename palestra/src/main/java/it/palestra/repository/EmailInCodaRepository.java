/*
 * FILE: EmailInCodaRepository.java
 * A COSA SERVE: accesso al database per la coda delle email (tabella email_in_coda). Le query sono scritte per funzionare con più istanze dell'app
 * che girano insieme: nessuno blocca righe per la durata di un invio; chi vuole spedire una riga la «reclama» con un UPDATE condizionato che riesce a
 * una sola istanza (vedi reclama).
 */
package it.palestra.repository;

import it.palestra.model.EmailInCoda;
import it.palestra.model.StatoEmail;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailInCodaRepository extends JpaRepository<EmailInCoda, Long> {

    // Le prossime email da spedire: DA_INVIARE e già «scadute» (prossimo_tentativo passato), le più vecchie per prime. "limite" dice quante (una pagina da N, senza ordinamenti
    // propri: l'ordine è quello della query). È solo una LETTURA: non prenota niente; chi vuole spedire una riga deve poi reclamarla.
    @Query("select e from EmailInCoda e where e.stato = it.palestra.model.StatoEmail.DA_INVIARE and e.prossimoTentativo <= :ora order by e.id")
    List<EmailInCoda> daSpedire(@Param("ora") Instant ora, Pageable limite);

    // «Reclama» una riga: da DA_INVIARE a IN_CORSO con un UPDATE condizionato (where stato = DA_INVIARE). Restituisce 1 se la riga era ancora libera e ora è tua, 0 se
    // nel frattempo l'ha presa un'altra istanza: così due giri contemporanei non spediscono mai la stessa email. Lo fa il database, in modo atomico: non servono blocchi di righe.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailInCoda e set e.stato = it.palestra.model.StatoEmail.IN_CORSO, e.inCorsoDal = :ora "
            + "where e.id = :id and e.stato = it.palestra.model.StatoEmail.DA_INVIARE")
    int reclama(@Param("id") Long id, @Param("ora") Instant ora);

    // Le righe IN_CORSO da troppo tempo (il processo che le stava spedendo si è fermato) tornano DA_INVIARE. Restituisce quante.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailInCoda e set e.stato = it.palestra.model.StatoEmail.DA_INVIARE, e.inCorsoDal = null "
            + "where e.stato = it.palestra.model.StatoEmail.IN_CORSO and e.inCorsoDal < :limite")
    int rimettiDaInviare(@Param("limite") Instant limite);

    // Pulizia notturna: cancella le righe FALLITA create prima di "limite" (contengono il link con il token vero: non devono restare). Restituisce quante.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EmailInCoda e where e.stato = it.palestra.model.StatoEmail.FALLITA and e.creataIl < :limite")
    int eliminaFallitePrima(@Param("limite") Instant limite);

    // Quante righe ci sono in un dato stato (serve ai test e al controllo dello stato della coda).
    long countByStato(StatoEmail stato);
}
