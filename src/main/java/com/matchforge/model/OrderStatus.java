package com.matchforge.model;

/**
 * Order lifecycle state machine:
 *   NEW -> PARTIALLY_FILLED -> FILLED
 *   NEW -> FILLED
 *   NEW / PARTIALLY_FILLED -> CANCELED
 *   NEW -> REJECTED
 * Transitions are enforced in Order itself.
 */
public enum OrderStatus {
    NEW, PARTIALLY_FILLED, FILLED, CANCELED, REJECTED
}
