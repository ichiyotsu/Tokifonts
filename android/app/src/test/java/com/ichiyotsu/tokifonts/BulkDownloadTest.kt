package com.ichiyotsu.tokifonts

import org.junit.Assert.assertEquals
import org.junit.Test

class BulkDownloadTest {
    private fun face(id: String, bytes: Long = 1000L) = FontFace(
        id = id,
        fileName = "$id.ttf",
        exportName = "$id.ttf",
        weight = 400,
        weightName = "Regular",
        weightLabel = "常规",
        italic = false,
        optical = "",
        opticalLabel = "",
        variable = false,
        format = "ttf",
        innerName = "",
        bytes = bytes,
        repo = "",
        ref = "",
        path = "",
        mirrorRepo = "",
        mirrorRef = "",
        mirrorPath = "",
    )

    private fun family(id: String, vararg faces: FontFace) = FontFamily(
        id = id,
        name = id,
        nameEn = "",
        group = "all",
        section = "all",
        summary = "",
        license = "",
        alias = "",
        tags = emptyList(),
        status = "",
        statusDetail = "",
        credit = "",
        sources = emptyList(),
        faces = faces.toList(),
    )

    @Test fun emptyCatalogGivesEmptyPending() {
        assertEquals(emptyList<FontFace>(), pendingDownloadFaces(emptyList(), emptySet()))
    }

    @Test fun nothingReadyGivesAllFaces() {
        val families = listOf(family("a", face("1"), face("2")), family("b", face("3")))
        assertEquals(listOf("1", "2", "3"), pendingDownloadFaces(families, emptySet()).map { it.id })
    }

    @Test fun readyFacesAreExcluded() {
        val families = listOf(family("a", face("1"), face("2")), family("b", face("3")))
        assertEquals(listOf("2"), pendingDownloadFaces(families, setOf("1", "3")).map { it.id })
    }

    @Test fun allReadyGivesEmptyPending() {
        val families = listOf(family("a", face("1"), face("2")))
        assertEquals(emptyList<FontFace>(), pendingDownloadFaces(families, setOf("1", "2")))
    }

    @Test fun unknownReadyIdsAreIgnored() {
        val families = listOf(family("a", face("1")))
        assertEquals(listOf("1"), pendingDownloadFaces(families, setOf("nope")).map { it.id })
    }

    @Test fun pendingBytesSumMatchesFaces() {
        val families = listOf(family("a", face("1", 100L), face("2", 250L)))
        val pending = pendingDownloadFaces(families, setOf("1"))
        assertEquals(250L, pending.sumOf { it.bytes })
    }
}
