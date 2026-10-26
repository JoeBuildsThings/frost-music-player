package com.joebuildsthings.frost.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebuildsthings.frost.FrostViewModel
import com.joebuildsthings.frost.data.AlbumItem
import com.joebuildsthings.frost.data.Playlist
import com.joebuildsthings.frost.data.Track
import com.joebuildsthings.frost.ui.Art
import com.joebuildsthings.frost.ui.DetailSource
import com.joebuildsthings.frost.ui.EmptyNote
import com.joebuildsthings.frost.ui.FrostIcons
import com.joebuildsthings.frost.ui.GlassDialog
import com.joebuildsthings.frost.ui.DialogTitle
import com.joebuildsthings.frost.ui.IconBlock
import com.joebuildsthings.frost.ui.ListRow
import com.joebuildsthings.frost.ui.MenuItem
import com.joebuildsthings.frost.ui.NameDialog
import com.joebuildsthings.frost.ui.Overlay
import com.joebuildsthings.frost.ui.PlayRow
import com.joebuildsthings.frost.ui.TrackMenu
import com.joebuildsthings.frost.ui.TrackRow
import com.joebuildsthings.frost.ui.theme.Ink
import com.joebuildsthings.frost.ui.theme.InkMuted
import com.joebuildsthings.frost.ui.theme.glassFlat

@Composable
fun AlbumsPage(vm: FrostViewModel, state: LazyGridState, bottomPad: Dp, topPad: Dp, onOpen: (Overlay) -> Unit) {
    val albums = vm.albums
    if (albums.isEmpty()) {
        EmptyNote("No albums yet", "Nothing here yet.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = state,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album) { onOpen(Overlay.Detail(DetailSource.Album(album.id))) }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumItem, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassFlat(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Art(album.id, 320, Modifier.fillMaxWidth().aspectRatio(1f), 16.dp, 36.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            album.artist,
            color = InkMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text("${album.tracks.size} songs", color = InkMuted, fontSize = 11.sp)
    }
}
