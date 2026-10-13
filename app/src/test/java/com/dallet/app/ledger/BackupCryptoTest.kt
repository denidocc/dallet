package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    private val plain = """{"app":"dallet","schemaVersion":2,"accounts":[]}"""

    @Test
    fun roundTripsWithTheSamePassword() {
        val envelope = BackupCrypto.encrypt(plain, "секрет-123")
        assertThat(BackupCrypto.isEncrypted(envelope)).isTrue()
        assertThat(envelope).doesNotContain("accounts")
        assertThat(BackupCrypto.decrypt(envelope, "секрет-123")).isEqualTo(plain)
    }

    @Test
    fun plainBackupIsNotEncrypted() {
        assertThat(BackupCrypto.isEncrypted(plain)).isFalse()
        assertThat(BackupCrypto.isEncrypted("not json")).isFalse()
    }

    @Test
    fun wrongPasswordIsRejected() {
        val envelope = BackupCrypto.encrypt(plain, "правильный")
        assertThrows(WrongPasswordException::class.java) { BackupCrypto.decrypt(envelope, "другой") }
    }

    @Test
    fun sameInputGivesDifferentEnvelopes() {
        assertThat(BackupCrypto.encrypt(plain, "p")).isNotEqualTo(BackupCrypto.encrypt(plain, "p"))
    }

    @Test
    fun brokenEnvelopeIsRejected() {
        assertThrows(InvalidBackupException::class.java) {
            BackupCrypto.decrypt("""{"app":"dallet","encrypted":true}""", "p")
        }
    }
}
