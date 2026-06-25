package com.playimdb.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.playimdb.mobile.BuildConfig

private val Background = Color(0xFF0A1020)
private val SurfaceColor = Color(0xFF151823)
private val Accent = Color(0xFFF5C518)
private val TextMuted = Color(0xFF9A9AB8)

private enum class Mode { Search, Charts }

@Composable
fun MobileApp(
    viewModel: MainViewModel = viewModel(),
    onOpenTitle: (TitleResult) -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Background,
            surface = SurfaceColor,
            onSurface = Color.White,
            onBackground = Color.White,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            var mode by remember { mutableStateOf(Mode.Search) }
            val query by viewModel.query.collectAsState()
            val searchState by viewModel.searchState.collectAsState()
            val selectedChart by viewModel.selectedChart.collectAsState()
            val chartState by viewModel.chartState.collectAsState()

            Column(Modifier.fillMaxSize()) {
                Header()
                TabRow(selectedTabIndex = mode.ordinal) {
                    Tab(selected = mode == Mode.Search, onClick = { mode = Mode.Search }, text = { Text("Search") })
                    Tab(selected = mode == Mode.Charts, onClick = { mode = Mode.Charts }, text = { Text("Charts") })
                }

                if (mode == Mode.Search) {
                    SearchContent(query, searchState, viewModel::onQueryChange, onOpenTitle)
                } else {
                    ChartsContent(selectedChart, chartState, viewModel::loadChart, onOpenTitle)
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text("▶", color = Color(0xFFE05C2A), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("Play", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("IMDB", color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("v${BuildConfig.VERSION_NAME}", color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SearchContent(
    query: String,
    state: LoadState,
    onQueryChange: (String) -> Unit,
    onOpenTitle: (TitleResult) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search movies, shows, titles...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        StateContent(state, "Search for a title.", onOpenTitle)
    }
}

@Composable
private fun ChartsContent(
    selectedChart: ChartKind,
    state: LoadState,
    onChartSelected: (ChartKind) -> Unit,
    onOpenTitle: (TitleResult) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(ChartKind.entries) { kind ->
                ChartChip(kind, selectedChart == kind) { onChartSelected(kind) }
            }
        }
        StateContent(state, "Choose a chart.", onOpenTitle)
    }
}

@Composable
private fun ChartChip(kind: ChartKind, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = kind.label,
        color = if (selected) Color.Black else Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Accent else SurfaceColor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun StateContent(
    state: LoadState,
    idleMessage: String,
    onOpenTitle: (TitleResult) -> Unit,
) {
    when (state) {
        LoadState.Idle -> CenterMessage(idleMessage)
        LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        is LoadState.Empty -> CenterMessage(state.message)
        is LoadState.Error -> CenterMessage(state.message, Color(0xFFE05C2A))
        is LoadState.Success -> TitleList(state.results, onOpenTitle)
    }
}

@Composable
private fun CenterMessage(message: String, color: Color = TextMuted) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = color, fontSize = 16.sp)
    }
}

@Composable
private fun TitleList(results: List<TitleResult>, onOpenTitle: (TitleResult) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(results, key = { it.id }) { result ->
            TitleRow(result) { onOpenTitle(result) }
        }
    }
}

@Composable
private fun TitleRow(result: TitleResult, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceColor)
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 80.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Background),
            contentAlignment = Alignment.Center,
        ) {
            if (result.posterUrl != null) {
                AsyncImage(
                    model = result.posterUrl,
                    contentDescription = result.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("🎬", fontSize = 26.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result.year != null) Text(result.year, color = TextMuted, fontSize = 13.sp)
                if (result.type != null) Text(result.type.uppercase(), color = TextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(PlayUrlResolver.displayPath(result.id, result.type), color = Accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
