-- FILE: V2__dati_di_prova.sql
-- A COSA SERVE: inserisce i dati di prova (4 corsi, 2 utenti e le loro iscrizioni). Prende il posto del
-- vecchio seeder Java (DatiIniziali): ora il database è PERMANENTE, quindi i dati si inseriscono UNA SOLA
-- VOLTA con una migration, invece di ricrearli a ogni avvio come con H2 in memoria.
-- (In un progetto reale i dati di prova non si metterebbero nelle migration di produzione.)

INSERT INTO corso (nome, descrizione, durata_minuti, livello) VALUES
    ('Yoga',     'Stretching e respirazione',          60, 'PRINCIPIANTE'),
    ('Spinning', 'Ciclismo indoor ad alta intensità',  45, 'INTERMEDIO'),
    ('CrossFit', 'Allenamento funzionale',             60, 'AVANZATO'),
    ('Pilates',  'Tonificazione e postura',            50, 'PRINCIPIANTE');

INSERT INTO utente (nome, cognome, email) VALUES
    ('Mario', 'Rossi',   'mario.rossi@example.com'),
    ('Laura', 'Bianchi', 'laura.bianchi@example.com');

-- Iscrizioni: gli id non si conoscono in anticipo, quindi si cercano con una SELECT per nome ed email.
INSERT INTO utente_corso (utente_id, corso_id)
SELECT u.id, c.id FROM utente u, corso c
WHERE u.email = 'mario.rossi@example.com' AND c.nome IN ('Yoga', 'Spinning');

INSERT INTO utente_corso (utente_id, corso_id)
SELECT u.id, c.id FROM utente u, corso c
WHERE u.email = 'laura.bianchi@example.com' AND c.nome = 'Pilates';
