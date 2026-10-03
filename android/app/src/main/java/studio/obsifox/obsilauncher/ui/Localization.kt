package studio.obsifox.obsilauncher.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import studio.obsifox.obsilauncher.app
import java.util.Locale

/**
 * v1.13.0 — live language switching WITHOUT restarting the activity.
 *
 * The old flow called Activity.recreate() — the app visibly rebooted on every
 * language change. Instead, this wrapper rebuilds a localized Context (locale
 * + layout direction) around the whole shell whenever the setting changes;
 * every `stringResource` under it re-resolves on the next composition, so the
 * UI flips instantly, in place. The attachBaseContext override stays as the
 * cold-start default so system dialogs match before Compose is up.
 */
@Composable
fun ObsiLanguage(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val language by context.app.settings.language.collectAsState()

    val localized = remember(language) {
        when (language) {
            "system" -> context
            else -> {
                val locale = Locale.forLanguageTag(language)
                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)
                config.setLayoutDirection(locale)
                context.createConfigurationContext(config)
            }
        }
    }

    val direction = when (language) {
        "fa" -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalLayoutDirectionSafe provides direction,
        content = content,
    )
}

/**
 * Compose's LocalLayoutDirection lives in androidx.compose.ui.platform —
 * re-exported here under a stable name so the wrapper stays readable.
 */
private val LocalLayoutDirectionSafe = androidx.compose.ui.platform.LocalLayoutDirection
