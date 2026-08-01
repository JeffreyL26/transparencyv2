package com.jbateam.scanconvert.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jbateam.scanconvert.R
import com.jbateam.scanconvert.data.GalleryAlbum
import com.jbateam.scanconvert.data.GalleryPhoto
import com.jbateam.scanconvert.data.GalleryStore
import com.jbateam.scanconvert.ui.components.Ic
import com.jbateam.scanconvert.ui.components.IcBack
import com.jbateam.scanconvert.ui.components.IcImage
import com.jbateam.scanconvert.ui.components.PrimaryButton
import com.jbateam.scanconvert.ui.components.scaleClick
import com.jbateam.scanconvert.ui.theme.Tokens
import com.jbateam.scanconvert.ui.theme.Txt

/**
 * In-App-Galerie (§Galerie-Scan): Album-Auswahl (Chips) + Foto-Grid, NUR Bilder.
 * Ein Tap auf ein Foto öffnet den Foto-Scan. Ohne Medien-Berechtigung erscheint
 * ein Erklär-Zustand mit „Zugriff erlauben“-Button.
 *
 * [hasPermission] wird von [com.jbateam.scanconvert.MainActivity] verwaltet;
 * [partialAccess] markiert die „Ausgewählte Fotos“-Teilfreigabe (Android 14+),
 * damit die UI eine erneute Auswahl anbieten kann.
 */
@Composable
fun GalleryScreen(
    hasPermission: Boolean,
    partialAccess: Boolean,
    onRequestPermission: () -> Unit,
    onPickPhoto: (GalleryPhoto) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Canvas)
            .statusBarsPadding()
    ) {
        GalleryHeader(onClose = onClose)

        if (!hasPermission) {
            GalleryPermissionState(onRequestPermission = onRequestPermission)
            return@Column
        }

        GalleryContent(
            partialAccess = partialAccess,
            onManageAccess = onRequestPermission,
            onPickPhoto = onPickPhoto,
        )
    }
}

/** Kopfzeile: Zurück-Pfeil + Titel. */
@Composable
private fun GalleryHeader(onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .scaleClick(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Ic(IcBack, tint = Tokens.Ink2, modifier = Modifier.size(22.dp))
        }
        Txt(
            stringResource(R.string.gallery_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Tokens.Ink,
        )
    }
}

/** Alben + Foto-Grid, sobald die Berechtigung vorliegt. */
@Composable
private fun GalleryContent(
    partialAccess: Boolean,
    onManageAccess: () -> Unit,
    onPickPhoto: (GalleryPhoto) -> Unit,
) {
    val context = LocalContext.current
    val photos by produceState<List<GalleryPhoto>?>(initialValue = null) {
        value = GalleryStore.loadPhotos(context)
    }
    val loaded = photos
    val albums by produceState<List<GalleryAlbum>>(initialValue = emptyList(), loaded) {
        value = if (loaded == null) emptyList() else GalleryStore.loadAlbums(context, loaded)
    }

    // null = „Alle Fotos“; sonst eine bucketId.
    var selectedBucket by remember { mutableStateOf<Long?>(null) }

    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Txt(stringResource(R.string.gallery_loading), fontSize = 14.sp, color = Tokens.Ink3)
        }
        return
    }

    if (loaded.isEmpty()) {
        Box(
            Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Txt(
                stringResource(R.string.gallery_empty),
                fontSize = 14.sp,
                color = Tokens.Ink2,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val shown = remember(loaded, selectedBucket) {
        if (selectedBucket == null) loaded else loaded.filter { it.bucketId == selectedBucket }
    }

    Column(Modifier.fillMaxSize()) {
        AlbumChips(
            albums = albums,
            allCount = loaded.size,
            selectedBucket = selectedBucket,
            onSelect = { selectedBucket = it },
        )

        if (partialAccess) {
            PartialAccessBanner(onManageAccess = onManageAccess)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp),
        ) {
            items(shown, key = { it.id }) { photo ->
                PhotoCell(photo = photo, onClick = { onPickPhoto(photo) })
            }
        }
    }
}

/** Horizontale Album-Chips; erster Chip ist immer „Alle Fotos“. */
@Composable
private fun AlbumChips(
    albums: List<GalleryAlbum>,
    allCount: Int,
    selectedBucket: Long?,
    onSelect: (Long?) -> Unit,
) {
    LazyRow(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            AlbumChip(
                name = stringResource(R.string.gallery_all_photos),
                count = allCount,
                selected = selectedBucket == null,
                onClick = { onSelect(null) },
            )
        }
        items(albums, key = { it.bucketId ?: -1L }) { album ->
            AlbumChip(
                name = album.name,
                count = album.count,
                selected = selectedBucket == album.bucketId,
                onClick = { onSelect(album.bucketId) },
            )
        }
    }
}

/** Ein Album-Chip: Name + Anzahl, Accent-Rahmen im gewählten Zustand. */
@Composable
private fun AlbumChip(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .scaleClick(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Tokens.AccentSoft else Tokens.Surface)
            .border(1.5.dp, if (selected) Tokens.Accent else Tokens.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Txt(
            name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) Tokens.AccentDeep else Tokens.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Txt(
            count.toString(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Tokens.Ink3,
        )
    }
}

/** Eine Grid-Zelle: quadratisches Thumbnail (asynchron geladen). */
@Composable
private fun PhotoCell(photo: GalleryPhoto, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(initialValue = null, photo.id) {
        value = GalleryStore.thumbnail(context, photo)
    }
    Box(
        Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Tokens.SurfaceWarm)
            .scaleClick(scale = 0.96f, onClick = onClick),
    ) {
        thumb?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Teilzugriff-Hinweis (Android 14+ „Ausgewählte Fotos“): erneut auswählen. */
@Composable
private fun PartialAccessBanner(onManageAccess: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.AccentSoft)
            .scaleClick(scale = 0.99f, onClick = onManageAccess)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(
            stringResource(R.string.gallery_partial_access),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Tokens.AccentDeep,
            modifier = Modifier.weight(1f),
        )
        Txt(
            stringResource(R.string.gallery_manage_access),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Tokens.AccentDeep,
        )
    }
}

/** Erklär-Zustand ohne Medien-Berechtigung. */
@Composable
private fun GalleryPermissionState(onRequestPermission: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Tokens.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Ic(IcImage, tint = Tokens.AccentDeep, modifier = Modifier.size(36.dp))
        }
        Txt(
            stringResource(R.string.gallery_permission_title),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Tokens.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Txt(
            stringResource(R.string.gallery_permission_body),
            fontSize = 13.5.sp,
            color = Tokens.Ink2,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.gallery_permission_cta),
            icon = IcImage,
            onClick = onRequestPermission,
        )
    }
}
