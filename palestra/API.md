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

### In Postman: collection già pronta
1. Postman → **Import** → scegli il file `palestra/postman/palestra.postman_collection.json` (o trascinalo nella finestra).
2. Apri la collection **Palestra API** → scheda **Variables** → nella riga `password` scrivi la password dell'admin nella colonna
   *Current value* e premi **Save** (`base` è già `http://localhost:8080`, `email` è `admin@palestra.local`).
3. Esegui **Auth → Login**: se riesce (200) il token viene salvato da solo nella variabile `token`.
4. Da qui in poi tutte le altre richieste usano quel token (è impostato come *Bearer Token* sull'intera collection).
   Le richieste di creazione (`Crea corso`, `Crea utente`, `Crea abbonamento`) salvano l'id creato, che le richieste
   successive (dettaglio, modifica, elimina) usano da sole. Se ricevi 401, il token è scaduto: rifai il Login.

### In Bruno / Postman (a mano)
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

## 5. Se ricevi 401 o 403: come capire perché

La risposta al client è volutamente generica ("Token mancante, scaduto o non valido"). Il **motivo vero** lo scrive l'app nel
suo **log**, cioè nel terminale di VS Code in cui gira. Cerca la riga `WARN ... API GET /api/... rifiutata con 401`:

| Cosa dice il log | Significato | Cosa fare |
|---|---|---|
| `intestazione Authorization ASSENTE` | il client non ha inviato il token. In Postman succede **quasi sempre perché la variabile `token` è vuota**: se il token è vuoto, Postman non aggiunge nemmeno l'intestazione | rifai **Auth → Login** e controlla che risponda **200** (se dà 401 controlla `email` e `password` nelle Variables della collection); poi riprova |
| `Bearer token is malformed` | l'intestazione c'è ma il token è vuoto o rotto | la variabile `token` è vuota o non risolta: rifai **Auth → Login** e controlla che dia 200 |
| `Jwt expired at ...` | token scaduto (dura 60 minuti) | rifai il Login |
| `Invalid signature` | token firmato con un'altra chiave (es. emesso da un'altra installazione o con un `JWT_SECRET` diverso) | rifai il Login sull'app che stai interrogando |
| `negata con 403` | token valido ma senza ruolo ADMIN | usa un admin |

In Postman, per vedere cosa parte davvero: **View → Show Postman Console** e rilancia la richiesta: si vede l'intestazione
`Authorization` inviata. Se vedi `Bearer {{token}}` (con le graffe) o `Bearer ` vuoto, la variabile non è stata sostituita:
controlla che nella collection la variabile `token` abbia un *Current value* e che **non sia selezionato un Environment**
(in alto a destra) con una variabile `token` o `base` che la sovrascrive.

Il log compare solo dopo aver **riavviato l'app** con il codice aggiornato.

## 6. Sicurezza in breve

- Niente sessioni né cookie: ogni richiesta porta il proprio token (per questo non serve il token CSRF).
- Il token è firmato con la chiave `app.jwt.secret` (**in produzione va impostata con la variabile d'ambiente
  `JWT_SECRET`**, lunga e casuale). Chi la conosce può fabbricare token validi.
- Il contenuto di un token è leggibile da chiunque (provalo su jwt.io): non vi si mette mai nulla di segreto.
- Un token resta valido fino alla scadenza anche se l'utente nel frattempo viene eliminato o perde il ruolo
  (il server non ricorda i token emessi). Per questo la durata è breve.
