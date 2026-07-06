package com.snuggle.music.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.snuggle.music.R
import com.snuggle.music.viewmodels.StatsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WrappedScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsState(initial = emptyList())
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsState(initial = emptyList())
    val totalPlayTime by viewModel.totalPlayTime.collectAsState(initial = 0L)

    val pagerState = rememberPagerState(pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()

    // Auto-advance logic
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage < 4) {
            delay(5000)
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2A0845),
                        Color(0xFF6441A5)
                    )
                )
            )
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> IntroSlide()
                1 -> TopSongsSlide(mostPlayedSongs)
                2 -> TopArtistsSlide(mostPlayedArtists)
                3 -> ListeningTimeSlide(totalPlayTime)
                4 -> SummarySlide(mostPlayedSongs, mostPlayedArtists, totalPlayTime, onClose = { navController.navigateUp() })
            }
        }

        // Tap navigation areas
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (pagerState.currentPage < 4) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
            )
        }

        // Progress indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0 until 5) {
                val progress by animateFloatAsState(
                    targetValue = if (i < pagerState.currentPage) 1f else if (i == pagerState.currentPage) 1f else 0f,
                    animationSpec = tween(if (i == pagerState.currentPage) 5000 else 300)
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }
        
        // Close button
        IconButton(
            onClick = { navController.navigateUp() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}

@Composable
fun IntroSlide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Ready to see your listening habits?",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 48.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TopSongsSlide(songs: List<com.snuggle.music.db.entities.Song>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your Top Songs",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 32.dp)
        )
        songs.take(5).forEachIndexed { index, song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${index + 1}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.width(40.dp)
                )
                Column {
                    Text(
                        text = song.song.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Text(
                        text = song.artists.joinToString { it.name }.ifEmpty { "Unknown Artist" },
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun TopArtistsSlide(artists: List<com.snuggle.music.db.entities.Artist>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your Top Artists",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 32.dp)
        )
        artists.take(5).forEachIndexed { index, artist ->
            Text(
                text = "${index + 1}. ${artist.artist.name}",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
fun ListeningTimeSlide(timeMs: Long) {
    val minutes = timeMs / 60000
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "You've spent",
            style = MaterialTheme.typography.headlineMedium.copy(color = Color.White)
        )
        Text(
            text = "$minutes",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 80.sp
            ),
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Text(
            text = "minutes listening to music.",
            style = MaterialTheme.typography.headlineMedium.copy(color = Color.White),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SummarySlide(
    songs: List<com.snuggle.music.db.entities.Song>,
    artists: List<com.snuggle.music.db.entities.Artist>,
    timeMs: Long,
    onClose: () -> Unit
) {
    val minutes = timeMs / 60000
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your Year in Review",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Top Artist", color = Color.White.copy(alpha = 0.7f))
                Text(artists.firstOrNull()?.artist?.name ?: "None", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Top Song", color = Color.White.copy(alpha = 0.7f))
                Text(songs.firstOrNull()?.song?.title ?: "None", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Minutes Listened", color = Color.White.copy(alpha = 0.7f))
                Text("$minutes", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("Done")
        }
    }
}
