package com.hackerapps.c2k.ui.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hackerapps.c2k.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Preview of the share image with a choice of backdrop, and the share button. [loadRoute] reads the
 * workout's GPS points; [message] is text shared alongside the image where the target app takes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardDialog(
    stats: ShareCardStats,
    loadRoute: suspend () -> List<OutlinePoint>,
    message: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var background by remember { mutableStateOf<ShareBackground>(ShareBackground.Photo(1)) }
    var customPhoto by remember { mutableStateOf<Bitmap?>(null) }
    // Null until loaded, so the first image already has the route rather than redrawing with it.
    val route by produceState<List<OutlinePoint>?>(null) { value = loadRoute() }
    val rendered by produceState<Bitmap?>(null, background, customPhoto, route) {
        val loaded = route ?: return@produceState
        value = withContext(Dispatchers.Default) {
            ShareCardRenderer.render(context, stats, loaded, background, customPhoto)
        }
    }
    val thumbnails by produceState(emptyMap<Int, ImageBitmap>()) {
        value = withContext(Dispatchers.IO) {
            (1..ShareBackground.PHOTO_COUNT).associateWith { n ->
                BitmapFactory.decodeResource(context.resources, ShareCardRenderer.photoRes(n),
                    BitmapFactory.Options().apply { inSampleSize = 8 }).asImageBitmap()
            }
        }
    }
    // The system photo picker: no storage permission, and only the chosen photo is shared with C2K.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val photo = withContext(Dispatchers.IO) { decodePhoto(context, uri) }
            if (photo != null) {
                customPhoto = photo
                background = ShareBackground.Custom
            }
        }
    }

    // A full-height sheet: it keeps clear of the system bars, so the Share button stays on screen.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null
    ) {
        Box(modifier = Modifier.fillMaxSize().testTag("share_card_dialog")) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("share_card_close")) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.share_card_close))
                    }
                    Text(stringResource(R.string.share_card_title), style = MaterialTheme.typography.titleLarge)
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val image = rendered
                    // Waits for the picker's thumbnails too, so the preview never shows blank tiles.
                    if (image == null || thumbnails.isEmpty()) {
                        CircularProgressIndicator()
                    } else {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                // Sized from the height left over, so the picker and Share button always fit below.
                                .aspectRatio(ShareCardRenderer.WIDTH / ShareCardRenderer.HEIGHT.toFloat(), matchHeightConstraintsFirst = true)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (background == ShareBackground.Sticker) Color.Gray.copy(alpha = 0.3f) else Color.Transparent)
                                .testTag("share_card_preview")
                        )
                    }
                }
                BackgroundPicker(
                    selected = background,
                    customPhoto = customPhoto,
                    thumbnails = thumbnails,
                    onPickPhoto = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onSelect = { background = it }
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val image = rendered ?: return@Button
                        scope.launch {
                            val intent = withContext(Dispatchers.IO) {
                                ShareCardRenderer.shareIntent(context, image, message)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, null))
                            } catch (_: ActivityNotFoundException) {
                                // Nothing on the device can share an image.
                            }
                        }
                    },
                    enabled = rendered != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).testTag("share_card_share")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.celebration_share))
                }
            }
        }
    }
}

@Composable
private fun BackgroundPicker(
    selected: ShareBackground,
    customPhoto: Bitmap?,
    thumbnails: Map<Int, ImageBitmap>,
    onPickPhoto: () -> Unit,
    onSelect: (ShareBackground) -> Unit
) {
    val yourPhoto = stringResource(R.string.share_card_your_photo)
    val sticker = stringResource(R.string.share_card_sticker)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Tile(selected == ShareBackground.Custom, yourPhoto, "share_bg_custom", onPickPhoto) {
                if (customPhoto != null) {
                    Image(customPhoto.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White)
            }
        }
        item {
            Tile(selected == ShareBackground.Sticker, sticker, "share_bg_sticker", { onSelect(ShareBackground.Sticker) }) {
                Icon(Icons.Default.CropFree, contentDescription = null)
            }
        }
        itemsIndexed(ShareBackground.builtIn) { index, choice ->
            val label = stringResource(R.string.share_card_background, index + 1)
            val tag = when (choice) {
                is ShareBackground.Gradient -> "share_bg_gradient_${choice.number}"
                is ShareBackground.Photo -> "share_bg_photo_${choice.number}"
                else -> ""
            }
            Tile(selected == choice, label, tag, { onSelect(choice) }) {
                when (choice) {
                    is ShareBackground.Gradient -> {
                        val colors = ShareCardRenderer.gradients[choice.number].map { Color(it) }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors)))
                    }
                    is ShareBackground.Photo -> thumbnails[choice.number]?.let {
                        Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun Tile(
    selected: Boolean,
    label: String,
    tag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 54.dp, height = 96.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(3.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .testTag(tag)
    ) { content() }
}

/** The picked photo, scaled to about the image's size: a full camera photo would only use memory. */
private fun decodePhoto(context: Context, uri: Uri): Bitmap? = try {
    val maxSide = 1440
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder also applies the photo's rotation.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
} catch (_: Exception) {
    null
}
