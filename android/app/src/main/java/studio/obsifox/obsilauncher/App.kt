package studio.obsifox.obsilauncher

import android.app.Application
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.accounts.AccountStore
import studio.obsifox.obsilauncher.core.auth.MicrosoftAuth
import studio.obsifox.obsilauncher.core.game.GameManager
import studio.obsifox.obsilauncher.core.game.VersionInstaller
import studio.obsifox.obsilauncher.core.game.VersionManifest
import studio.obsifox.obsilauncher.core.instance.InstancesStore
import studio.obsifox.obsilauncher.core.loaders.LoaderService
import studio.obsifox.obsilauncher.core.modrinth.ModrinthInstaller
import studio.obsifox.obsilauncher.core.runtime.RuntimePacks
import studio.obsifox.obsilauncher.core.update.UpdateGate
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
    lateinit var instances: InstancesStore
        private set
    lateinit var loaders: LoaderService
        private set
    lateinit var modrinth: ModrinthInstaller
        private set
    lateinit var microsoft: MicrosoftAuth
        private set
    lateinit var updateGate: UpdateGate
        private set

    val gameManager: GameManager by lazy {
        GameManager(settings).also { gm ->
            gm.sessionEvents = object : GameManager.SessionEvents {
                override fun onSessionStart(instanceId: String) {
                    instances.markLaunched(instanceId)
                }
                override fun onSessionEnd(instanceId: String, seconds: Long) {
                    instances.recordSession(instanceId, seconds)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = ObsiSettings(this)
        accounts = AccountStore(this)
        wallpaper = ObsiWallpaper(this, settings)
        manifest = VersionManifest(this)
        installer = VersionInstaller(this)
        runtimePacks = RuntimePacks(this)
        instances = InstancesStore(this, settings)
        loaders = LoaderService(this)
        modrinth = ModrinthInstaller(this)
        microsoft = MicrosoftAuth()
        updateGate = UpdateGate(this, runtimePacks)
    }
}

val android.content.Context.app: App
    get() = applicationContext as App
