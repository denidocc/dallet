package com.dallet.app.security

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PinLockTest {

    private class MemoryStorage : SecurityStorage {
        val strings = mutableMapOf<String, String>()
        val booleans = mutableMapOf<String, Boolean>()

        override fun getString(key: String): String? = strings[key]

        override fun putString(key: String, value: String?) {
            if (value == null) strings.remove(key) else strings[key] = value
        }

        override fun getBoolean(key: String): Boolean = booleans[key] ?: false

        override fun putBoolean(key: String, value: Boolean) {
            booleans[key] = value
        }
    }

    @Test
    fun byDefault_lockIsDisabled() {
        val lock = PinLock(MemoryStorage())

        assertThat(lock.isEnabled).isFalse()
        assertThat(lock.biometricEnabled).isFalse()
    }

    @Test
    fun afterSettingPin_correctPinVerifies() {
        val lock = PinLock(MemoryStorage())

        lock.setPin("1234")

        assertThat(lock.isEnabled).isTrue()
        assertThat(lock.verify("1234")).isTrue()
    }

    @Test
    fun wrongPin_isRejected() {
        val lock = PinLock(MemoryStorage())
        lock.setPin("1234")

        assertThat(lock.verify("4321")).isFalse()
        assertThat(lock.verify("123")).isFalse()
        assertThat(lock.verify("")).isFalse()
    }

    @Test
    fun pinIsNotStoredInPlainText() {
        val storage = MemoryStorage()
        PinLock(storage).setPin("1234")

        assertThat(storage.strings.values.any { it.contains("1234") }).isFalse()
    }

    @Test
    fun invalidPins_areRejectedOnSet() {
        val lock = PinLock(MemoryStorage())

        assertThrows(IllegalArgumentException::class.java) { lock.setPin("123") }
        assertThrows(IllegalArgumentException::class.java) { lock.setPin("12345") }
        assertThrows(IllegalArgumentException::class.java) { lock.setPin("12a4") }
        assertThat(lock.isEnabled).isFalse()
    }

    @Test
    fun settingPinAgain_replacesOldPin() {
        val lock = PinLock(MemoryStorage())
        lock.setPin("1111")

        lock.setPin("2222")

        assertThat(lock.verify("1111")).isFalse()
        assertThat(lock.verify("2222")).isTrue()
    }

    @Test
    fun disable_removesPinAndBiometric() {
        val lock = PinLock(MemoryStorage())
        lock.setPin("1234")
        lock.setBiometricEnabled(true)

        lock.disable()

        assertThat(lock.isEnabled).isFalse()
        assertThat(lock.biometricEnabled).isFalse()
        assertThat(lock.verify("1234")).isFalse()
    }

    @Test
    fun biometric_canOnlyBeEnabledWithPin() {
        val lock = PinLock(MemoryStorage())

        lock.setBiometricEnabled(true)

        assertThat(lock.biometricEnabled).isFalse()
    }

    @Test
    fun biometric_canBeToggledWhenPinIsSet() {
        val lock = PinLock(MemoryStorage())
        lock.setPin("1234")

        lock.setBiometricEnabled(true)
        assertThat(lock.biometricEnabled).isTrue()

        lock.setBiometricEnabled(false)
        assertThat(lock.biometricEnabled).isFalse()
    }
}
