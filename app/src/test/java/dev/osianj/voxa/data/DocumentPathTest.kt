package dev.osianj.voxa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentPathTest {
    private val base = "content://com.android.externalstorage.documents/tree/primary%3APodcasts/document/"

    @Test fun mapsAPickerUriToVolumeAndPath() = assertEquals(
        "primary" to "Podcasts/RSK XFM/2012-08-21 - Learn English with Ricky Gervais.mp3",
        documentPathOf(base + "primary%3APodcasts%2FRSK%20XFM%2F2012-08-21%20-%20Learn%20English%20with%20Ricky%20Gervais.mp3"),
    )
    @Test fun readsAnSdCardVolume() = assertEquals("1234-ABCD" to "Pods/a.mp3", documentPathOf("content://com.android.externalstorage.documents/tree/1234-ABCD%3APods/document/1234-ABCD%3APods%2Fa.mp3"))
    @Test fun keepsAPlusAndUnicodeInFileNames() = assertEquals(
        "primary" to "Podcasts/S/C++ & café.mp3",
        documentPathOf(base + "primary%3APodcasts%2FS%2FC%2B%2B%20%26%20caf%C3%A9.mp3"),
    )
    @Test fun refusesAppStoragePaths() = assertNull(documentPathOf("/data/user/0/dev.osianj.voxa/files/episodes/12.audio"))
    @Test fun refusesOtherProviders() = assertNull(documentPathOf("content://com.example.provider/tree/x/document/y"))
    @Test fun refusesPathsThatClimbOut() = assertNull(documentPathOf(base + "primary%3APodcasts%2F..%2F..%2Fdata%2Fx.mp3"))
    @Test fun refusesAUriWithNoDocument() = assertNull(documentPathOf("content://com.android.externalstorage.documents/tree/primary%3APodcasts"))
}
