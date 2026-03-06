package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.domain.model.DownloadTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun buildDateSections(
    tasks: List<DownloadTask>,
    todayLabel: String,
    yesterdayLabel: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
    currentDate: LocalDate = LocalDate.now(zoneId),
    locale: Locale = Locale.getDefault()
): List<DateSection> {
    if (tasks.isEmpty()) return emptyList()

    val yesterday = currentDate.minusDays(1)

    val groupsByTitle = tasks
        .sortedByDescending { it.createdAt }
        .groupBy { it.torrentTitle }

    val taskGroups = groupsByTitle.map { (title, groupTasks) ->
        val latest = groupTasks.maxOfOrNull { it.createdAt } ?: 0L
        val date = Instant.ofEpochMilli(latest).atZone(zoneId).toLocalDate()
        val topicId = parseTopicIdFromTaskId(groupTasks.firstOrNull()?.id)

        TaskGroup(
            title = title,
            tasks = groupTasks.sortedBy { it.id },
            date = date,
            latestTs = latest,
            topicId = topicId
        )
    }

    val groupedByDate = taskGroups.groupBy { it.date }
        .mapValues { (_, groups) -> groups.sortedByDescending { it.latestTs } }

    return groupedByDate.keys.sortedDescending().map { date ->
        val dateLabel = when (date) {
            currentDate -> todayLabel
            yesterday -> yesterdayLabel
            else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
        }

        DateSection(
            date = date,
            groups = groupedByDate[date].orEmpty(),
            dateLabel = dateLabel
        )
    }
}
