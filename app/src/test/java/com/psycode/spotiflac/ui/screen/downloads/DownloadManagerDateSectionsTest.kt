package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class DownloadManagerDateSectionsTest {

    @Test
    fun `groups tasks by torrent title and sorts sections by date descending`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 2, 8)

        val tasks = listOf(
            task("101_a", "A-1", "Alpha", zdt(2026, 2, 8, 9, 0, zone)),
            task("101_b", "A-2", "Alpha", zdt(2026, 2, 8, 10, 0, zone)),
            task("202_a", "B-1", "Beta", zdt(2026, 2, 7, 11, 0, zone)),
            task("303_a", "C-1", "Gamma", zdt(2026, 2, 6, 12, 0, zone))
        )

        val sections = buildDateSections(
            tasks = tasks,
            todayLabel = "Today",
            yesterdayLabel = "Yesterday",
            zoneId = zone,
            currentDate = today,
            locale = Locale.US
        )

        assertEquals(3, sections.size)
        assertEquals("Today", sections[0].dateLabel)
        assertEquals("Yesterday", sections[1].dateLabel)
        assertEquals("6 February 2026", sections[2].dateLabel)
        assertEquals(listOf("Alpha"), sections[0].groups.map { it.title })
        assertEquals(listOf("Beta"), sections[1].groups.map { it.title })
        assertEquals(listOf("Gamma"), sections[2].groups.map { it.title })
    }

    @Test
    fun `extracts topicId from task id and keeps tasks sorted by id inside group`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 2, 8)

        val tasks = listOf(
            task("400_z", "Z", "Pack", zdt(2026, 2, 8, 10, 0, zone)),
            task("400_a", "A", "Pack", zdt(2026, 2, 8, 9, 0, zone))
        )

        val sections = buildDateSections(
            tasks = tasks,
            todayLabel = "Today",
            yesterdayLabel = "Yesterday",
            zoneId = zone,
            currentDate = today,
            locale = Locale.US
        )

        val group = sections.single().groups.single()
        assertEquals(400, group.topicId)
        assertEquals(listOf("400_a", "400_z"), group.tasks.map { it.id })
    }

    private fun task(id: String, fileName: String, title: String, createdAt: Long) = DownloadTask(
        id = id,
        fileName = fileName,
        size = 1L,
        progress = 0,
        status = DownloadStatus.QUEUED,
        errorMessage = null,
        contentUri = null,
        torrentTitle = title,
        speedBytesPerSec = 0L,
        createdAt = createdAt
    )

    private fun zdt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: ZoneId
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}
