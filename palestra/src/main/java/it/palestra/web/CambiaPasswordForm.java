/*
 * FILE: CambiaPasswordForm.java
 * A COSA SERVE: i tre campi del form "Cambia password" (password attuale, nuova, conferma) con le regole
 * semplici di validazione. Le regole più complesse (l'attuale è giusta? la nuova è lunga abbastanza? le due
 * coincidono?) sono nel controller e nel service.
 * PARAGONE LARAVEL: una FormRequest con 'current_password', 'password' e 'password_confirmation'.
 */
package it.palestra.web;

import jakarta.validation.constraints.NotBlank;

public class CambiaPasswordForm {

    @NotBlank(message = "Scrivi la password attuale")
    private String passwordAttuale;

    @NotBlank(message = "Scrivi la nuova password")
    private String nuovaPassword;

    @NotBlank(message = "Ripeti la nuova password")
    private String confermaPassword;

    public String getPasswordAttuale() { return passwordAttuale; }
    public void setPasswordAttuale(String passwordAttuale) { this.passwordAttuale = passwordAttuale; }
    public String getNuovaPassword() { return nuovaPassword; }
    public void setNuovaPassword(String nuovaPassword) { this.nuovaPassword = nuovaPassword; }
    public String getConfermaPassword() { return confermaPassword; }
    public void setConfermaPassword(String confermaPassword) { this.confermaPassword = confermaPassword; }
}
