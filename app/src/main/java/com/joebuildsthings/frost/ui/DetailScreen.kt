package com.joebuildsthings.frost.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebuildsthings.frost.FrostViewModel
import com.joebuildsthings.frost.data.Track
import com.joebuildsthings.frost.ui.theme.Ink
import com.joebuildsthings.frost.ui.theme.InkMuted

private class DetailInfo(
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
    val artAlbumId: Long
)

private fun resolveDetail(source: DetailSource, vm: FrostViewModel): DetailInfo = when (source) {
    is DetailSource.Album -> {
        val a = vm.albums.firstOrNull { it.id == source.id }
        DetailInfo(a?.name ?: "Album", a?.artist ?: "", a?.tracks ?: emptyList(), source.id)
    }
    is DetailSource.Artist -> {
        val a = vm.artists.firstOrNull { it.name == source.name }
        DetailInfo(
            source.name,
            "${a?.tracks?.size ?: 0} songs",
            a?.tracks ?: emptyList(),
            a?.tracks?.firstOrNull()?.albumId ?: 0L
        )
    }
    is DetailSource.Folder -> {
        val f = vm.folders.firstOrNull { it.path == source.path }
        DetailInfo(
            f?.name ?: "Folder",
            source.path,
            f?.tracks ?: emptyList(),
            f?.tracks?.firstOrNull()?.albumId ?: 0L
        )
    }
    is DetailSource.Playlist -> {
        val p = vm.playlists.firstOrNull { it.id == source.id }
        val byId = vm.tracks.associateBy { it.id }
        val list = p?.trackIds?.mapNotNull { byId[it] } ?: emptyList()
        DetailInfo(p?.name ?: "Playlist", "${list.size} songs", list, list.firstOrNull()?.albumId ?: 0L)
    }
    DetailSource.Favorites -> {
        val list = vm.tracks.filter { it.id in vm.favorites }
        DetailInfo("Favorites", "${list.size} songs", list, list.firstOrNull()?.albumId ?: 0L)
    }
    DetailSource.Recent -> {
        val byId = vm.tracks.associateBy { it.id }
        val list = vm.recent.mapNotNull { byId[it] }
        DetailInfo("Recently played", "${list.size} songs", list, list.firstOrNull()?.albumId ?: 0L)
    }
    DetailSource.MostPlayed -> {
        val list = vm.tracks
            .filter { (vm.plays[it.id] ?: 0) > 0 }
            .sortedByDescending { vm.plays[it.id] ?: 0 }
            .take(50)
        DetailInfo("Most played", "${list.size} songs", list, list.firstOrNull()?.albumId ?: 0L)
    }
    DetailSource.Added -> {
        val list = vm.tracks.sortedByDescending { it.addedAt }.take(100)
        DetailInfo("Recently added", "${list.size} songs", list, list.firstOrNull()?.albumId ?: 0L)
    }
}

@Composable
fun DetailScreen(source: DetailSource, vm: FrostViewModel, bottomPad: Dp, onBack: () -> Unit) {
    val info = resolveDetail(source, vm)
    val tracks = info.tracks
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val playlistId = (source as? DetailSource.Playlist)?.id

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Art(info.artAlbumId, 360, Modifier.size(112.dp), 24.dp, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            info.title,
                            color = Ink,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            info.subtitle,
                            color = InkMuted,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (tracks.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Nothing here yet.",
                        color = InkMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                item(key = "actions") {
                    PlayRow(onPlay = { vm.play(tracks, 0) }, onShuffle = { vm.shuffleAll(tracks) })
                }
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        active = track.id == vm.current?.id,
                        playing = vm.playWhenReady,
                        favorite = track.id in vm.favorites,
                        onClick = { vm.play(tracks, index) },
                        onMore = { menuTrack = track }
                    )
                }
            }
        }
    }
    menuTrack?.let { TrackMenu(it, vm, onDismiss = { menuTrack = null }, playlistId = playlistId) }
}
