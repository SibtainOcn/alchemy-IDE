package com.sibtainocn.alchemy

import com.sibtainocn.alchemy.exec.Readiness
import com.sibtainocn.alchemy.exec.Termux
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The setup ladder, which is the whole user experience of this feature. Every rung needs
 * its own answer on screen: one generic "could not run" is what makes an integration
 * like this feel broken rather than unfinished.
 *
 * Only compiled into the F-Droid build, alongside the code it covers.
 */
class TermuxReadinessTest {

    private fun readiness(
        installed: Boolean = true,
        installer: String? = "org.fdroid.fdroid",
        versionCode: Long? = 118,
        permission: Boolean = true,
    ) = Termux.readinessOf(installed, installer, versionCode, permission)

    @Test
    fun `everything in place reads as ready`() {
        assertEquals(Readiness.Ready, readiness())
    }

    @Test
    fun `not installed is reported before anything else`() {
        assertEquals(Readiness.RunnerMissing, readiness(installed = false, permission = false))
    }

    @Test
    fun `a Play Store Termux is called out specifically`() {
        // That copy is frozen years behind and cannot be updated into a working one, so
        // it has to be replaced rather than fixed. Saying "too old" would send the user
        // to check for updates that will never come.
        assertEquals(
            Readiness.RunnerFromAppStore,
            readiness(installer = Termux.PLAY_STORE_PACKAGE),
        )
    }

    @Test
    fun `a sideloaded Termux is not mistaken for a store one`() {
        assertEquals(Readiness.Ready, readiness(installer = null))
    }

    @Test
    fun `a version below the interface we use is reported as too old`() {
        val readiness = readiness(versionCode = 117)
        assertTrue(readiness is Readiness.RunnerTooOld)
    }

    @Test
    fun `an unreadable version is not treated as too old`() {
        // Better to try and report a real failure than to refuse over a fact we could not
        // establish.
        assertEquals(Readiness.Ready, readiness(versionCode = null))
    }

    @Test
    fun `a missing permission is the last rung we can see from here`() {
        assertEquals(Readiness.PermissionMissing, readiness(permission = false))
    }

    @Test
    fun `a refused command is recognised by what Termux says about it`() {
        assertTrue(
            Termux.rejectedForExternalApps(
                "RUN_COMMAND: allow-external-apps property is not set to true",
            )
        )
        assertTrue(Termux.rejectedForExternalApps("Allow-External-Apps must be enabled"))
    }

    @Test
    fun `an ordinary failure is not mistaken for a refused one`() {
        assertTrue(!Termux.rejectedForExternalApps("python: command not found"))
        assertTrue(!Termux.rejectedForExternalApps(null))
    }

    @Test
    fun `the setup line repairs the property rather than appending to it`() {
        // Appending only when the line is absent leaves a file that already carries the
        // setting in some other shape exactly as it was: commented out, spaced
        // differently, or set to false. Every form goes first, then one clean line, which
        // also makes the command safe to paste again when something else went wrong.
        val command = Termux.ENABLE_EXTERNAL_APPS
        assertTrue("Removes whatever is there", command.contains("sed -i '/allow-external-apps/d'"))
        assertTrue("Writes one clean line", command.contains("echo 'allow-external-apps=true'"))
        assertTrue("Makes Termux re-read it", command.contains("termux-reload-settings"))
        assertTrue("Says so when it worked", command.trimEnd().endsWith("echo done"))
    }

    @Test
    fun `the handshake is something a working runner answers instantly`() {
        // A refused command produces a Termux notification and no reply at all, so
        // silence is the only signal there is. That makes the wait for this the whole
        // detection, and it has to be a command that cannot be slow for any other reason.
        assertTrue(Termux.HANDSHAKE.startsWith("echo "))
        assertTrue(Termux.HANDSHAKE.contains(Termux.HANDSHAKE_REPLY))
    }
}
