package app.accord.ui.vendors

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.accord.R
import app.accord.ui.components.DecorativeIcon

/** Logo nominativo, sempre com descrição textual ao lado. Sem tintura, rede ou parser SVG. */
@Composable
fun VendorLogo(vendor: Vendor, modifier: Modifier = Modifier) {
    val drawable = when (vendor) {
        Vendor.Uber -> R.drawable.vendor_uber
        Vendor.Spotify -> R.drawable.vendor_spotify
    }
    Surface(
        modifier = modifier.size(48.dp).testTag("vendor-logo-${vendor.key}"),
        color = Color.White,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        )
    }
}

/** A marca só substitui o desenho; categoria, tipo e dados financeiros continuam nos textos. */
@Composable
fun EntryIdentityIcon(
    description: String,
    @DrawableRes fallbackIcon: Int,
    allowVendor: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val vendor = remember(description, allowVendor) {
        if (allowVendor) VendorCatalog.matchExplicitDescription(description) else null
    }
    if (vendor != null) {
        VendorLogo(vendor, modifier)
    } else {
        Surface(modifier = modifier.size(48.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DecorativeIcon(fallbackIcon, modifier = Modifier.size(20.dp))
            }
        }
    }
}
