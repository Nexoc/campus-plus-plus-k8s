package at.campus.backend.security;

/**
 * Canonical role names used inside the backend.
 *
 * Incoming role headers are normalized to this uppercase format.
 */
public final class Roles {

    public static final String APPLICANT = "APPLICANT";
    public static final String STUDENT = "STUDENT";
    public static final String MODERATOR = "MODERATOR";

    private Roles() {
    }
}
