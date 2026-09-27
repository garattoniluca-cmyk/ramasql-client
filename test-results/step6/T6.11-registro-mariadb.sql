-- RamaSQL Client - registro SQL esportato il 2026-09-28 00:01:40
-- Connessione: MariaDB locale (MariaDB 11.5.2)
-- Istruzioni: 37 riuscite; 0 non riuscite o interrotte (commentate, non vengono rieseguite: possono essere state applicate in parte, verifica)
-- Riferimenti al catalogo «ramasql_test_ui_m1a_3et7v2» tolti: lo script si riesegue sul catalogo corrente

-- #1 00:01:35 · Navigatore · OK · 1 righe · 0 ms
-- CREATE DATABASE `ramasql_test_ui_m1a_3et7v2` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
-- (istruzione sul catalogo d'origine «ramasql_test_ui_m1a_3et7v2» omessa: rieseguire lo script non deve mai crearlo, modificarlo o eliminarlo)

-- #2 00:01:39 · Editor di tabelle · OK · 0 righe · 5 ms
CREATE TABLE `editori` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `nome` VARCHAR(80) NOT NULL,
  `citta` VARCHAR(60) NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uq_editori_nome` (`nome`)
) ENGINE=InnoDB;

-- #3 00:01:39 · Editor di tabelle · OK · 0 righe · 3 ms
CREATE TABLE `autori` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `cognome` VARCHAR(60) NOT NULL,
  `nome` VARCHAR(60) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

-- #4 00:01:40 · Editor di tabelle · OK · 0 righe · 5 ms
CREATE TABLE `libri` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `titolo` VARCHAR(150) NOT NULL,
  `isbn` CHAR(10) NULL,
  `id_editore` INT UNSIGNED NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uq_libri_isbn` (`isbn`),
  CONSTRAINT `fk_libri_editori` FOREIGN KEY (`id_editore`) REFERENCES `editori` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

-- #5 00:01:40 · Editor di tabelle · OK · 0 righe · 4 ms
CREATE TABLE `libri_autori` (
  `id_libro` INT UNSIGNED NOT NULL,
  `id_autore` INT UNSIGNED NOT NULL,
  PRIMARY KEY (`id_libro`, `id_autore`),
  CONSTRAINT `fk_libri_autori_libri` FOREIGN KEY (`id_libro`) REFERENCES `libri` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_libri_autori_autori` FOREIGN KEY (`id_autore`) REFERENCES `autori` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB;

-- #6 00:01:40 · Griglia · OK · 0 righe · 0 ms
SELECT `id`, `nome`, `citta` FROM `editori` LIMIT 1001;

-- #7 00:01:40 · Griglia · OK · 1 righe · 2 ms
INSERT INTO `editori` (`nome`, `citta`) VALUES ('Einaudi', 'Torino');

-- #8 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `editori` (`nome`, `citta`) VALUES ('Adelphi', 'Milano');

-- #9 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `editori` (`nome`, `citta`) VALUES ('Sellerio', 'Palermo');

-- #10 00:01:40 · Griglia · OK · 3 righe · 0 ms
SELECT `id`, `nome`, `citta` FROM `editori` LIMIT 1001;

-- #11 00:01:40 · Griglia · OK · 0 righe · 0 ms
SELECT `id`, `cognome`, `nome` FROM `autori` LIMIT 1001;

-- #12 00:01:40 · Griglia · OK · 1 righe · 1 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore01', 'Nome 1');

-- #13 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore02', 'Nome 2');

-- #14 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore03', 'Nicolò');

-- #15 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore04', 'Nome 4');

-- #16 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore05', 'Nome 5');

-- #17 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore06', 'Nicolò');

-- #18 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore07', 'Nome 7');

-- #19 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore08', 'Nome 8');

-- #20 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore09', 'Nicolò');

-- #21 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore10', 'Nome 10');

-- #22 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore11', 'Nome 11');

-- #23 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore12', 'Nicolò');

-- #24 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore13', 'Nome 13');

-- #25 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore14', 'Nome 14');

-- #26 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore15', 'Nicolò');

-- #27 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore16', 'Nome 16');

-- #28 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore17', 'Nome 17');

-- #29 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore18', 'Nicolò');

-- #30 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore19', 'Nome 19');

-- #31 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Autore20', 'Nome 20');

-- #32 00:01:40 · Griglia · OK · 20 righe · 0 ms
SELECT `id`, `cognome`, `nome` FROM `autori` LIMIT 1001;

-- #33 00:01:40 · Griglia · OK · 0 righe · 0 ms
SELECT `id`, `titolo`, `isbn`, `id_editore` FROM `libri` LIMIT 1001;

-- #34 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `libri` (`titolo`, `isbn`, `id_editore`) VALUES ('Se questo è un uomo', '9788806001', 1);

-- #35 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `libri` (`titolo`, `isbn`, `id_editore`) VALUES ('Il deserto dei Tartari', '9788845902', 2);

-- #36 00:01:40 · Griglia · OK · 1 righe · 0 ms
INSERT INTO `libri` (`titolo`, `isbn`, `id_editore`) VALUES ('Il birraio di Preston', '9788838903', 3);

-- #37 00:01:40 · Griglia · OK · 3 righe · 0 ms
SELECT `id`, `titolo`, `isbn`, `id_editore` FROM `libri` LIMIT 1001;
