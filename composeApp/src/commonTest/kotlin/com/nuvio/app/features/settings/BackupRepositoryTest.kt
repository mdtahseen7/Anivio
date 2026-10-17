package com.nuvio.app.features.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupRepositoryTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun validatesCurrentBackupVersion() {
        val payload = json.encodeToString(NuvioBackupPayload(version = 1))

        assertTrue(BackupRepository.validateJson(payload).valid)
    }

    @Test
    fun rejectsUnknownBackupVersion() {
        val payload = json.encodeToString(NuvioBackupPayload(version = 99))

        assertFalse(BackupRepository.validateJson(payload).valid)
    }

    @Test
    fun rejectsMalformedJson() {
        assertFalse(BackupRepository.validateJson("not-json").valid)
    }
}
