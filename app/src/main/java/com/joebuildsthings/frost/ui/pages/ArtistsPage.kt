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
fun ArtistsPage(vm: FrostViewModel, state: LazyListState, bottomPad: Dp, topPad: Dp, onOpen: (Overlay) -> Unit) {
    val artists = vm.artists
    if (artists.isEmpty()) {
        EmptyNote("No artists yet", "Nothing here yet.")
    } else {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(artists, key = { it.name }) { artist ->
                ListRow(
                    title = artist.name,
                    subtitle = "${artist.tracks.size} songs, ${artist.albumCount} albums",
                    onClick = { onOpen(Overlay.Detail(DetailSource.Artist(artist.name))) }
                ) {
                    Art(artist.tracks.first().albumId, 128, Modifier.size(48.dp), 24.dp)
                }
            }
        }
    }
}
