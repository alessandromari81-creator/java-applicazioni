/*
 * FILE: GeneratoreToken.java
 * A COSA SERVE: crea i token dei link inviati per email: 32 byte casuali da un generatore sicuro (256 bit: impossibili da indovinare) scritti in
 * Base64 adatto agli indirizzi web. Nel database si salva solo l'hash SHA-256 del token (lo calcola RefreshTokenService.hash, lo stesso dei refresh token).
 */
package it.palestra.service;

import java.security.SecureRandom;
import java.util.Base64;

public final class GeneratoreToken {

    private static final SecureRandom CASUALE = new SecureRandom();

    private GeneratoreToken() {}

    // Un nuovo token casuale (la stringa che finisce nel link dell'email).
    public static String nuovo() {
        byte[] byteCasuali = new byte[32];
        CASUALE.nextBytes(byteCasuali);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteCasuali);
    }

    // L'hash che si salva nel database al posto del token.
    public static String hash(String token) {
        return RefreshTokenService.hash(token);
    }
}
