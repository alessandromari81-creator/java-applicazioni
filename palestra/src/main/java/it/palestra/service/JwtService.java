/*
 * FILE: JwtService.java
 * A COSA SERVE: crea i token JWT per chi ha fatto login alla API. Scrive nel token chi è l'utente (email), i suoi
 * ruoli e la scadenza, poi lo firma con la chiave segreta (vedi JwtConfig).
 */
package it.palestra.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    // Nome del "claim" (campo del contenuto del token) che porta i ruoli. SecurityConfig lo legge con lo stesso nome.
    public static final String CLAIM_RUOLI = "ruoli";

    // Un token appena emesso: la stringa e tra quanti secondi scade (la API li restituisce entrambi al client).
    public record TokenEmesso(String token, long scadeTraSecondi) {}

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration durata;

    // La durata arriva da application.properties (app.jwt.durata-minuti): più è breve, meno danno fa un token rubato.
    // L'orologio è quello dell'applicazione (OrologioConfig), non LocalDate.now()/Instant.now() diretti.
    public JwtService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.durata-minuti}") long durataMinuti) {
        this.encoder = encoder;
        this.clock = clock;
        this.durata = Duration.ofMinutes(durataMinuti);
    }

    // Emette un token per l'utente con l'email e i ruoli indicati (nomi senza prefisso: "ADMIN", non "ROLE_ADMIN").
    public TokenEmesso emetti(String email, Collection<String> ruoli) {
        Instant ora = clock.instant();
        JwtClaimsSet contenuto = JwtClaimsSet.builder()
                .issuer("palestra")                      // chi ha emesso il token
                .subject(email)                          // a chi appartiene
                .issuedAt(ora)                           // quando è stato emesso
                .expiresAt(ora.plus(durata))             // quando smette di valere
                .claim(CLAIM_RUOLI, List.copyOf(ruoli))  // i ruoli, per le autorizzazioni
                .build();
        // L'intestazione dichiara l'algoritmo di firma: HS256, lo stesso del decodificatore in JwtConfig.
        JwsHeader intestazione = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(intestazione, contenuto)).getTokenValue();
        return new TokenEmesso(token, durata.toSeconds());
    }
}
