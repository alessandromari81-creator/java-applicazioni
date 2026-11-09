# API REST della Palestra

La stessa applicazione che mostra le pagine HTML espone anche una API in **JSON**, sotto `/api`. Usa gli stessi
dati e le stesse regole delle pagine (stessi *service*). Avvio: vedi `CLAUDE.md` (`docker compose up -d` e
`./mvnw spring-boot:run`), indirizzo base `http://localhost:8080`.

## 1. Autenticazione (JWT)

1. **Login**: `POST /api/auth/login` con email e password di un utente che ha una password (di sviluppo:
   `admin@palestra.local` / la password che hai impostato).
   ```bash
   curl -s -X POST http://localhost:8080/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@palestra.local","password":"LA-TUA-PASSWORD"}'
   # {"token":"eyJ...","tipo":"Bearer","scadeTraSecondi":3600}
   ```
2. **Usa il token** in ogni richiesta, con l'intestazione `Authorization: Bearer <token>`:
   ```bash
   curl -s http://localhost:8080/api/corsi -H "Authorization: Bearer $TOKEN"
   ```
3. Il token dura **60 minuti**; poi si rifà il login. Serve il ruolo **ADMIN** (un iscritto con password ottiene
   un token ma riceve 403).

### In Bruno / Postman
- Crea una richiesta `POST {{base}}/api/auth/login` con corpo JSON; copia il `token` della risposta.
- Nelle altre richieste: scheda **Auth** → tipo **Bearer Token** → incolla il token (meglio in una variabile
  d'ambiente `token`, e `base` = `http://localhost:8080`).
- Per i corpi JSON ricorda l'intestazione `Content-Type: application/json` (di solito la imposta da solo).

## 2. Endpoint

| Metodo | Indirizzo | Risposta | Note |
|---|---|---|---|
| POST | `/api/auth/login` | 200 | pubblico, restituisce il token |
| GET | `/api/corsi` | 200 | elenco |
| GET | `/api/corsi/{id}` | 200 / 404 | |
| POST | `/api/corsi` | 201 / 400 | `Location` con l'indirizzo del nuovo corso |
| PUT | `/api/corsi/{id}` | 200 / 400 / 404 | **sostituisce** tutti i campi |
| DELETE | `/api/corsi/{id}` | 204 / 404 / 409 | 409 se ha iscritti |
| GET | `/api/utenti` | 200 | ruoli, corsi frequentati e corsi tenuti |
| GET | `/api/utenti/{id}` | 200 / 404 | |
| POST | `/api/utenti` | 201 / 400 | |
| PUT | `/api/utenti/{id}` | 200 / 400 / 404 / 409 | **sostituisce** ruoli e corsi; password omessa = invariata |
| DELETE | `/api/utenti/{id}` | 204 / 404 / 409 | 409: ultimo admin, istruttore di corsi, ha abbonamenti |
| GET | `/api/abbonamenti` | 200 | tutti |
| GET | `/api/abbonamenti/{id}` | 200 / 404 | |
| DELETE | `/api/abbonamenti/{id}` | 204 / 404 / 409 | solo quelli non ancora iniziati |
| GET | `/api/utenti/{id}/abbonamenti` | 200 / 404 | storico dell'utente, dal più recente |
| GET | `/api/utenti/{id}/abbonamenti/primo-giorno-libero` | 200 | `{"data":"aaaa-mm-gg"}` per un rinnovo |
| POST | `/api/utenti/{id}/abbonamenti` | 201 / 400 / 404 | |

## 3. Esempi di corpo (JSON)

**Corso** (`POST`/`PUT /api/corsi`):
```json
{ "nome": "Zumba", "descrizione": "Danza", "durataMinuti": 55, "livello": "INTERMEDIO", "istruttoreId": 3 }
```
`livello`: `PRINCIPIANTE`, `INTERMEDIO`, `AVANZATO`. `istruttoreId` facoltativo (un utente con ruolo `ISTRUTTORE`).

**Utente** (`POST`/`PUT /api/utenti`):
```json
{ "nome": "Mario", "cognome": "Rossi", "email": "mario@example.com",
  "password": "obbligatoria-solo-per-ADMIN", "ruoli": ["ISCRITTO"], "corsiIds": [1, 2] }
```
Ruoli per **nome** (`ADMIN`, `ISTRUTTORE`, `ISCRITTO`). La risposta non contiene mai la password: c'è solo `haPassword`.

**Abbonamento** (`POST /api/utenti/{id}/abbonamenti`):
```json
{ "tipo": "TRIMESTRALE", "dataInizio": "2026-12-09" }
```
`tipo`: `MENSILE`, `TRIMESTRALE`, `ANNUALE`. Data di fine e prezzo li calcola il server; la risposta ha anche `stato`
(`FUTURO`, `ATTIVO`, `SCADUTO`).

## 4. Errori

Sempre JSON, con questa forma:
```json
{ "status": 400, "errore": "Dati non validi", "messaggio": "Alcuni campi non sono validi",
  "campi": { "nome": ["Il nome è obbligatorio"] } }
```

| Codice | Quando |
|---|---|
| 400 | dati sbagliati: JSON rotto, campi obbligatori, regole sui campi (email già usata, password, data nel passato, sovrapposizione...). Se riguarda dei campi c'è `campi` |
| 401 | token mancante, scaduto, modificato, o credenziali di login errate |
| 403 | token valido ma senza il ruolo ADMIN |
| 404 | risorsa inesistente |
| 409 | operazione rifiutata da una regola sullo stato attuale (ultimo admin, corso con iscritti, abbonamento già iniziato...) |

Nota: un indirizzo `/api/...` che non esiste risponde 404 nel formato standard di Spring Boot
(`timestamp`, `status`, `error`, `path`), non in quello sopra.

## 5. Sicurezza in breve

- Niente sessioni né cookie: ogni richiesta porta il proprio token (per questo non serve il token CSRF).
- Il token è firmato con la chiave `app.jwt.secret` (**in produzione va impostata con la variabile d'ambiente
  `JWT_SECRET`**, lunga e casuale). Chi la conosce può fabbricare token validi.
- Il contenuto di un token è leggibile da chiunque (provalo su jwt.io): non vi si mette mai nulla di segreto.
- Un token resta valido fino alla scadenza anche se l'utente nel frattempo viene eliminato o perde il ruolo
  (il server non ricorda i token emessi). Per questo la durata è breve.
