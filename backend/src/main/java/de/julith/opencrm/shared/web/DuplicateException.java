package de.julith.opencrm.shared.web;

/** Fachliche Dublette (docs/10: duplicate_found, HTTP 409). */
public class DuplicateException extends RuntimeException {

    public DuplicateException(String message) {
        super(message);
    }
}
