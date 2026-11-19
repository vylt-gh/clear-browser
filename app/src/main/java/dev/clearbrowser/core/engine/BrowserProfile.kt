package dev.clearbrowser.core.engine

/**
 * Which storage partition a session belongs to.
 *
 * Incognito uses ephemeral private mode sessions where the private profile
 * is erased on exit, ensuring a private window genuinely cannot see or write
 * the normal window's state.
 */
enum class BrowserProfile(
    internal val profileName: String?,
) {
    DEFAULT(null),
    INCOGNITO("dev.clearbrowser.incognito"),
    ;

    val isIncognito: Boolean get() = this == INCOGNITO
}
