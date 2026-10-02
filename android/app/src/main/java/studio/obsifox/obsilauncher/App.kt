package studio.obsifox.obsilauncher

import android.app.Application
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.accounts.AccountStore
import studio.obsifox.obsilauncher.core.game.GameManager
import studio.obsifox.obsilauncher.core.game.VersionInstaller
import studio.obsifox.obsilauncher.core.game.VersionManifest
import studio.obsifox.obsilauncher.core.runtime.RuntimePacks
import studio.obsifox.obsilauncher.obsi.ObsiWallpaper

/**
 * ObsiLauncher — a free, from-scratch Minecraft launcher for Android.
 * GPL-3.0-or-later. No access keys, no locked features.
 */
class App : Application() {

    lateinit var settings: ObsiSettings
        private set
    lateinit var accounts: AccountStore
        private set
    lateinit var wallpaper: ObsiWallpaper
        private set
    lateinit var manifest: VersionManifest
        private set
    lateinit var installer: VersionInstaller
        private set
    lateinit var runtimePacks: RuntimePacks
        private set

    val gameManager: GameManager by lazy { GameManager(settings) }

    override fun onCreate() {
        super.onCreate()
        settings = ObsiSettings(this)
        accounts = AccountStore(this)
        wallpaper = ObsiWallpaper(this, settings)
        manifest = VersionManifest(this)
        installer = VersionInstaller(this)
        runtimePacks = RuntimePacks(this)
    }
}

val android.content.Context.app: App
    get() = applicationContext as App
