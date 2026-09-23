-- RamaSQL Client - fixture di prova dei metadati "tipi speciali" (Step 3, revisione del normalizzatore).
-- Separata da biblioteca.sql: ZEROFILL, TINYINT(1), TIMESTAMP con ON UPDATE, ENUM con apostrofo, JSON,
-- indice FULLTEXT, indice su prefisso, indice DESC, chiave esterna con azioni omesse.
-- Valida su MariaDB >= 10.8 e MySQL 8.0. Si esegue dentro un catalogo di test (ramasql_test_*).

CREATE TABLE categorie (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nome VARCHAR(50) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE articoli (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  codice INT(6) UNSIGNED ZEROFILL NOT NULL,
  attivo TINYINT(1) NOT NULL DEFAULT 1,
  stato ENUM('bozza','pubblicato','l''archivio') NOT NULL DEFAULT 'bozza',
  dati JSON NULL,
  titolo VARCHAR(200) NOT NULL,
  testo TEXT NULL,
  anno SMALLINT NOT NULL,
  id_categoria INT UNSIGNED NULL,
  modificato TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_codice (codice),
  KEY ix_titolo_prefisso (titolo(20)),
  KEY ix_anno_desc (anno DESC),
  FULLTEXT KEY ft_testo (titolo, testo),
  KEY ix_categoria (id_categoria),
  CONSTRAINT fk_articoli_categoria FOREIGN KEY (id_categoria) REFERENCES categorie (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO categorie (nome) VALUES ('Narrativa'), ('Saggistica');
INSERT INTO articoli (codice, attivo, stato, dati, titolo, testo, anno, id_categoria)
VALUES (42, 1, 'bozza', '{"pagine": 120}', 'Il nome della rosa', 'Un giallo in un monastero', 1980, 1),
       (7, 0, 'l''archivio', NULL, 'Lezioni americane', NULL, 1988, 2);
