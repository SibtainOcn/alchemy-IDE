package dev.hazel.code

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hazel.code.ui.common.AccessGate
import dev.hazel.code.ui.common.BrandSplash
import dev.hazel.code.ui.common.Motion
import dev.hazel.code.ui.common.Storage
import dev.hazel.code.ui.editor.EditorScreen
import dev.hazel.code.ui.editor.EditorViewModel
import dev.hazel.code.ui.explorer.ExplorerScreen
import dev.hazel.code.ui.explorer.ExplorerViewModel
import dev.hazel.code.ui.theme.HazelAccents
import dev.hazel.code.ui.theme.HazelTheme
import dev.hazel.code.ui.theme.LocalAccents
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the system splash only for the first frame; the in-app brand moment takes
        // over from there so there is never a black gap between the two.
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        val startFile = fileFromIntent(intent)

        setContent {
            HazelTheme {
                CompositionLocalProvider(LocalAccents provides HazelAccents()) {
                    LaunchedEffect(Unit) { ready = true }
                    HazelApp(startFile)
                }
            }
        }
    }

    /**
     * VIEW/EDIT intents from other apps. Direct file paths work as-is; the storage
     * provider's document URIs are mapped back to a path, which covers the common
     * "open with" flows. Anything else is declined clearly rather than opened blank.
     */
    private fun fileFromIntent(intent: Intent?): File? {
        val uri = intent?.data ?: return null
        return when {
            uri.scheme == "file" -> uri.path?.let(::File)?.takeIf { it.isFile }
            DocumentsContract.isDocumentUri(this, uri) -> resolveDocumentUri(uri)
            else -> null
        }
    }

    private fun resolveDocumentUri(uri: Uri): File? = runCatching {
        val id = DocumentsContract.getDocumentId(uri)
        val parts = id.split(":", limit = 2)
        if (parts.size != 2) return null
        val (type, relative) = parts
        if (uri.authority != "com.android.externalstorage.documents") return null
        val base = if (type.equals("primary", true)) {
            android.os.Environment.getExternalStorageDirectory()
        } else {
            File("/storage/$type")
        }
        File(base, relative).takeIf { it.isFile }
    }.getOrNull()
}

@Composable
private fun HazelApp(startFile: File?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAccess by remember { mutableStateOf(Storage.hasAccess(context)) }
    var booting by remember { mutableStateOf(true) }
    var openPath by rememberSaveable { mutableStateOf(startFile?.absolutePath) }

    // Access is granted on a Settings screen outside the app, so the only reliable moment
    // to re-check is when we come back to the foreground.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = Storage.hasAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val legacyPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAccess = granted }

    LaunchedEffect(Unit) {
        // Long enough to read the mark, short enough not to be in the way.
        delay(620)
        booting = false
    }

    AnimatedContent(
        targetState = when {
            booting -> Phase.SPLASH
            !hasAccess -> Phase.GATE
            openPath != null -> Phase.EDITOR
            else -> Phase.EXPLORER
        },
        transitionSpec = {
            when {
                initialState == Phase.SPLASH ->
                    (fadeIn(Motion.standard()) + scaleIn(Motion.expressive(), initialScale = 1.04f))
                        .togetherWith(fadeOut(Motion.standard()) + scaleOut(Motion.standard(), targetScale = 0.97f))

                targetState == Phase.EDITOR ->
                    (slideInHorizontally(Motion.offset()) { it / 6 } + fadeIn(Motion.standard()))
                        .togetherWith(fadeOut(Motion.snappy()))

                else ->
                    (slideInHorizontally(Motion.offset()) { -it / 8 } + fadeIn(Motion.standard()))
                        .togetherWith(fadeOut(Motion.snappy()))
            }
        },
        label = "phase",
        modifier = Modifier,
    ) { phase ->
        when (phase) {
            Phase.SPLASH -> BrandSplash()

            Phase.GATE -> AccessGate {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { context.startActivity(Storage.allFilesAccessIntent(context)) }
                        .onFailure { context.startActivity(Storage.appSettingsIntent(context)) }
                } else {
                    legacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            Phase.EXPLORER -> {
                val vm: ExplorerViewModel = viewModel()
                LaunchedEffect(hasAccess) { if (hasAccess) vm.refresh() }
                ExplorerScreen(vm = vm, onOpenFile = { openPath = it.absolutePath })
            }

            Phase.EDITOR -> {
                val vm: EditorViewModel = viewModel()
                // Captured once for this content instance: openPath is already null while
                // the editor animates out, and reading it live would blank the screen
                // mid-transition.
                val path = remember { openPath }
                if (path != null) {
                    EditorScreen(vm = vm, file = File(path), onClose = { openPath = null })
                }
            }
        }
    }
}

private enum class Phase { SPLASH, GATE, EXPLORER, EDITOR }
