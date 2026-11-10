/*
 * FILE: RegistrazioneInAttesaRepository.java
 * A COSA SERVE: accesso al database per le registrazioni in attesa di conferma: ricerca per email (una sola per indirizzo) e per hash del token del
 * link, e pulizia di quelle scadute.
 */
package it.palestra.repository;

import it.palestra.model.RegistrazioneInAttesa;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrazioneInAttesaRepository extends JpaRepository<RegistrazioneInAttesa, Long> {

    // La registrazione in attesa per questo indirizzo (già in minuscolo), se c'è.
    Optional<RegistrazioneInAttesa> findByEmail(String email);

    // La registrazione dal hash del token del link.
    Optional<RegistrazioneInAttesa> findByTokenHash(String tokenHash);

    // Pulizia notturna: cancella le registrazioni con il link scaduto. Restituisce quante.
    @Modifying
    @Query("delete from RegistrazioneInAttesa r where r.scadeIl < :limite")
    int eliminaScadutePrima(@Param("limite") Instant limite);
}
