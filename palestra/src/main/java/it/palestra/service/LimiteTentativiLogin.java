/*
 * FILE: LimiteTentativiLogin.java
 * A COSA SERVE: frena chi prova a indovinare le password. Conta i login FALLITI e, superata una soglia, rifiuta altri tentativi per un po'
 * (anche se poi la password fosse giusta). Vale per il login della API (POST /api/auth/login) e per il form del pannello (POST /login).
 *
 * DUE CONTATORI, per non bloccare per errore persone innocenti:
 *   1) per COPPIA indirizzo IP + email: massimo `login-max-tentativi` (predefinito 5) falliti nella finestra. Chi sbaglia la password
 *      cinque volte aspetta, ma lo stesso utente da un altro indirizzo non è toccato (un attaccante non può chiudere fuori l'admin
 *      dal suo computer, perché per lui il blocco vale solo da quell'indirizzo).
 *   2) per solo IP: massimo `login-max-tentativi-per-ip` (predefinito 20) falliti su QUALSIASI email: ferma chi prova tante email diverse.
 * FINESTRA: `login-finestra-minuti` (predefinita 15) a partire dal PRIMO fallimento; scaduta la finestra il contatore riparte da zero.
 * Un login riuscito azzera il contatore della coppia IP+email (non quello dell'IP).
 *
 * LIMITI NOTI:
 *   - i contatori stanno in MEMORIA: si azzerano al riavvio e non sono condivisi tra più istanze dell'app (per più istanze servirebbe
 *     un archivio condiviso, es. Redis);
 *   - l'indirizzo è quello che vede il server (getRemoteAddr). Dietro un proxy/load balancer sono tutti lo stesso indirizzo finché non si
 *     configura `server.forward-headers-strategy` (non attivata di proposito: l'intestazione X-Forwarded-For si può falsificare).
 * La data/ora viene dal Clock iniettato (nei test si usa un orologio finto).
 */
package it.palestra.service;

import it.palestra.exception.TroppiTentativiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LimiteTentativiLogin {

    // Oltre questo numero di contatori in memoria si buttano via quelli con la finestra già scaduta (evita che la mappa cresca senza fine).
    private static final int SOGLIA_PULIZIA = 10_000;

    // Un contatore: quanti fallimenti e da quando è iniziata la finestra.
    private record Finestra(int fallimenti, Instant inizio) {}

    private final Clock clock;
    private final int maxPerCoppia;
    private final int maxPerIp;
    private final Duration finestra;
    private final ConcurrentHashMap<String, Finestra> contatori = new ConcurrentHashMap<>();

    public LimiteTentativiLogin(Clock clock,
                                @Value("${app.sicurezza.login-max-tentativi:5}") int maxPerCoppia,
                                @Value("${app.sicurezza.login-max-tentativi-per-ip:20}") int maxPerIp,
                                @Value("${app.sicurezza.login-finestra-minuti:15}") long finestraMinuti) {
        this.clock = clock;
        this.maxPerCoppia = maxPerCoppia;
        this.maxPerIp = maxPerIp;
        this.finestra = Duration.ofMinutes(finestraMinuti);
    }

    // Da chiamare PRIMA di controllare la password: se la coppia IP+email o l'IP hanno superato la soglia lancia
    // TroppiTentativiException (con i secondi da attendere), senza nemmeno guardare la password.
    public void verifica(String ip, String email) {
        long attesa = Math.max(
                secondiResidui(chiaveCoppia(ip, email), maxPerCoppia),
                secondiResidui(chiaveIp(ip), maxPerIp));
        if (attesa > 0) {
            throw new TroppiTentativiException(attesa);
        }
    }

    // Da chiamare dopo un login fallito: aumenta entrambi i contatori (o li avvia, se la finestra era scaduta).
    public void registraFallimento(String ip, String email) {
        if (contatori.size() > SOGLIA_PULIZIA) {
            eliminaScaduti();
        }
        incrementa(chiaveCoppia(ip, email));
        incrementa(chiaveIp(ip));
    }

    // Da chiamare dopo un login riuscito: azzera il contatore della coppia IP+email.
    public void registraSuccesso(String ip, String email) {
        contatori.remove(chiaveCoppia(ip, email));
    }

    // Azzera tutto (serve ai test, perché i contatori vivono in memoria e sopravvivono da un test all'altro).
    public void azzera() {
        contatori.clear();
    }

    // Secondi che mancano alla fine del blocco per questa chiave; 0 se non è bloccata (sotto soglia o finestra scaduta).
    private long secondiResidui(String chiave, int massimo) {
        Finestra f = contatori.get(chiave);
        if (f == null || f.fallimenti() < massimo) {
            return 0;
        }
        long residuo = Duration.between(clock.instant(), f.inizio().plus(finestra)).toSeconds() + 1;
        return Math.max(residuo, 0);
    }

    // Aumenta il contatore in modo atomico (più richieste insieme non si perdono) e ricomincia da 1 se la finestra era scaduta.
    private void incrementa(String chiave) {
        Instant ora = clock.instant();
        contatori.merge(chiave, new Finestra(1, ora), (attuale, nuova) ->
                attuale.inizio().plus(finestra).isAfter(ora)
                        ? new Finestra(attuale.fallimenti() + 1, attuale.inizio())
                        : nuova);
    }

    private void eliminaScaduti() {
        Instant ora = clock.instant();
        contatori.entrySet().removeIf(voce -> !voce.getValue().inizio().plus(finestra).isAfter(ora));
    }

    // Chiavi dei contatori. L'email si normalizza (spazi e maiuscole) e si accorcia, così non si aggira il limite scrivendola in
    // modi diversi e non si riempie la memoria con testi enormi.
    private String chiaveCoppia(String ip, String email) {
        String pulita = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (pulita.length() > 100) {
            pulita = pulita.substring(0, 100);
        }
        return "coppia|" + ip + "|" + pulita;
    }

    private String chiaveIp(String ip) {
        return "ip|" + ip;
    }
}
