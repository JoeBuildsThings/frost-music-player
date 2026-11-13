package com.joebuildsthings.frost.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.joebuildsthings.frost.FrostViewModel
import com.joebuildsthings.frost.data.Track
import com.joebuildsthings.frost.ui.theme.Accent
import com.joebuildsthings.frost.ui.theme.AccentTint
import com.joebuildsthings.frost.ui.theme.GlassShadow
import com.joebuildsthings.frost.ui.theme.Ink
import com.joebuildsthings.frost.ui.theme.InkMuted
import com.joebuildsthings.frost.ui.theme.PanelScrim
import com.joebuildsthings.frost.ui.theme.panelHazeStyle
import com.joebuildsthings.frost.ui.theme.dynamicGlow
import com.joebuildsthings.frost.ui.theme.glassEdge
import com.joebuildsthings.frost.ui.theme.glassFlat
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(vm: FrostViewModel, glowColors: List<Color>?, onBack: () -> Unit) {
    val track = vm.current
    val context = LocalContext.current
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    val hazeState = rememberHazeState()
    val panelShape = RoundedCornerShape(32.dp)
    val artScale by animateFloatAsState(
        targetValue = if (vm.playWhenReady) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "art"
    )

    LaunchedEffect(Unit) {
        while (true) {
            if (!scrubbing) position = vm.positionMs().coerceAtLeast(0L)
            delay(if (vm.isPlaying) 300L else 900L)
        }
    }

    LaunchedEffect(track?.id) {
        scrubbing = false
        scrub = 0f
        position = vm.positionMs().coerceAtLeast(0L)
    }

    val duration = vm.durationMs().takeIf { it > 0L } ?: (track?.durationMs ?: 0L)
    val fraction = when {
        duration <= 0L -> 0f
        scrubbing -> scrub
        else -> (position.toFloat() / duration).coerceIn(0f, 1f)
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .dynamicGlow(glowColors)
        ) {
            if (track != null) {
                ArtBackdrop(track.albumId, 720, Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PanelScrim)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconCircle(Icons.Rounded.KeyboardArrowDown, "Close player", onBack)
                Text("Now playing", color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                IconCircle(FrostIcons.QueueMusic, "Queue", { showQueue = !showQueue }, active = showQueue)
            }
            Spacer(Modifier.height(12.dp))

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showQueue) {
                    QueueList(vm)
                } else if (track != null) {
                    val side = minOf(maxWidth, maxHeight)
                    Crossfade(
                        targetState = track,
                        animationSpec = tween(320),
                        label = "album-art"
                    ) { displayedTrack ->
                        Art(
                            displayedTrack.albumId,
                            720,
                            Modifier
                                .size(side)
                                .graphicsLayer {
                                    scaleX = artScale
                                    scaleY = artScale
                                }
                                .shadow(18.dp, RoundedCornerShape(32.dp), ambientColor = GlassShadow, spotColor = GlassShadow),
                            32.dp,
                            72.dp
                        )
                    }
                } else {
                    Text("Nothing is playing yet.", color = InkMuted, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(panelShape)
                    .hazeEffect(state = hazeState, style = panelHazeStyle())
                    .glassEdge(panelShape)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    track?.title ?: "Nothing playing",
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track?.artist ?: "",
                    color = InkMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = fraction,
                    enabled = track != null && duration > 0L,
                    onValueChange = {
                        scrubbing = true
                        scrub = it
                    },
                    onValueChangeFinished = {
                        vm.seekTo((scrub * duration).toLong())
                        position = (scrub * duration).toLong()
                        scrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = AccentTint
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatDuration(if (scrubbing) (scrub * duration).toLong() else position),
                        color = InkMuted,
                        fontSize = 12.sp
                    )
                    Text(formatDuration(duration), color = InkMuted, fontSize = 12.sp)
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconCircle(FrostIcons.Shuffle, "Shuffle", { vm.toggleShuffle() }, active = vm.shuffle)
                    IconButton(
                        onClick = { vm.previous() },
                        enabled = vm.current != null,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            FrostIcons.SkipPrevious,
                            contentDescription = "Previous song",
                            tint = if (vm.current != null) Ink else InkMuted,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    val canStartPlayback = vm.current != null || vm.tracks.isNotEmpty()
                    val playSource = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .pressScale(playSource)
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (canStartPlayback) Accent else AccentTint)
                            .clickable(
                                enabled = canStartPlayback,
                                interactionSource = playSource,
                                indication = LocalIndication.current
                            ) { vm.togglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(targetState = vm.playWhenReady, animationSpec = tween(180), label = "playpause") { playing ->
                            Icon(
                                if (playing) FrostIcons.Pause else FrostIcons.Play,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = if (canStartPlayback) Color.White else InkMuted,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { vm.next() },
                        enabled = vm.current != null,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            FrostIcons.SkipNext,
                            contentDescription = "Next song",
                            tint = if (vm.current != null) Ink else InkMuted,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    IconCircle(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) FrostIcons.RepeatOne else FrostIcons.Repeat,
                        "Repeat",
                        { vm.cycleRepeat() },
                        active = vm.repeatMode != Player.REPEAT_MODE_OFF
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val favorite = track != null && track.id in vm.favorites
                    IconCircle(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Favorite",
                        { if (track != null) vm.toggleFavorite(track.id) },
                        active = favorite
                    )
                    SleepCircle(vm) { showSleep = true }
                    TextCircle(speedLabel(vm.speed), { vm.cycleSpeed() }, active = vm.speed != 1f)
                    IconCircle(
                        Icons.Rounded.Share,
                        "Share",
                        { if (track != null) shareTrack(context, track) }
                    )
                }
            }
        }
    }

    if (showSleep) {
        GlassDialog(onDismiss = { showSleep = false }) {
            DialogTitle("Sleep timer")
            Spacer(Modifier.height(14.dp))
            SleepChips(vm)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Done", { showSleep = false })
        }
    }
}

@Composable
private fun SleepButton(vm: FrostViewModel, modifier: Modifier, onClick: () -> Unit) {
    val end = vm.sleepEndsAt
    if (end == 0L) {
        GhostButton("Sleep timer", onClick, modifier, FrostIcons.Timer)
    } else {
        val now by produceState(System.currentTimeMillis(), end) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1000L)
            }
        }
        val left = ((end - now) / 1000L).coerceAtLeast(0L)
        GhostButton("%d:%02d left".format(left / 60L, left % 60L), onClick, modifier, FrostIcons.Timer)
    }
}

@Composable
private fun QueueList(vm: FrostViewModel) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (vm.queueIndex - 2).coerceAtLeast(0))
    LaunchedEffect(vm.queueIndex, vm.queue.size) {
        if (vm.queue.isNotEmpty()) {
            val activeIndex = vm.queueIndex.coerceIn(0, vm.queue.lastIndex)
            state.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }
    if (vm.queue.isEmpty()) {
        Text("The queue is empty.", color = InkMuted, fontSize = 15.sp)
    } else {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(vm.queue) { index, t ->
                QueueRow(t, active = index == vm.queueIndex) { vm.jumpTo(index) }
            }
        }
    }
}

@Composable
private fun QueueRow(track: Track, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassFlat(RoundedCornerShape(16.dp))
            .background(if (active) AccentTint else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(track.albumId, 96, Modifier.size(40.dp), 12.dp, 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (active) Accent else Ink,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                color = InkMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun speedLabel(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x"

@Composable
private fun SleepCircle(vm: FrostViewModel, onClick: () -> Unit) {
    val end = vm.sleepEndsAt
    if (end == 0L) {
        IconCircle(FrostIcons.Timer, "Sleep timer", onClick)
    } else {
        val now by produceState(System.currentTimeMillis(), end) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1000L)
            }
        }
        val left = ((end - now) / 1000L).coerceAtLeast(0L)
        TextCircle("%d:%02d".format(left / 60L, left % 60L), onClick, active = true)
    }
}
