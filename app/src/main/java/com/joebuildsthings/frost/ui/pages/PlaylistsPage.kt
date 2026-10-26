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
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
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
fun PlaylistsPage(vm: FrostViewModel, state: LazyListState, bottomPad: Dp, topPad: Dp, onOpen: (Overlay) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Playlist?>(null) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(
        state = state,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "favorites") {
            ListRow(
                title = "Favorites",
                subtitle = "${vm.favorites.size} songs",
                onClick = { onOpen(Overlay.Detail(DetailSource.Favorites)) }
            ) {
                IconBlock(Icons.Rounded.Favorite)
            }
        }
        item(key = "recent") {
            ListRow(
                title = "Recently played",
                subtitle = "${vm.recent.size} songs",
                onClick = { onOpen(Overlay.Detail(DetailSource.Recent)) }
            ) {
                IconBlock(FrostIcons.Timer)
            }
        }
        item(key = "most") {
            ListRow(
                title = "Most played",
                subtitle = "",
                onClick = { onOpen(Overlay.Detail(DetailSource.MostPlayed)) }
            ) {
                IconBlock(Icons.Rounded.Star)
            }
        }
        item(key = "added") {
            ListRow(
                title = "Recently added",
                subtitle = "",
                onClick = { onOpen(Overlay.Detail(DetailSource.Added)) }
            ) {
                IconBlock(Icons.Rounded.DateRange)
            }
        }
        item(key = "new") {
            ListRow(
                title = "New playlist",
                subtitle = "",
                onClick = { creating = true }
            ) {
                IconBlock(Icons.Rounded.Add)
            }
        }
        items(vm.playlists, key = { it.id }) { playlist ->
            ListRow(
                title = playlist.name,
                subtitle = "${playlist.trackIds.size} songs",
                onClick = { onOpen(Overlay.Detail(DetailSource.Playlist(playlist.id))) },
                onMore = { menuFor = playlist }
            ) {
                IconBlock(FrostIcons.QueueMusic)
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                vm.createPlaylist(name)
                creating = false
            }
        )
    }

    menuFor?.let { playlist ->
        GlassDialog(onDismiss = { menuFor = null }) {
            DialogTitle(playlist.name)
            Spacer(Modifier.height(8.dp))
            MenuItem("Rename", Icons.Rounded.Edit) {
                renaming = playlist
                menuFor = null
            }
            MenuItem("Delete playlist", Icons.Rounded.Delete) {
                vm.deletePlaylist(playlist.id)
                menuFor = null
            }
        }
    }

    renaming?.let { playlist ->
        NameDialog(
            title = "Rename playlist",
            initial = playlist.name,
            confirmLabel = "Save",
            onDismiss = { renaming = null },
            onConfirm = { name ->
                vm.renamePlaylist(playlist.id, name)
                renaming = null
            }
        )
    }
}
