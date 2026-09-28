-- RamaSQL Client - registro SQL esportato il 2026-09-28 20:29:32
-- Connessione: Test MySQL (MySQL 8.0.40)
-- Istruzioni: 4 riuscite; 1 non riuscite o interrotte (commentate, non vengono rieseguite: possono essere state applicate in parte, verifica)
-- Riferimenti al catalogo «<catalogo>» tolti: lo script si riesegue sul catalogo corrente

-- #1 20:29:32 · Editor SQL · OK · 0 righe · 9 ms
CREATE TABLE x (id INT PRIMARY KEY);

-- #2 20:29:32 · Editor SQL · OK · 2 righe · 1 ms
INSERT INTO x VALUES (1), (2);

-- #3 20:29:32 · Editor SQL · OK · 0 righe · 7 ms
CREATE TABLE y (id INT PRIMARY KEY);

-- #4 20:29:32 · Editor SQL · ERRORE 1051 (42S02): (conn=25030) Unknown table '<catalogo>.manca'
-- DROP TABLE x, manca;
-- (non riuscita: può essere stata applicata in parte: verifica sul server prima di rieseguire lo script)

-- #5 20:29:32 · Editor SQL · OK · 1 righe · 1 ms
INSERT INTO y VALUES (7);
