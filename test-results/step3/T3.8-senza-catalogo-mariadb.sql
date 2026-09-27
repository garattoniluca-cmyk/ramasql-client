-- RamaSQL Client - registro SQL esportato il 2026-09-28 00:04:00
-- Connessione: Test MariaDB (MariaDB 11.5.2)
-- Istruzioni: 8 riuscite; 0 non riuscite o interrotte (commentate, non vengono rieseguite: possono essere state applicate in parte, verifica)
-- Riferimenti al catalogo «<origine>» tolti: lo script si riesegue sul catalogo corrente

-- #1 00:04:00 · Editor SQL · OK · 1 righe · 0 ms
-- CREATE DATABASE `<origine>` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- (istruzione sul catalogo d'origine «<origine>» omessa: rieseguire lo script non deve mai crearlo, modificarlo o eliminarlo)

-- #2 00:04:00 · Editor SQL · OK · 0 righe · 0 ms
-- USE `<origine>`;
-- (USE del catalogo d'origine omesso)

-- #3 00:04:00 · Editor SQL · OK · 0 righe · 2 ms
-- (istruzione scritta a mano: tolti i riferimenti al catalogo «<origine>», controlla che faccia ancora ciò che vuoi)
CREATE TABLE `t` (id INT PRIMARY KEY, nota VARCHAR(20));

-- #4 00:04:00 · Editor SQL · OK · 1 righe · 2 ms
-- INSERT INTO <origine> . t VALUES (1, 'spazi');
-- (omessa: nomina ancora il catalogo d'origine «<origine>» in una forma che non si può togliere con sicurezza; adattala a mano se serve)

-- #5 00:04:00 · Editor SQL · OK · 1 righe · 0 ms
INSERT INTO t VALUES (2, 'corrente');

-- #6 00:04:00 · Editor SQL · OK · 1 righe · 0 ms
-- ALTER DATABASE `<origine>` COLLATE utf8mb4_bin;
-- (istruzione sul catalogo d'origine «<origine>» omessa: rieseguire lo script non deve mai crearlo, modificarlo o eliminarlo)

-- #7 00:04:00 · Editor SQL · OK · 0 righe · 3 ms
CREATE TABLE u (x INT);

-- #8 00:04:00 · Editor SQL · OK · 2 righe · 6 ms
-- DROP DATABASE `<origine>`;
-- (istruzione sul catalogo d'origine «<origine>» omessa: rieseguire lo script non deve mai crearlo, modificarlo o eliminarlo)
