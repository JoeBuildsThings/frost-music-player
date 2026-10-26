package com.joebuildsthings.frost.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.joebuildsthings.frost.FrostViewModel
import com.joebuildsthings.frost.data.ArtCache
import com.joebuildsthings.frost.data.Track
import com.joebuildsthings.frost.ui.theme.Accent
import com.joebuildsthings.frost.ui.theme.AccentTint
import com.joebuildsthings.frost.ui.theme.GlassEdgeFade
import com.joebuildsthings.frost.ui.theme.GlassEdgeStrong
import com.joebuildsthings.frost.ui.theme.GlassFlat
import com.joebuildsthings.frost.ui.theme.Ink
import com.joebuildsthings.frost.ui.theme.InkMuted
import com.joebuildsthings.frost.ui.theme.glass
import com.joebuildsthings.frost.ui.theme.glassFlat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60L, total % 60L)
}

@Composable
fun Art(
    albumId: Long,
    requestPx: Int,
    modifier: Modifier,
    corner: Dp = 14.dp,
    iconSize: Dp = 22.dp
) {
    val context = LocalContext.current
    val art by produceState<Bitmap?>(ArtCache.peek(albumId, requestPx), albumId, requestPx) {
        value = ArtCache.peek(albumId, requestPx)
            ?: withContext(Dispatchers.IO) { ArtCache.load(context, albumId, requestPx) }
    }
    val image = remember(art) { art?.asImageBitmap() }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(AccentTint),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(FrostIcons.MusicNote, contentDescription = null, tint = Accent, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun IconBlock(icon: ImageVector, modifier: Modifier = Modifier.size(48.dp)) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AccentTint),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun IconCircle(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 44.dp
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source)
            .size(size)
            .clip(CircleShape)
            .background(if (active) Accent else GlassFlat)
            .border(1.dp, if (active) Accent else GlassEdgeFade, CircleShape)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) Color.White else Ink,
            modifier = Modifier.size(size / 2)
        )
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(source)
            .clip(RoundedCornerShape(16.dp))
            .background(Accent)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(source)
            .glassFlat(RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun PlayRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PrimaryButton("Play all", onPlay, Modifier.weight(1f), FrostIcons.Play)
        GhostButton("Shuffle", onShuffle, Modifier.weight(1f), FrostIcons.Shuffle)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = InkMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
fun TrackRow(
    track: Track,
    active: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    playing: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassFlat()
            .background(if (active) AccentTint else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp)) {
            Art(track.albumId, 128, Modifier.size(48.dp), 14.dp)
            if (active) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) {
                    EqBars(playing)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (active) Accent else Ink,
                fontSize = 15.sp,
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
        if (favorite) {
            Icon(Icons.Rounded.Favorite, contentDescription = "Favorite", tint = Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(formatDuration(track.durationMs), color = InkMuted, fontSize = 12.sp)
        IconButton(onClick = onMore) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = InkMuted)
        }
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    leading: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassFlat()
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = if (onMore == null) 14.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    color = InkMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = InkMuted)
            }
        }
    }
}

@Composable
fun StateCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AccentTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
            }
            Text(
                title,
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                body,
                color = InkMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            content()
        }
    }
}

@Composable
fun EmptyNote(title: String, body: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    StateCard(Modifier.fillMaxSize(), FrostIcons.MusicNote, title, body) {
        if (actionLabel != null) PrimaryButton(actionLabel, onAction)
    }
}

@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xF7FFFFFF))
                .border(1.dp, GlassEdgeStrong, shape)
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
fun DialogTitle(text: String) {
    Text(text, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    GlassDialog(onDismiss) {
        DialogTitle(title)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .glassFlat(RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (text.isEmpty()) Text("Name", color = InkMuted, fontSize = 16.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onDismiss, Modifier.weight(1f))
            PrimaryButton(confirmLabel, { onConfirm(text) }, Modifier.weight(1f))
        }
    }
}

@Composable
fun TrackMenu(track: Track, vm: FrostViewModel, onDismiss: () -> Unit, playlistId: String? = null) {
    var picking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val favorite = track.id in vm.favorites
    val context = LocalContext.current

    if (naming) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { naming = false },
            onConfirm = { name ->
                vm.createPlaylist(name, track.id)
                naming = false
                onDismiss()
            }
        )
    } else if (picking) {
        GlassDialog(onDismiss = { picking = false }) {
            DialogTitle("Add to playlist")
            Spacer(Modifier.height(8.dp))
            MenuItem("New playlist", Icons.Rounded.Add) { naming = true }
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                vm.playlists.forEach { p ->
                    MenuItem(p.name, FrostIcons.QueueMusic) {
                        vm.addToPlaylist(p.id, track.id)
                        onDismiss()
                    }
                }
            }
        }
    } else {
        GlassDialog(onDismiss = onDismiss) {
            DialogTitle(track.title)
            Text(track.artist, color = InkMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            MenuItem("Play next", FrostIcons.Play) {
                vm.playNext(track)
                onDismiss()
            }
            MenuItem("Add to queue", FrostIcons.QueueMusic) {
                vm.addToQueue(track)
                onDismiss()
            }
            MenuItem(
                if (favorite) "Remove from favorites" else "Add to favorites",
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
            ) {
                vm.toggleFavorite(track.id)
                onDismiss()
            }
            MenuItem("Add to playlist", Icons.Rounded.Add) { picking = true }
            MenuItem("Share", Icons.Rounded.Share) {
                shareTrack(context, track)
                onDismiss()
            }
            if (playlistId != null) {
                MenuItem("Remove from this playlist", Icons.Rounded.Delete) {
                    vm.removeFromPlaylist(playlistId, track.id)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(source)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Accent else AccentTint)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SleepChips(vm: FrostViewModel) {
    val options = listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min")
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (minutes, label) ->
            Chip(label, selected = vm.sleepMinutes == minutes) { vm.setSleepTimer(minutes) }
        }
    }
}

@Composable
fun ArtBackdrop(albumId: Long, requestPx: Int, modifier: Modifier) {
    val context = LocalContext.current
    val art by produceState<Bitmap?>(ArtCache.peek(albumId, requestPx), albumId, requestPx) {
        value = ArtCache.peek(albumId, requestPx)
            ?: withContext(Dispatchers.IO) { ArtCache.load(context, albumId, requestPx) }
    }
    val image = remember(art) { art?.asImageBitmap() }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.93f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "press"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun EqBars(playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "eq")
    val a by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "a"
    )
    val b by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "b"
    )
    val c by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(460), RepeatMode.Reverse),
        label = "c"
    )
    Canvas(Modifier.size(20.dp)) {
        val w = size.width / 5f
        val heights = if (playing) floatArrayOf(a, b, c) else floatArrayOf(0.35f, 0.6f, 0.45f)
        for (i in 0 until 3) {
            val h = size.height * heights[i]
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * (i * 2), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f)
            )
        }
    }
}

@Composable
fun TextCircle(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 44.dp
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source)
            .size(size)
            .clip(CircleShape)
            .background(if (active) Accent else GlassFlat)
            .border(1.dp, if (active) Accent else GlassEdgeFade, CircleShape)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Color.White else Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

fun shareTrack(context: Context, track: Track) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, track.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
