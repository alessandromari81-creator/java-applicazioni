/*
 * FILE: RegolePassword.java
 * A COSA SERVE: la regola sulla lunghezza di una nuova password, scritta in un solo posto. La usano il form utente dell'admin, il
 * cambio password, il recupero password e la registrazione. È una funzione pura (nessun database, nessun Spring): per questo è una
 * classe con un metodo statico e si prova senza avviare niente.
 */
package it.palestra.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class RegolePassword {

    // Limite di bcrypt: oltre 72 BYTE ignorerebbe il resto della password.
    public static final int MASSIMO_BYTE = 72;

    // Lunghezza minima in caratteri.
    public static final int MINIMO_CARATTERI = 8;

    // Classe di sola utilità: non si crea nessuna istanza.
    private RegolePassword() {
    }

    // Restituisce il messaggio d'errore se la password non rispetta la regola, altrimenti Optional vuoto. La regola: almeno 8 caratteri
    // e al massimo 72 BYTE (i caratteri accentati occupano più di un byte, quindi contano i byte e non i caratteri).
    public static Optional<String> errore(String password) {
        if (password.length() < MINIMO_CARATTERI || password.getBytes(StandardCharsets.UTF_8).length > MASSIMO_BYTE) {
            return Optional.of("La password deve avere da 8 a 72 caratteri");
        }
        return Optional.empty();
    }

    // true se la password può essere quella giusta rispetto al limite di bcrypt (non null e al massimo 72 byte): oltre il limite non
    // si confronta nemmeno, perché bcrypt lancerebbe un errore invece di rispondere "non corrisponde".
    public static boolean entraNelLimiteDiBcrypt(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= MASSIMO_BYTE;
    }
}
