package com.atyafcode.sirat.features.behavior.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.utils.behaviorRepository
import com.atyafcode.sirat.data.repository.BehaviorLog
import com.atyafcode.sirat.features.behavior.domain.BehaviorExportManager
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

private enum class AnalysisPeriod {
    WEEKLY, MONTHLY, YEARLY
}

data class ChartDataPoint(
    val label: String,
    val value: Int,
    val isHighlighted: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BehaviorScreen() {
    val context = LocalContext.current
    val repository = remember { context.behaviorRepository() }
    val exportManager = remember { BehaviorExportManager(context, repository) }
    
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedPeriod by remember { mutableStateOf(AnalysisPeriod.WEEKLY) }
    
    val today = LocalDate.now()
    
    var showBottomSheet by remember { mutableStateOf(false) }
    var behaviorCount by remember { mutableIntStateOf(1) }
    var behaviorReason by remember { mutableStateOf("") }
    
    // UI Update triggers
    var refreshKey by remember { mutableIntStateOf(0) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val success = exportManager.exportToCsv(uri)
            Toast.makeText(
                context,
                if (success) context.getString(R.string.plan_export_pdf_success) else context.getString(R.string.plan_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = exportManager.importFromCsv(uri)
            if (result.isSuccess) {
                refreshKey++
                Toast.makeText(context, context.getString(R.string.behavior_import_success, result.getOrNull() ?: 0), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.behavior_import_failed, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val currentLog = remember(selectedDate, refreshKey) { 
        repository.getLog(selectedDate) 
    }

    val chartDataPoints = remember(selectedPeriod, refreshKey, selectedMonth) {
        when (selectedPeriod) {
            AnalysisPeriod.WEEKLY -> {
                val end = LocalDate.now()
                val start = end.minusDays(6)
                val logs = repository.getLogsForRange(start, end)
                (0..6).map { i ->
                    val date = start.plusDays(i.toLong())
                    val log = logs.find { it.date == date }
                    ChartDataPoint(
                        label = date.dayOfMonth.toString(),
                        value = log?.count ?: 0,
                        isHighlighted = date == today
                    )
                }
            }
            AnalysisPeriod.MONTHLY -> {
                val start = selectedMonth.atDay(1)
                val end = selectedMonth.atEndOfMonth()
                val logs = repository.getLogsForRange(start, end)
                (1..selectedMonth.lengthOfMonth()).map { day ->
                    val date = selectedMonth.atDay(day)
                    val log = logs.find { it.date == date }
                    ChartDataPoint(
                        label = day.toString(),
                        value = log?.count ?: 0,
                        isHighlighted = date == today
                    )
                }
            }
            AnalysisPeriod.YEARLY -> {
                val currentYear = selectedMonth.year
                (1..12).map { month ->
                    val yearMonth = YearMonth.of(currentYear, month)
                    val logs = repository.getLogsForRange(yearMonth.atDay(1), yearMonth.atEndOfMonth())
                    val totalCount = logs.sumOf { it.count }
                    ChartDataPoint(
                        label = yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        value = totalCount,
                        isHighlighted = yearMonth == YearMonth.now()
                    )
                }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values")) },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.behavior_import), fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { exportLauncher.launch("sirat_behavior_logs.csv") },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.behavior_export), fontSize = 12.sp)
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            val periods = listOf(
                AnalysisPeriod.WEEKLY to stringResource(R.string.behavior_period_weekly),
                AnalysisPeriod.MONTHLY to stringResource(R.string.behavior_period_monthly),
                AnalysisPeriod.YEARLY to stringResource(R.string.behavior_period_yearly)
            )
            periods.forEachIndexed { index, (period, label) ->
                SegmentedButton(
                    selected = selectedPeriod == period,
                    onClick = { selectedPeriod = period },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                    label = { Text(label) }
                )
            }
        }

        Text(
            text = when(selectedPeriod) {
                AnalysisPeriod.WEEKLY -> stringResource(R.string.behavior_stats_weekly)
                AnalysisPeriod.MONTHLY -> stringResource(R.string.behavior_stats_monthly)
                AnalysisPeriod.YEARLY -> stringResource(R.string.behavior_stats_yearly)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        BehaviorChart(dataPoints = chartDataPoints)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        CalendarHeader(
            selectedMonth = selectedMonth,
            onMonthChange = { selectedMonth = it }
        )
        Spacer(modifier = Modifier.height(16.dp))
        CalendarGrid(
            selectedMonth = selectedMonth,
            selectedDate = selectedDate,
            today = today,
            onDateSelected = { selectedDate = it },
            onDateLongClick = { 
                selectedDate = it
                val existing = repository.getLog(it)
                behaviorCount = existing?.count ?: 1
                behaviorReason = existing?.reason ?: ""
                showBottomSheet = true 
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        BehaviorDetailsCard(selectedDate, currentLog)
        
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            BehaviorLogInputSheet(
                selectedDate = selectedDate,
                count = behaviorCount,
                reason = behaviorReason,
                onCountChange = { behaviorCount = it },
                onReasonChange = { behaviorReason = it },
                onSave = {
                    val log = BehaviorLog(selectedDate, behaviorCount, behaviorReason)
                    repository.saveLog(log)
                    refreshKey++
                    showBottomSheet = false
                },
                onCancel = { showBottomSheet = false }
            )
        }
    }
}

@Composable
private fun BehaviorChart(dataPoints: List<ChartDataPoint>) {
    val maxCount = (dataPoints.maxOfOrNull { it.value } ?: 5).coerceAtLeast(5)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 16.dp)
        ) {
            // Check if we need horizontal scroll for many data points (like Monthly)
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                dataPoints.forEach { point ->
                    val barHeightFraction = point.value.toFloat() / maxCount
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.width(if (dataPoints.size > 12) 24.dp else 40.dp)
                    ) {
                        if (point.value > 0) {
                            Text(
                                text = point.value.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        
                        val animatedHeight by animateFloatAsState(
                            targetValue = barHeightFraction,
                            animationSpec = tween(durationMillis = 1000),
                            label = "BarHeight"
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(animatedHeight.coerceAtLeast(0.02f))
                                .width(if (dataPoints.size > 12) 8.dp else 12.dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            if (point.isHighlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                            (if (point.isHighlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary).copy(alpha = 0.5f)
                                        )
                                    )
                                )
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (point.isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            fontWeight = if (point.isHighlighted) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
        }
        
        Text(
            text = "${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${selectedMonth.year}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarGrid(
    selectedMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDateLongClick: (LocalDate) -> Unit
) {
    val daysInMonth = selectedMonth.lengthOfMonth()
    val firstDayOfMonth = selectedMonth.atDay(1).dayOfWeek.value % 7
    val days = (1..daysInMonth).toList()
    val emptyDaysBefore = (0 until firstDayOfMonth).toList()

    val dayNames = listOf(
        stringResource(R.string.behavior_day_sun),
        stringResource(R.string.behavior_day_mon),
        stringResource(R.string.behavior_day_tue),
        stringResource(R.string.behavior_day_wed),
        stringResource(R.string.behavior_day_thu),
        stringResource(R.string.behavior_day_fri),
        stringResource(R.string.behavior_day_sat)
    )

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            dayNames.forEach { dayName ->
                Text(
                    text = dayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val allDays = emptyDaysBefore.map { null } + days
        val chunks = allDays.chunked(7)
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chunks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                val date = selectedMonth.atDay(day)
                                val isToday = date == today
                                val isSelected = date == selectedDate
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .combinedClickable(
                                            onClick = { onDateSelected(date) },
                                            onLongClick = { onDateLongClick(date) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.toString(),
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                            isToday -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    if (week.size < 7) {
                        repeat(7 - week.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BehaviorDetailsCard(date: LocalDate, log: BehaviorLog?) {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.behavior_history_title, date.format(dateFormatter)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (log != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.behavior_count) + ": ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = log.count.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (log.reason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = log.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.no_behavior_recorded),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BehaviorLogInputSheet(
    selectedDate: LocalDate,
    count: Int,
    reason: String,
    onCountChange: (Int) -> Unit,
    onReasonChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.add_behavior_log),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.behavior_count),
            style = MaterialTheme.typography.titleMedium
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            IconButton(
                onClick = { if (count > 1) onCountChange(count - 1) },
                colors = IconButtonDefaults.filledIconButtonColors()
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
            }
            
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 32.dp),
                fontWeight = FontWeight.Bold
            )
            
            IconButton(
                onClick = { onCountChange(count + 1) },
                colors = IconButtonDefaults.filledIconButtonColors()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
        
        OutlinedTextField(
            value = reason,
            onValueChange = onReasonChange,
            label = { Text(stringResource(R.string.behavior_reason)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.save_behavior))
        }
        
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.cancel_button))
        }
    }
}
