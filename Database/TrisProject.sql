--------------------------------------------------------------------------------------------------------------------------------------
-- DATABASE
--------------------------------------------------------------------------------------------------------------------------------------
DROP SCHEMA IF EXISTS tris CASCADE;
CREATE SCHEMA tris;

--Tabella Partita
CREATE TABLE tris.Partita(
    IdPartita SERIAL PRIMARY KEY,
    Stato     VARCHAR(20) NOT NULL  -- terminata, in corso, in attesa, nuova creazione
);

--Tabella Giocatore
CREATE TABLE tris.Giocatore(
    IdGiocatore    SERIAL PRIMARY KEY,
    Username       VARCHAR(32) NOT NULL UNIQUE
);

--Tabella SessioneGioco (ponte)
CREATE TABLE tris.SessioneGioco (
    IdGiocatore    INTEGER,
    IdPartita      INTEGER,   -- può essere NULL se non sta partecipando a nessuna
    EsitoGiocatore VARCHAR(20), -- vittoria, sconfitta, pareggio

    PRIMARY KEY (IdGiocatore, IdPartita),
    CONSTRAINT FK_Giocatore FOREIGN KEY (IdGiocatore) REFERENCES tris.Giocatore(IdGiocatore),
    CONSTRAINT FK_Partita FOREIGN KEY (IdPartita) REFERENCES tris.Partita(IdPartita)
);


--Tabella Mossa
CREATE TABLE tris.Mossa(
    IdMossa     SERIAL,
    Posizione   INTEGER NOT NULL CHECK (Posizione BETWEEN 1 AND 9),
    IdPartita   INTEGER NOT NULL,
    IdGiocatore INTEGER NOT NULL,

    CONSTRAINT PK_Mossa PRIMARY KEY (IdMossa),
    CONSTRAINT FK_Partita FOREIGN KEY (IdPartita) REFERENCES tris.Partita(IdPartita),
    CONSTRAINT FK_Giocatore FOREIGN KEY (IdGiocatore) REFERENCES tris.Giocatore(IdGiocatore)
);


--------------------------------------------------------------------------------------------------------------------------------------
-- TRIGGER E FUNZIONI
--------------------------------------------------------------------------------------------------------------------------------------

-- funzione che permette di giocare ad una sola partita alla volta
--