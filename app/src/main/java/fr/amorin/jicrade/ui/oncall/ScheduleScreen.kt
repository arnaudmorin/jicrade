package fr.amorin.jicrade.ui.oncall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import fr.amorin.jicrade.ui.common.EmptyState
import fr.amorin.jicrade.ui.common.durationText
import fr.amorin.jicrade.ui.common.formatDateTime
import fr.amorin.jicrade.ui.common.formatTime
import fr.amorin.jicrade.ui.theme.personColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val hourHeight = 40.dp
private val gutterWidth = 36.dp
private val dayHeader: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE")
private val dayNumber: DateTimeFormatter = DateTimeFormatter.ofPattern("d")
private val rangeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit, vm: ScheduleViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<OnCallPeriodEntity?>(null) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.clearError() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = {
                    Column {
                        Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = vm::previous) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week") }
                    TextButton(onClick = vm::today) { Text("Today") }
                    IconButton(onClick = vm::next) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().swipeWeeks(vm::previous, vm::next)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            Legend(state)
            DayHeader(state.weekStart, state.zone)
            HorizontalDivider()
            PullToRefreshBox(isRefreshing = false, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                when {
                    state.periods == null -> EmptyState("Loading…")
                    state.blocks.isEmpty() -> EmptyState("Nobody on call this week", "Pull down to refresh")
                    else -> WeekGrid(state, onSelect = { selected = it })
                }
            }
        }
    }

    selected?.let { p -> ShiftDialog(p, state, onDismiss = { selected = null }) }
}

private fun subtitle(state: ScheduleState): String {
    val end = state.weekStart.plusDays(6)
    val range = "${rangeFormat.format(state.weekStart)} – ${rangeFormat.format(end)} ${end.year}"
    val tz = state.timezone?.takeIf { it != state.zone.id }
    return if (tz != null) "$range · schedule in $tz" else range
}

private fun Modifier.swipeWeeks(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (abs(total) > 120.dp.toPx()) if (total > 0) onPrevious() else onNext()
        },
    ) { _, delta -> total += delta }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(state: ScheduleState) {
    val ids = state.weekIds
    if (ids.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ids.forEach { id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(personColors[state.colorIndex(id)])
                Text(
                    state.name(id),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (id == state.accountId) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
fun ColorDot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
private fun DayHeader(weekStart: LocalDate, zone: ZoneId) {
    val today = LocalDate.now(zone)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Spacer(Modifier.width(gutterWidth))
        (0 until 7).forEach { d ->
            val day = weekStart.plusDays(d.toLong())
            val isToday = day == today
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    dayHeader.format(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dayNumber.format(day),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekGrid(state: ScheduleState, onSelect: (OnCallPeriodEntity) -> Unit) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val now = System.currentTimeMillis()
    val todayIndex = weekStartOf(now, state.zone).let { if (it == state.weekStart) LocalDate.now(state.zone).dayOfWeek.value - 1 else -1 }
    val nowMin = minuteOfDay(now, state.zone)
    val byDay = state.blocks.groupBy { it.day }

    LaunchedEffect(state.weekStart) {
        val hour = if (todayIndex >= 0) (nowMin / 60 - 2).coerceAtLeast(0) else 7
        scroll.scrollTo(with(density) { (hourHeight * hour).roundToPx() })
    }

    Row(
        Modifier.fillMaxSize().verticalScroll(scroll).height(hourHeight * 24)
            .drawBehind {
                val step = hourHeight.toPx()
                for (h in 1 until 24) drawLine(lineColor, Offset(gutterWidth.toPx(), h * step), Offset(size.width, h * step), 1f)
            },
    ) {
        Column(Modifier.width(gutterWidth)) {
            (0 until 24).forEach { h ->
                Box(Modifier.height(hourHeight).fillMaxWidth()) {
                    if (h > 0) {
                        Text(
                            h.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp),
                        )
                    }
                }
            }
        }
        (0 until 7).forEach { d ->
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxHeight().drawBehind {
                    drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), 1f)
                },
            ) {
                byDay[d].orEmpty().forEach { b ->
                    val laneWidth = maxWidth / b.lanes
                    ShiftBlockView(
                        b,
                        state,
                        Modifier
                            .offset(x = laneWidth * b.lane, y = hourHeight * (b.startMin / 60f))
                            .width(laneWidth)
                            .height(hourHeight * ((b.endMin - b.startMin) / 60f))
                            .padding(1.dp)
                            .clickable { onSelect(b.period) },
                    )
                }
                if (d == todayIndex) {
                    Box(
                        Modifier.offset(y = hourHeight * (nowMin / 60f)).fillMaxWidth().height(2.dp)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShiftBlockView(b: ShiftBlock, state: ScheduleState, modifier: Modifier) {
    val ids = b.period.responderIds
    val color = personColors[state.colorIndex(ids.first())]
    val mine = state.accountId in ids
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier
            .clip(shape)
            .background(color)
            .then(if (mine) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), shape) else Modifier)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    ) {
        val small = 10.sp
        Text(
            ids.joinToString { state.name(it).substringBefore(' ') },
            color = Color.White,
            fontSize = small,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (b.endMin - b.startMin >= 60) {
            Text(formatTime(b.period.start), color = Color.White, fontSize = small, lineHeight = 11.sp, maxLines = 1)
            Text(formatTime(b.period.end), color = Color.White, fontSize = small, lineHeight = 11.sp, maxLines = 1)
        }
        if (b.period.type != "base" && b.endMin - b.startMin >= 90) {
            Text(b.period.type, color = Color.White, fontSize = small, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShiftDialog(p: OnCallPeriodEntity, state: ScheduleState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(p.responderIds.joinToString { state.name(it) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.responderIds.forEach { id ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(personColors[state.colorIndex(id)], 12)
                        Text(state.name(id), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text("From ${formatDateTime(p.start)}")
                Text("To ${formatDateTime(p.end)} (${durationText(p.start, p.end)})")
                if (p.rotationName.isNotBlank()) Text("Rotation: ${p.rotationName}")
                if (p.type != "base") Text("Type: ${p.type}")
            }
        },
    )
}
