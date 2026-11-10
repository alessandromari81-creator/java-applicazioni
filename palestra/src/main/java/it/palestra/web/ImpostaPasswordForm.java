/*
 * FILE: ImpostaPasswordForm.java
 * A COSA SERVE: i dati del modulo in cui si sceglie la password dopo aver aperto il link ricevuto per email (conferma registrazione, reimpostazione,
 * invito): il token del link (campo nascosto), la nuova password e la sua ripetizione. Le regole sulla lunghezza e sul confronto le controlla il controller.
 */
package it.palestra.web;

import jakarta.validation.constraints.NotBlank;

public class ImpostaPasswordForm {

    private String token;

    @NotBlank(message = "Scrivi la nuova password")
    private String nuovaPassword;

    @NotBlank(message = "Ripeti la nuova password")
    private String confermaPassword;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNuovaPassword() { return nuovaPassword; }
    public void setNuovaPassword(String nuovaPassword) { this.nuovaPassword = nuovaPassword; }
    public String getConfermaPassword() { return confermaPassword; }
    public void setConfermaPassword(String confermaPassword) { this.confermaPassword = confermaPassword; }
}
