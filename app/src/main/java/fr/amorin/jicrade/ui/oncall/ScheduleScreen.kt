package fr.amorin.jicrade.ui.oncall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.ui.theme.personColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val CENTER = 520
private val dayLabelWidth = 36.dp
private val trackHeight = 28.dp
private val dayName: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE")
private val rangeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val rangeYearFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private data class SlotKey(val week: LocalDate, val day: Int, val index: Int)

@Immutable
private class People(val colors: Map<String, Int>, val names: Map<String, String>, val accountId: String) {
    fun name(id: String): String = names[id] ?: id.takeLast(6)
    fun color(id: String): Color = personColors[colors[id] ?: 0]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit, vm: ScheduleViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = CENTER)
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<SlotKey?>(null) }
    val onSelect: (SlotKey) -> Unit = remember { { key -> selected = if (selected == key) null else key } }
    val thisWeek = state.thisWeek
    val topWeek by remember(thisWeek) { derivedStateOf { thisWeek.plusWeeks((listState.firstVisibleItemIndex - CENTER).toLong()) } }
    val people = remember(state.colors, state.names, state.accountId) { People(state.colors, state.names, state.accountId) }
    val today = remember(state.zone) { LocalDate.now(state.zone) }
    val nowMin = remember(state.zone) { minuteOfDay(System.currentTimeMillis(), state.zone) }
    val weeks = state.weeks
    val loading = state.loading
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) selected = null }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = {
                    Column {
                        Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle(topWeek, state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    TextButton(onClick = { scope.launch { listState.animateScrollToItem(CENTER) } }) { Text("Today") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (loading.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            PullToRefreshBox(isRefreshing = false, onRefresh = { vm.refresh(visibleWeeks(listState, thisWeek)) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = 2 * CENTER + 1, key = { it }, contentType = { 0 }) { i ->
                        val week = thisWeek.plusWeeks((i - CENTER).toLong())
                        LaunchedEffect(week) { vm.ensure(week) }
                        WeekCard(
                            week = week,
                            blocks = weeks[week],
                            loading = week in loading,
                            isThisWeek = week == thisWeek,
                            today = today,
                            nowMin = nowMin,
                            people = people,
                            selected = selected?.takeIf { it.week == week },
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}

private fun visibleWeeks(listState: LazyListState, thisWeek: LocalDate): List<LocalDate> =
    listState.layoutInfo.visibleItemsInfo.map { thisWeek.plusWeeks((it.index - CENTER).toLong()) }

private fun weekRange(week: LocalDate): String {
    val end = week.plusDays(6)
    val start = if (week.year == end.year) rangeFormat.format(week) else rangeYearFormat.format(week)
    return "$start – ${rangeYearFormat.format(end)}"
}

private fun subtitle(week: LocalDate, state: ScheduleState): String {
    val tz = state.timezone?.takeIf { it != state.zone.id }
    return if (tz != null) "${weekRange(week)} · schedule in $tz" else weekRange(week)
}

@Composable
fun ColorDot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
private fun WeekCard(
    week: LocalDate,
    blocks: List<ShiftBlock>?,
    loading: Boolean,
    isThisWeek: Boolean,
    today: LocalDate,
    nowMin: Int,
    people: People,
    selected: SlotKey?,
    onSelect: (SlotKey) -> Unit,
) {
    val byDay = remember(blocks) { blocks.orEmpty().groupBy { it.day } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(remember(week) { weekRange(week) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                val tag = when {
                    isThisWeek -> "This week"
                    blocks == null && loading -> "Loading…"
                    blocks == null -> "Not loaded, pull to refresh"
                    else -> null
                }
                if (tag != null) Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Spacer(Modifier.width(dayLabelWidth + 8.dp))
                Ticks(Modifier.weight(1f))
            }
            (0 until 7).forEach { d ->
                val day = week.plusDays(d.toLong())
                val isToday = day == today
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (isToday) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(dayLabelWidth)) {
                        Text(dayName.format(day), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (isToday) Text("today", fontSize = 11.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    DayTrack(
                        week, d, byDay[d].orEmpty(), people,
                        nowMin = if (isToday) nowMin else null,
                        selected = selected,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Ticks(modifier: Modifier) {
    Row(modifier) {
        listOf("00", "06", "12", "18", "24").forEachIndexed { i, label ->
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = when (i) {
                    0 -> TextAlign.Start
                    4 -> TextAlign.End
                    else -> TextAlign.Center
                },
            )
        }
    }
}

private fun Modifier.span(startMin: Int, endMin: Int): Modifier = layout { measurable, constraints ->
    val full = constraints.maxWidth
    val x = (full * startMin.toFloat() / MINUTES_PER_DAY).roundToInt()
    val w = ((full * endMin.toFloat() / MINUTES_PER_DAY).roundToInt() - x).coerceAtLeast(1)
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(full, placeable.height) { placeable.place(x, 0) }
}

@Composable
private fun DayTrack(
    week: LocalDate,
    day: Int,
    blocks: List<ShiftBlock>,
    people: People,
    nowMin: Int?,
    selected: SlotKey?,
    onSelect: (SlotKey) -> Unit,
    modifier: Modifier,
) {
    Box(modifier.height(trackHeight).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        blocks.forEachIndexed { i, b ->
            val key = SlotKey(week, day, i)
            val laneHeight = trackHeight / b.lanes
            Slot(
                b, people,
                isSelected = selected == key,
                onClick = { onSelect(key) },
                modifier = Modifier.offset(y = laneHeight * b.lane).span(b.startMin, b.endMin).height(laneHeight),
            )
        }
        if (nowMin != null) {
            Box(Modifier.span(nowMin, nowMin).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.error))
        }
    }
}

@Composable
private fun Slot(b: ShiftBlock, people: People, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val ids = b.period.responderIds
    val mine = people.accountId in ids
    val color = people.color(ids.first())
    Box(
        modifier
            .background(color)
            .then(if (isSelected) Modifier.background(Color.White.copy(alpha = 0.2f)) else Modifier)
            .then(if (mine) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(people.name(ids.first())) + if (ids.size > 1) "+" else "",
            color = if (color.luminance() > 0.5f) Color.Black else Color.White,
            fontSize = if (b.lanes > 1) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        if (isSelected) SlotPopup(b, people, onDismiss = onClick)
    }
}

@Composable
private fun SlotPopup(b: ShiftBlock, people: People, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val provider = remember(density) { with(density) { BelowOrAbove(gap = 6.dp.roundToPx(), margin = 8.dp.roundToPx()) } }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                b.period.responderIds.forEach { id ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(people.color(id))
                        Text(
                            people.name(id) + if (id == people.accountId) " (you)" else "",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Text(
                    hoursLabel(b) + if (b.period.type != "base") " · ${b.period.type}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
                )
            }
        }
    }
}

private class BelowOrAbove(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin, maxX)
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height > windowSize.height - margin) anchorBounds.top - gap - popupContentSize.height else below
        return IntOffset(x, y)
    }
}
