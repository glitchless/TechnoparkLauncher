package ru.lionzxy.tplauncher.utils

import io.sentry.Sentry
import io.sentry.protocol.User
import ru.lionzxy.tplauncher.config.Profile

/**
 * Attaches the logged-in profile to Sentry as the current user (Sentry 8.x static API).
 * Replaces the removed 1.x `Sentry.getContext().setUser(...)`. Passing null clears the user.
 *
 * Note: this also fixes a latent bug in the old `Context.setUser` extension, which built a
 * `User` object and never assigned it (a no-op).
 */
fun setSentryUser(profile: Profile?) {
    if (profile == null) {
        Sentry.setUser(null)
        return
    }
    Sentry.setUser(User().apply {
        id = profile.profileId       // ISession UUID
        username = profile.login     // ISession username
        email = profile.email
    })
}
