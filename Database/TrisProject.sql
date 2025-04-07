--------------------------------------------------------------------------------------------------------------------------------------
-- DATABASE
--------------------------------------------------------------------------------------------------------------------------------------
DROP SCHEMA IF EXISTS tris CASCADE;
CREATE SCHEMA tris;

--Tabella Partita
CREATE TABLE tris.Partita(
    IdPartita SERIAL,

    CONSTRAINT PK_Partita PRIMARY KEY (IdPartita)
);


--Tabella Giocatore
CREATE TABLE tris.Giocatore(
    IdGiocatore SERIAL,
    Username    VARCHAR(32) NOT NULL,
    IdPartita   INTEGER,

    CONSTRAINT PK_Giocatore PRIMARY KEY (IdGiocatore),
    CONSTRAINT FK_Partita FOREIGN KEY (IdPartita) REFERENCES tris.Partita(IdPartita)
);

--Tabella Mossa
CREATE TABLE tris.Mossa(
    IdMossa  SERIAL,
    Username VARCHAR(32) NOT NULL,

    CONSTRAINT PK_Mossa PRIMARY KEY (IdMossa)
);