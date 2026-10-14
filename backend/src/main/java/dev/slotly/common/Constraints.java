package dev.slotly.common;

import org.springframework.dao.DataIntegrityViolationException;

public final class Constraints {

    private Constraints() {
    }

    /** True if the DB rejected the write because of the named constraint. */
    public static boolean isViolated(DataIntegrityViolationException e, String constraintName) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
        }
        return false;
    }
}
