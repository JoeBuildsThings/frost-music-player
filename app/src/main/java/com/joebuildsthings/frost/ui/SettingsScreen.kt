package com.joebuildsthings.frost.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebuildsthings.frost.FrostViewModel
import com.joebuildsthings.frost.ui.theme.Accent
import com.joebuildsthings.frost.ui.theme.AccentTint
import com.joebuildsthings.frost.ui.theme.Ink
import com.joebuildsthings.frost.ui.theme.InkMuted
import com.joebuildsthings.frost.ui.theme.glass
import com.joebuildsthings.frost.ui.theme.glassFlat

private const val GITHUB_URL = "https://github.com/JoeBuildsThings"

private fun openBatterySettings(context: Context) {
    try {
        context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (e: Exception) {
        context.startActivity(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }
}

private fun freqLabel(hz: Int): String {
    if (hz < 1000) return "$hz"
    val k = hz / 1000f
    return if (k == k.toInt().toFloat()) "${k.toInt()}k" else "${(k * 10).toInt() / 10f}k"
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = AccentTint
            )
        )
    }
}

@Composable
fun SettingsScreen(vm: FrostViewModel, bottomPad: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    val sliderColors = SliderDefaults.colors(
        thumbColor = Accent,
        activeTrackColor = Accent,
        inactiveTrackColor = AccentTint
    )

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(14.dp))
            Text("Settings", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "appearance") {
                SettingsCard("Appearance") {
                    SwitchRow("Dark mode", vm.darkMode) { vm.updateDarkMode(it) }
                }
            }
            item(key = "playback") {
                SettingsCard("Playback") {
                    SwitchRow("Keep playing during games", vm.keepPlaying) { vm.updateKeepPlaying(it) }
                    GhostButton("Battery settings", { openBatterySettings(context) }, Modifier.fillMaxWidth())
                }
            }
            if (vm.eqFreqs.isNotEmpty() && vm.eqMax > vm.eqMin) {
                item(key = "eq") {
                    SettingsCard("Equalizer") {
                        SwitchRow("Enabled", vm.eqOn) { vm.updateEqOn(it) }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Chip("Flat", false) { vm.applyEqPreset(0) }
                            Chip("Bass", false) { vm.applyEqPreset(1) }
                            Chip("Vocal", false) { vm.applyEqPreset(2) }
                            Chip("Treble", false) { vm.applyEqPreset(3) }
                        }
                        vm.eqFreqs.forEachIndexed { i, hz ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    freqLabel(hz),
                                    color = InkMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(40.dp)
                                )
                                Slider(
                                    value = (vm.eqLevels.getOrNull(i) ?: 0).toFloat(),
                                    onValueChange = { vm.updateEqBand(i, it.toInt()) },
                                    valueRange = vm.eqMin.toFloat()..vm.eqMax.toFloat(),
                                    colors = sliderColors,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Bass",
                                color = InkMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(40.dp)
                            )
                            Slider(
                                value = vm.bass.toFloat(),
                                onValueChange = { vm.updateBass(it.toInt()) },
                                valueRange = 0f..1000f,
                                colors = sliderColors,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            item(key = "library") {
                SettingsCard("Library") {
                    Text("Hide songs shorter than", color = InkMuted, fontSize = 13.sp)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Chip("Show all", vm.minSeconds == 0) { vm.updateMinSeconds(0) }
                        Chip("30 seconds", vm.minSeconds == 30) { vm.updateMinSeconds(30) }
                        Chip("60 seconds", vm.minSeconds == 60) { vm.updateMinSeconds(60) }
                    }
                    GhostButton(
                        if (vm.refreshing) "Scanning" else "Scan again",
                        { vm.scan() },
                        Modifier.fillMaxWidth()
                    )
                }
            }
            item(key = "sleep") {
                SettingsCard("Sleep timer") {
                    SleepChips(vm)
                }
            }
            item(key = "folders_head") {
                SectionTitle("HIDDEN FOLDERS")
            }
            items(vm.allFolders, key = { "f_" + it.path }) { folder ->
                val hidden = folder.path in vm.excluded
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glassFlat()
                        .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBlock(FrostIcons.Folder, Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            folder.name,
                            color = Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("${folder.tracks.size} songs", color = InkMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = hidden,
                        onCheckedChange = { vm.setFolderHidden(folder.path, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = AccentTint
                        )
                    )
                }
            }
            item(key = "github") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(24.dp))
                        .clickable { uriHandler.openUri(GITHUB_URL) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(FrostIcons.GitHub, contentDescription = "GitHub", tint = Ink, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("JoeBuildsThings", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("github.com/JoeBuildsThings", color = InkMuted, fontSize = 13.sp)
                    }
                }
            }
            item(key = "version") {
                Text(
                    "Frost Music Player $version",
                    color = InkMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
