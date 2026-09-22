/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

/**
 * Suggerimento di join tra due tabelle: una chiave esterna reale oppure una relazione logica
 * del modello ER (docs/GLOSSARY.md). Il lato «primario» è la tabella referenziata.
 *
 * @param name          nome della FK o della relazione (può essere {@code null})
 * @param primaryTable  tabella referenziata
 * @param primaryColumn colonna referenziata
 * @param foreignTable  tabella che referenzia
 * @param foreignColumn colonna che referenzia
 */
public record JoinHint(String name, String primaryTable, String primaryColumn,
                       String foreignTable, String foreignColumn) {
}
