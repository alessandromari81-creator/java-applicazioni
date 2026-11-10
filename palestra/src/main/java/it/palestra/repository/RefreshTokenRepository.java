/*
 * FILE: RefreshTokenRepository.java
 * A COSA SERVE: accesso al database per i refresh token: cerca un token dal suo hash e revoca in blocco tutti quelli di
 * un utente (sospetto furto).
 */
package it.palestra.repository;

import it.palestra.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Cerca un token dal suo hash (SELECT ... WHERE token_hash = ?), con l'utente già caricato.
    @EntityGraph(attributePaths = "utente")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Revoca TUTTI i token ancora attivi di un utente con un solo UPDATE. Si usa quando qualcuno presenta un token già
    // usato: potrebbe essere stato rubato, e per prudenza si chiudono tutte le sessioni di quell'utente.
    // @Modifying dice a Spring Data che la query MODIFICA dati (UPDATE, non SELECT). clearAutomatically/flushAutomatically:
    // prima di eseguire la modifica scrive su database quanto in sospeso e dopo svuota la cache di Hibernate, così le
    // letture successive vedono i valori aggiornati.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken r set r.revocatoIl = :ora where r.utente.id = :utenteId and r.revocatoIl is null")
    int revocaTuttiDi(@Param("utenteId") Long utenteId, @Param("ora") Instant ora);

    // Cancella dal database i refresh token GIÀ SCADUTI (scade_il precedente al limite), sia quelli mai usati sia quelli usati o
    // revocati: dopo la scadenza non servono più a nessuno (un token scaduto è rifiutato comunque). Restituisce quanti ne ha cancellati.
    // Quelli non ancora scaduti restano anche se revocati: servono a riconoscere il RIUSO di un token già usato (sospetto furto).
    @Modifying
    @Query("delete from RefreshToken r where r.scadeIl < :limite")
    int eliminaScadutiPrima(@Param("limite") Instant limite);

    // Come revocaTuttiDi ma SENZA svuotare la memoria di Hibernate (nessun clearAutomatically): si usa dentro un servizio che ha
    // altre entità in corso di modifica (es. cambio password), che altrimenti andrebbero perse. Prima di eseguirla scrive su database
    // le modifiche in sospeso (flushAutomatically).
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken r set r.revocatoIl = :ora where r.utente.id = :utenteId and r.revocatoIl is null")
    int revocaTuttiPerUtente(@Param("utenteId") Long utenteId, @Param("ora") Instant ora);
}
