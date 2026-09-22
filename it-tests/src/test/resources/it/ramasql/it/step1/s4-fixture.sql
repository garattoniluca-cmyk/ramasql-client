-- Fixture dello spike S4 (driver unico?): due tabelle InnoDB con chiave esterna CASCADE,
-- indice UNIQUE composto, colonne UNSIGNED, DEFAULT, AUTO_INCREMENT, commenti, emoji utf8mb4.
-- I commenti NON contengono emoji: i due server li conservano in utf8mb3 e le trasformano in «?»
-- (verificato anche con il client nativo: non dipende dal driver). Le emoji stanno nei dati.
-- Si esegue dentro un catalogo ramasql_test_* creato da TestCatalog.

CREATE TABLE autore (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nome VARCHAR(80) NOT NULL COMMENT 'Nome e cognome',
  nazione CHAR(2) NOT NULL DEFAULT 'IT',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Autori dei libri';

CREATE TABLE libro (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  autore_id INT UNSIGNED NOT NULL,
  titolo VARCHAR(120) NOT NULL COMMENT 'Titolo: àèìòù €',
  anno SMALLINT UNSIGNED NULL,
  prezzo DECIMAL(8,2) NOT NULL DEFAULT 9.90,
  pubblicato DATE NULL,
  registrato DATETIME NULL,
  disponibile TINYINT(1) NOT NULL DEFAULT 1,
  flag BIT(1) NULL,
  maschera BIT(8) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_libro_autore_titolo (autore_id, titolo),
  KEY ix_libro_anno (anno),
  CONSTRAINT fk_libro_autore FOREIGN KEY (autore_id) REFERENCES autore (id)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO autore (nome, nazione) VALUES ('Italo Calvino 😀', 'IT'), ('Jorge Luis Borges', 'AR');

INSERT INTO libro (autore_id, titolo, anno, prezzo, pubblicato, registrato, disponibile, flag, maschera) VALUES
  (1, 'Il barone rampante 🌳', 1957, 12.50, '1957-06-01', '2026-09-21 10:30:45', 1, b'1', b'10100101'),
  (2, 'Finzioni', 1944, 1234.05, '1944-01-15', '2026-09-21 23:59:59', 0, b'0', b'00000001');
