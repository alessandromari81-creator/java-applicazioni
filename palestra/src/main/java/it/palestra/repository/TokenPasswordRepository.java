/*
 * FILE: TokenPasswordRepository.java
 * A COSA SERVE: accesso al database per i token di impostazione/reimpostazione della password: ricerca dal hash, cancellazione di quelli di un utente,
 * ricerca dell'ultimo emesso (per la pausa tra due email) e pulizia di quelli scaduti.
 */
package it.palestra.repository;

import it.palestra.model.TipoTokenPassword;
import it.palestra.model.TokenPassword;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenPasswordRepository extends JpaRepository<TokenPassword, Long> {

    // Il token dal suo hash, con l'utente già caricato (serve subito dopo, fuori dalla ricerca).
    @EntityGraph(attributePaths = "utente")
    Optional<TokenPassword> findByTokenHash(String tokenHash);

    // L'ultimo token di quel tipo emesso per l'utente: serve a non rispedire l'email se ne è appena partita una.
    Optional<TokenPassword> findFirstByUtenteIdAndTipoOrderByCreatoIlDesc(Long utenteId, TipoTokenPassword tipo);

    // Cancella tutti i token di un utente (di qualunque tipo): quando uno viene usato, gli altri link smettono di valere.
    @Modifying(flushAutomatically = true)
    @Query("delete from TokenPassword t where t.utente.id = :utenteId")
    int eliminaDiUtente(@Param("utenteId") Long utenteId);

    // Cancella i token di un tipo per un utente: una nuova richiesta sostituisce la precedente.
    @Modifying(flushAutomatically = true)
    @Query("delete from TokenPassword t where t.utente.id = :utenteId and t.tipo = :tipo")
    int eliminaDiUtenteETipo(@Param("utenteId") Long utenteId, @Param("tipo") TipoTokenPassword tipo);

    // Pulizia notturna: cancella i token già scaduti. Restituisce quanti.
    @Modifying
    @Query("delete from TokenPassword t where t.scadeIl < :limite")
    int eliminaScadutiPrima(@Param("limite") Instant limite);
}
