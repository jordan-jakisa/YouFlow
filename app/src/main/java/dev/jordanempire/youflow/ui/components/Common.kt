package dev.jordanempire.youflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jordanempire.youflow.ui.model.UiState

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

@Composable
fun MessageBox(
    title: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Renders Loading / Error / Content, with [emptyContent] when [isEmpty] says the data is empty. */
@Composable
fun <T> StateHost(
    state: UiState<T>,
    onRetry: () -> Unit,
    isEmpty: (T) -> Boolean = { false },
    emptyContent: @Composable () -> Unit = {},
    content: @Composable (T) -> Unit
) {
    when (state) {
        UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(state, onRetry)
        is UiState.Content -> if (isEmpty(state.data)) emptyContent() else content(state.data)
    }
}

@Composable
fun Thumbnail(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

/** Error with Retry, or a Verify button when YouTube asked for a reCAPTCHA. */
@Composable
fun ErrorBox(error: UiState.Error, onRetry: () -> Unit, modifier: Modifier = Modifier, title: String = "Couldn't load this") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { onRetry() }
    if (error.recaptchaUrl != null) {
        MessageBox(
            "Quick check needed",
            error.message,
            "Verify",
            {
                launcher.launch(
                    android.content.Intent(context, dev.jordanempire.youflow.error.ReCaptchaActivity::class.java)
                        .putExtra(dev.jordanempire.youflow.error.ReCaptchaActivity.RECAPTCHA_URL_EXTRA, error.recaptchaUrl)
                )
            },
            modifier
        )
    } else {
        MessageBox(title, error.message, "Retry", onRetry, modifier)
    }
}

/** Circular channel avatar, with the first letter of the name when YouTube sends no image. */
@Composable
fun Avatar(url: String?, name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.CircleShape
    if (!url.isNullOrBlank()) {
        Thumbnail(
            url,
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer)
        )
    } else {
        Box(
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
