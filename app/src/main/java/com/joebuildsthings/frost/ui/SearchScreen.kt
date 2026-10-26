package com.joebuildsthings.frost.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.joebuildsthings.frost.FrostViewModel
import com.joebuildsthings.frost.data.Track
import com.joebuildsthings.frost.ui.theme.Accent
import com.joebuildsthings.frost.ui.theme.Ink
import com.joebuildsthings.frost.ui.theme.InkMuted
import com.joebuildsthings.frost.ui.theme.glass
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(vm: FrostViewModel, bottomPad: Dp, onBack: () -> Unit, onOpen: (Overlay) -> Unit) {
    var query by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    var menuTrack by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(query) {
        delay(180L)
        applied = query
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val q = applied.trim().lowercase()
    val songs = remember(q, vm.tracks) {
        if (q.isEmpty()) {
            emptyList()
        } else {
            vm.tracks.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
            }.take(120)
        }
    }
    val albums = remember(q, vm.albums) {
        if (q.isEmpty()) {
            emptyList()
        } else {
            vm.albums.filter { it.name.lowercase().contains(q) || it.artist.lowercase().contains(q) }.take(12)
        }
    }
    val artists = remember(q, vm.artists) {
        if (q.isEmpty()) emptyList() else vm.artists.filter { it.name.lowercase().contains(q) }.take(12)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier
                    .weight(1f)
                    .glass(RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = InkMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Songs, albums, artists", color = InkMuted, fontSize = 16.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(Accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear",
                        tint = InkMuted,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { query = "" }
                    )
                }
            }
        }

        if (q.isEmpty()) {
            Text(
                "",
                color = InkMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        } else if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            Text(
                "No results",
                color = InkMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (artists.isNotEmpty()) {
                    item(key = "h_artists") { SectionTitle("ARTISTS") }
                    items(artists, key = { "ar_" + it.name }) { artist ->
                        ListRow(
                            title = artist.name,
                            subtitle = "${artist.tracks.size} songs",
                            onClick = { onOpen(Overlay.Detail(DetailSource.Artist(artist.name))) }
                        ) {
                            Art(artist.tracks.first().albumId, 128, Modifier.size(48.dp), 24.dp)
                        }
                    }
                }
                if (albums.isNotEmpty()) {
                    item(key = "h_albums") { SectionTitle("ALBUMS") }
                    items(albums, key = { "al_" + it.id }) { album ->
                        ListRow(
                            title = album.name,
                            subtitle = album.artist,
                            onClick = { onOpen(Overlay.Detail(DetailSource.Album(album.id))) }
                        ) {
                            Art(album.id, 128, Modifier.size(48.dp), 14.dp)
                        }
                    }
                }
                if (songs.isNotEmpty()) {
                    item(key = "h_songs") { SectionTitle("SONGS") }
                    itemsIndexed(songs, key = { _, t -> "s_" + t.id }) { index, track ->
                        TrackRow(
                            track = track,
                            active = track.id == vm.current?.id,
                            playing = vm.playWhenReady,
                            favorite = track.id in vm.favorites,
                            onClick = { vm.play(songs, index) },
                            onMore = { menuTrack = track }
                        )
                    }
                }
            }
        }
    }
    menuTrack?.let { TrackMenu(it, vm, onDismiss = { menuTrack = null }) }
}
