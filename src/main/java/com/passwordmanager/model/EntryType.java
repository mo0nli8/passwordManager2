package com.passwordmanager.model;

/** The four supported vault entry types. */
public enum EntryType {
    LOGIN, NOTE, CARD, IDENTITY;

    /** Returns the enum matching a DB string (case-insensitive). */
    public static EntryType from(String name) {
        return valueOf(name.toUpperCase());
    }
}
