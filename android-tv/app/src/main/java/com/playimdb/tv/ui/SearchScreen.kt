package com.playimdb.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import coil.compose.AsyncImage
import com.playimdb.tv.R
import com.playimdb.tv.SearchUiState
import com.playimdb.tv.SearchViewModel
import com.playimdb.tv.model.TitleResult
import com.playimdb.tv.ui.theme.Accent
import com.playimdb.tv.ui.theme.AccentOrange
import com.playimdb.tv.ui.theme.Background
import com.playimdb.tv.ui.theme.Border
import com.playimdb.tv.ui.theme.Surface
import com.playimdb.tv.ui.theme.SurfaceFocused
import com.playimdb.tv.ui.theme.TextMuted
import com.playimdb.tv.ui.theme.TextPrimary
import com.playimdb.tv.ui.theme.TextSecondary

private val TYPE_LABELS = mapOf(
    "feature" to "MOVIE",
    "short" to "SHORT",
    "tv" to "TV SERIES",
    "tvSeries" to "TV SERIES",
    "tvMiniSeries" to "MINI-SERIES",
    "tvMovie" to "TV MOVIE",
    "tvSpecial" to "TV SPECIAL",
    "videoGame" to "GAME",
    "video" to "VIDEO",
    "podcastSeries" to "PODCAST",
    "podcastEpisode" to "PODCAST EP",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onResultSelected: (String) -> Unit,
) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()
    val searchFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchFocus.requestFocus()
    }

    BackHandler(enabled = query.isNotEmpty()) {
        viewModel.onQueryChange("")
        searchFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 56.dp, vertical = 36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "▶",
                color = AccentOrange,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Play",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "IMDB",
                color = Accent,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = {
                Text(
                    text = "Search movies, shows, titles…",
                    color = TextMuted,
                    fontSize = 22.sp,
                )
            },
            textStyle = TextStyle(color = TextPrimary, fontSize = 22.sp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocus)
                .focusProperties { down = firstItemFocus },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                cursorColor = Accent,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
            ),
            shape = RoundedCornerShape(8.dp),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Press OK to open the keyboard — use its mic button for voice input",
            color = TextMuted,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            SearchUiState.Idle -> CenteredMessage("Type to search IMDB titles.")
            SearchUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            is SearchUiState.Empty -> CenteredMessage("No titles found for \"${s.query}\".")
            is SearchUiState.Error -> CenteredMessage(s.message, isError = true)
            is SearchUiState.Success -> ResultsList(
                results = s.results,
                firstItemFocus = firstItemFocus,
                onSelected = onResultSelected,
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = if (isError) AccentOrange else TextMuted,
            fontSize = 24.sp,
        )
    }
}

@Composable
private fun ResultsList(
    results: List<TitleResult>,
    firstItemFocus: FocusRequester,
    onSelected: (String) -> Unit,
) {
    TvLazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        pivotOffsets = PivotOffsets(parentFraction = 0.3f),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items = results, key = { _, r -> r.id }) { index, result ->
            val rowMod = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
            ResultRow(
                result = result,
                modifier = rowMod,
                onSelected = { onSelected(result.id) },
            )
        }
    }
}

@Composable
private fun ResultRow(
    result: TitleResult,
    modifier: Modifier = Modifier,
    onSelected: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    val borderWidth by animateDpAsState(
        targetValue = if (focused) 4.dp else 1.dp,
        animationSpec = tween(120),
        label = "border-width",
    )
    val rowScale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        animationSpec = tween(140),
        label = "row-scale",
    )
    val borderColor = if (focused) Accent else Border
    val bgColor = if (focused) SurfaceFocused else Surface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .scale(rowScale)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (
                        event.key == Key.DirectionCenter ||
                            event.key == Key.Enter ||
                            event.key == Key.NumPadEnter
                        )
                ) {
                    onSelected()
                    true
                } else {
                    false
                }
            }
            .clickable { onSelected() }
            .padding(18.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 70.dp, height = 104.dp)
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
                Text("🎬", fontSize = 32.sp)
            }
        }

        Spacer(Modifier.width(22.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = result.title,
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (result.year != null) {
                    Text(
                        text = result.year,
                        color = TextSecondary,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.width(14.dp))
                }
                val typeLabel = result.type?.let { TYPE_LABELS[it] ?: it.uppercase() }
                if (typeLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (focused) Accent.copy(alpha = 0.35f) else Border)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = typeLabel,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "playimdb.com/title/${result.id}",
                color = Accent,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
