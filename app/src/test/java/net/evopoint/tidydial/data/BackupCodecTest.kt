package net.evopoint.tidydial.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.OffsetDateTime

// Robolectric only for a working org.json.
@RunWith(RobolectricTestRunner::class)
class BackupCodecTest {

    private val createdAt = OffsetDateTime.parse("2026-10-31T09:04:12+01:00")

    private val avery = RowIdentity(
        dataId = 101,
        rawContactId = 10,
        lookupKey = "0r10-2A4C",
        accountType = "com.google",
        accountName = "avery@example.com",
        sourceId = "c5550101",
        displayName = "Avery Sample",
    )

    /** A device contact: no account, no server id. */
    private val frankie = RowIdentity(dataId = 601, rawContactId = 60)

    private val changes = BackupFile.Changes(
        createdAt = createdAt,
        appVersion = "1.2.0",
        countryIso = "US",
        entries = listOf(
            ChangeEntry(avery, before = "2125550101", after = "+1 212-555-0101"),
            ChangeEntry(frankie, before = "6175550199", after = "+1 617-555-0199"),
        ),
    )

    private val snapshot = BackupFile.Snapshot(
        createdAt = createdAt,
        appVersion = "1.2.0",
        countryIso = null,
        entries = listOf(SnapshotEntry(avery, "2125550101"), SnapshotEntry(frankie, "6175550199")),
    )

    private fun assertRejected(error: BackupReadError, text: String) {
        try {
            BackupCodec.decode(text)
            fail("Accepted: $text")
        } catch (e: BackupFormatException) {
            assertEquals(text, error, e.error)
        }
    }

    /** A valid changes backup with [edit] applied to its JSON. */
    private fun changesWith(edit: JSONObject.() -> Unit): String =
        JSONObject(BackupCodec.encode(changes)).apply(edit).toString()

    private fun firstEntryWith(edit: JSONObject.() -> Unit): String = changesWith {
        getJSONArray("entries").getJSONObject(0).edit()
    }

    @Test
    fun changesRoundTrip() {
        assertEquals(changes, BackupCodec.decode(BackupCodec.encode(changes)))
    }

    @Test
    fun snapshotRoundTrip() {
        val decoded = BackupCodec.decode(BackupCodec.encode(snapshot))
        assertEquals(snapshot, decoded)
        assertEquals(BackupKind.SNAPSHOT, decoded.kind)
        assertEquals(2, decoded.size)
    }

    @Test
    fun fileIsReadableAndVersioned() {
        val json = JSONObject(BackupCodec.encode(changes))
        assertEquals("TidyDial", json.getString("app"))
        assertEquals(1, json.getInt("format"))
        assertEquals("changes", json.getString("kind"))
        assertEquals("2026-10-31T09:04:12+01:00", json.getString("createdAt"))
        assertEquals("1.2.0", json.getString("appVersion"))
        val device = json.getJSONArray("entries").getJSONObject(1)
        // Missing identity is written as null, not left out; device contacts have no account type.
        assertTrue(device.isNull("lookupKey"))
        assertTrue(device.isNull("sourceId"))
        assertEquals("", device.getString("accountType"))
        assertEquals("+1 617-555-0199", device.getString("after"))
        // Indented, so a tester can read it.
        assertTrue(BackupCodec.encode(changes).contains("\n  \"entries\""))
    }

    @Test
    fun snapshotEntriesHaveANumberInsteadOfBeforeAndAfter() {
        val entry = JSONObject(BackupCodec.encode(snapshot)).getJSONArray("entries").getJSONObject(0)
        assertEquals("2125550101", entry.getString("number"))
        assertTrue(!entry.has("before") && !entry.has("after"))
        assertNull(BackupCodec.decode(BackupCodec.encode(snapshot)).countryIso)
    }

    @Test
    fun offsetIsKept() {
        assertEquals(createdAt, BackupCodec.decode(BackupCodec.encode(changes)).createdAt)
        assertEquals(1, BackupCodec.decode(BackupCodec.encode(changes)).createdAt.offset.totalSeconds / 3600)
    }

    @Test
    fun byteOrderMarkIsIgnored() {
        assertEquals(changes, BackupCodec.decode("\uFEFF" + BackupCodec.encode(changes)))
    }

    @Test
    fun otherAppsFilesAreNotBackups() {
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { put("app", "SomethingElse") })
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { remove("app") })
    }

    @Test
    fun newerFormatIsRecognized() {
        assertRejected(BackupReadError.NEWER_FORMAT, changesWith { put("format", 2) })
        // A newer format may have new kinds; the version check comes first.
        assertRejected(BackupReadError.NEWER_FORMAT, changesWith { put("format", 2).put("kind", "contacts") })
    }

    @Test
    fun missingOrInvalidFormatIsRejected() {
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { put("format", 0) })
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { remove("format") })
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { put("format", "1") })
    }

    @Test
    fun unknownKindIsRejected() {
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { put("kind", "contacts") })
    }

    @Test
    fun malformedFilesAreRejected() {
        assertRejected(BackupReadError.NOT_A_BACKUP, "")
        assertRejected(BackupReadError.NOT_A_BACKUP, "not json at all")
        assertRejected(BackupReadError.NOT_A_BACKUP, "{\"app\":\"TidyDial\",")
        assertRejected(BackupReadError.NOT_A_BACKUP, "[]")
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { put("createdAt", "yesterday") })
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { remove("entries") })
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { put("entries", "none") })
    }

    @Test
    fun deeplyNestedFilesAreRejectedWithoutParsing() {
        // org.json parses recursively: this would overflow the stack instead of failing.
        assertRejected(BackupReadError.NOT_A_BACKUP, "[".repeat(200_000))
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { put("extra", nested(17)) })
        // Brackets inside strings do not count, in either quote style.
        val names = "[".repeat(40)
        val quoted = changesWith { getJSONArray("entries").getJSONObject(0).put("displayName", names) }
        assertEquals(names, (BackupCodec.decode(quoted) as BackupFile.Changes).entries[0].row.displayName)
        val singleQuoted = changesWith {}.replaceFirst("{", "{\"note\":'$names',")
        assertTrue(BackupCodec.decode(singleQuoted) is BackupFile.Changes)
        assertTrue(BackupCodec.decode(changesWith { put("extra", nested(12)) }) is BackupFile.Changes)
    }

    /** An array nested [depth] levels deep. */
    private fun nested(depth: Int): JSONArray = (1 until depth).fold(JSONArray()) { inner, _ -> JSONArray().put(inner) }

    @Test
    fun invalidEntriesRejectTheWholeFile() {
        assertRejected(BackupReadError.NOT_A_BACKUP, firstEntryWith { remove("dataId") })
        assertRejected(BackupReadError.NOT_A_BACKUP, firstEntryWith { put("dataId", "101") })
        assertRejected(BackupReadError.NOT_A_BACKUP, firstEntryWith { remove("rawContactId") })
        assertRejected(BackupReadError.NOT_A_BACKUP, firstEntryWith { put("after", " ") })
        assertRejected(BackupReadError.NOT_A_BACKUP, firstEntryWith { remove("before") })
        // A change that changes nothing.
        assertRejected(BackupReadError.NOT_A_BACKUP, firstEntryWith { put("after", "2125550101") })
        // The entries must match the kind.
        assertRejected(BackupReadError.NOT_A_BACKUP, firstEntryWith { put("number", "2125550101") })
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { put("kind", "snapshot") })
        assertRejected(BackupReadError.NOT_A_BACKUP, changesWith { getJSONArray("entries").put(7) })
    }

    @Test
    fun duplicateRowsKeepTheFirstEntry() {
        val text = changesWith {
            getJSONArray("entries").put(
                JSONObject().put("dataId", 101).put("rawContactId", 10).put("before", "1").put("after", "2"),
            )
        }
        val decoded = BackupCodec.decode(text) as BackupFile.Changes
        assertEquals(changes.entries, decoded.entries)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = changesWith {
            put("comment", "written by a future version")
            getJSONArray("entries").getJSONObject(0).put("label", "Mobile")
        }
        assertEquals(changes, BackupCodec.decode(text))
    }

    @Test
    fun missingOptionalFieldsAreAllowed() {
        val text = """
            {"app":"TidyDial","format":1,"kind":"snapshot","createdAt":"2026-10-31T09:04:12Z",
             "entries":[{"dataId":101,"rawContactId":10,"number":"2125550101"}]}
        """.trimIndent()
        val decoded = BackupCodec.decode(text) as BackupFile.Snapshot
        assertEquals(listOf(SnapshotEntry(RowIdentity(101, 10), "2125550101")), decoded.entries)
        assertEquals("", decoded.appVersion)
        assertNull(decoded.countryIso)
    }

    @Test
    fun fileNamesUseLocalTimeWithSeconds() {
        val time = LocalDateTime.parse("2026-10-31T09:04:12")
        assertEquals("TidyDial backup 2026-10-31 090412.json", BackupNames.changes(time))
        assertEquals("TidyDial snapshot 2026-10-31 090412.json", BackupNames.snapshot(time))
        assertEquals(
            "TidyDial backup 2026-10-31 090412 (2).json",
            BackupNames.withSuffix(BackupNames.changes(time), 2),
        )
    }
}
