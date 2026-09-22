-- RamaSQL Client - fixture degli spike S2a, S2c, S2d (Step 1): una «biblioteca» ridotta ma realistica.
-- Caricata identica su MariaDB e MySQL. Accenti, apostrofi e NULL sono voluti.
-- Regole dello script (TestCatalog.runScript): «;» a fine riga chiude l'istruzione, «--» a inizio riga è un commento.

CREATE TABLE editori (
  id INT NOT NULL AUTO_INCREMENT,
  nome VARCHAR(80) NOT NULL,
  citta VARCHAR(60) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_editori_nome (nome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE autori (
  id INT NOT NULL AUTO_INCREMENT,
  nome VARCHAR(60) NOT NULL,
  cognome VARCHAR(60) NOT NULL,
  nazionalita VARCHAR(40) NULL,
  anno_nascita SMALLINT NULL,
  PRIMARY KEY (id),
  KEY ix_autori_cognome (cognome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE libri (
  id INT NOT NULL AUTO_INCREMENT,
  titolo VARCHAR(120) NOT NULL,
  anno SMALLINT NOT NULL,
  prezzo DECIMAL(6,2) NULL,
  copie INT NOT NULL DEFAULT 1,
  id_editore INT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_libri_titolo (titolo),
  KEY ix_libri_editore (id_editore),
  CONSTRAINT fk_libri_editori FOREIGN KEY (id_editore) REFERENCES editori (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE libri_autori (
  id_libro INT NOT NULL,
  id_autore INT NOT NULL,
  PRIMARY KEY (id_libro, id_autore),
  KEY ix_libri_autori_autore (id_autore),
  CONSTRAINT fk_la_libri FOREIGN KEY (id_libro) REFERENCES libri (id) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_la_autori FOREIGN KEY (id_autore) REFERENCES autori (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE soci (
  id INT NOT NULL AUTO_INCREMENT,
  nome VARCHAR(60) NOT NULL,
  cognome VARCHAR(60) NOT NULL,
  email VARCHAR(120) NULL,
  data_iscrizione DATE NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE prestiti (
  id INT NOT NULL AUTO_INCREMENT,
  id_libro INT NOT NULL,
  id_socio INT NOT NULL,
  data_prestito DATE NOT NULL,
  data_restituzione DATE NULL,
  PRIMARY KEY (id),
  KEY ix_prestiti_libro (id_libro),
  KEY ix_prestiti_socio (id_socio),
  CONSTRAINT fk_prestiti_libri FOREIGN KEY (id_libro) REFERENCES libri (id) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_prestiti_soci FOREIGN KEY (id_socio) REFERENCES soci (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO editori (id, nome, citta) VALUES
  (1, 'Einaudi', 'Torino'),
  (2, 'Mondadori', 'Milano'),
  (3, 'Feltrinelli', 'Milano'),
  (4, 'Adelphi', 'Milano'),
  (5, 'Sellerio', 'Palermo'),
  (6, 'L''Orma', NULL),
  (7, 'Edizioni e/o', 'Roma');

INSERT INTO autori (id, nome, cognome, nazionalita, anno_nascita) VALUES
  (1, 'Italo', 'Calvino', 'italiana', 1923),
  (2, 'Umberto', 'Eco', 'italiana', 1932),
  (3, 'Elsa', 'Morante', 'italiana', 1912),
  (4, 'Primo', 'Levi', 'italiana', 1919),
  (5, 'Andrea', 'Camilleri', 'italiana', 1925),
  (6, 'Elena', 'Ferrante', 'italiana', NULL),
  (7, 'Gabriel', 'García Márquez', 'colombiana', 1927),
  (8, 'Annie', 'Ernaux', 'francese', 1940),
  (9, 'Carlo', 'Fruttero', 'italiana', 1926),
  (10, 'Franco', 'Lucentini', 'italiana', 1920),
  (11, 'Niccolò', 'Ammaniti', 'italiana', 1966),
  (12, 'Gesualdo', 'Bufalino', NULL, 1920),
  (13, 'Beppe', 'Fenoglio', 'italiana', 1922);

INSERT INTO libri (id, titolo, anno, prezzo, copie, id_editore) VALUES
  (1, 'Il barone rampante', 1957, 12.50, 3, 1),
  (2, 'Le città invisibili', 1972, 11.00, 2, 1),
  (3, 'Se una notte d''inverno un viaggiatore', 1979, 13.00, 2, 1),
  (4, 'Il nome della rosa', 1980, 15.00, 4, NULL),
  (5, 'Il pendolo di Foucault', 1988, 16.50, 1, NULL),
  (6, 'La Storia', 1974, 18.00, 2, 1),
  (7, 'L''isola di Arturo', 1957, 12.00, 1, 1),
  (8, 'Se questo è un uomo', 1947, 10.50, 5, 1),
  (9, 'La tregua', 1963, 11.50, 2, 1),
  (10, 'La forma dell''acqua', 1994, 10.00, 3, 5),
  (11, 'Il cane di terracotta', 1996, 10.00, 2, 5),
  (12, 'L''amica geniale', 2011, 18.00, 4, 7),
  (13, 'Storia del nuovo cognome', 2012, 19.50, 2, 7),
  (14, 'Cent''anni di solitudine', 1967, 14.00, 3, 2),
  (15, 'L''amore ai tempi del colera', 1985, 14.50, 1, 2),
  (16, 'Gli anni', 2008, 17.00, 1, 6),
  (17, 'La donna della domenica', 1972, 13.50, 2, 2),
  (18, 'A che punto è la notte', 1979, NULL, 1, 2),
  (19, 'Io non ho paura', 2001, 12.00, 3, 1),
  (20, 'Diceria dell''untore', 1981, 11.00, 1, 5),
  (21, 'Una questione privata', 1963, NULL, 0, 1),
  (22, 'Il posto', 1983, 9.50, 2, 6);

INSERT INTO libri_autori (id_libro, id_autore) VALUES
  (1, 1), (2, 1), (3, 1), (4, 2), (5, 2), (6, 3), (7, 3), (8, 4), (9, 4), (10, 5), (11, 5),
  (12, 6), (13, 6), (14, 7), (15, 7), (16, 8), (17, 9), (17, 10), (18, 9), (18, 10), (19, 11),
  (20, 12), (22, 8);

INSERT INTO soci (id, nome, cognome, email, data_iscrizione) VALUES
  (1, 'Anna', 'Rossi', 'anna.rossi@example.org', '2019-09-16'),
  (2, 'Marco', 'Bianchi', 'marco.bianchi@example.org', '2020-01-10'),
  (3, 'Giulia', 'D''Angelo', NULL, '2020-10-05'),
  (4, 'Luca', 'Verdi', 'luca.verdi@example.org', '2021-03-22'),
  (5, 'Sofia', 'Esposito', NULL, '2022-09-12'),
  (6, 'Nicolò', 'Fabbri', 'nicolo.fabbri@example.org', '2023-02-01'),
  (7, 'Chiara', 'Dell''Acqua', 'chiara.dellacqua@example.org', '2023-11-20'),
  (8, 'Matteo', 'Ricci', NULL, '2024-09-09');

INSERT INTO prestiti (id, id_libro, id_socio, data_prestito, data_restituzione) VALUES
  (1, 1, 1, '2023-10-02', '2023-10-20'),
  (2, 4, 1, '2023-11-06', '2023-12-01'),
  (3, 8, 2, '2023-11-10', '2023-11-30'),
  (4, 12, 3, '2024-01-15', '2024-02-10'),
  (5, 13, 3, '2024-02-12', '2024-03-04'),
  (6, 14, 4, '2024-02-20', NULL),
  (7, 10, 2, '2024-03-01', '2024-03-15'),
  (8, 11, 2, '2024-03-18', '2024-04-02'),
  (9, 4, 4, '2024-04-08', '2024-05-03'),
  (10, 2, 6, '2024-04-22', '2024-05-06'),
  (11, 8, 6, '2024-05-13', '2024-06-03'),
  (12, 19, 7, '2024-09-16', '2024-09-30'),
  (13, 12, 7, '2024-10-01', '2024-10-28'),
  (14, 6, 1, '2024-10-14', NULL),
  (15, 17, 4, '2024-11-04', '2024-11-25'),
  (16, 4, 6, '2024-11-11', NULL),
  (17, 8, 3, '2025-01-13', '2025-02-03'),
  (18, 16, 7, '2025-01-20', '2025-02-10'),
  (19, 3, 1, '2025-02-17', '2025-03-10'),
  (20, 12, 2, '2025-03-03', NULL),
  (21, 9, 6, '2025-03-10', '2025-03-31'),
  (22, 1, 4, '2025-04-07', '2025-04-28'),
  (23, 15, 3, '2025-05-05', NULL),
  (24, 8, 7, '2025-05-12', '2025-06-02'),
  (25, 20, 1, '2025-09-15', NULL);
