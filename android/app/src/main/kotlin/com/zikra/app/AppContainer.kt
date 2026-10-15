package com.zikra.app

import android.app.Application
import android.content.Context
import com.zikra.app.auth.KeystoreTokenStore
import com.zikra.core.network.AuthRepository
import com.zikra.core.network.GatedTransport
import com.zikra.core.network.NetworkGate
import com.zikra.core.network.OkHttpTransport
import com.zikra.core.network.SessionHolder
import com.zikra.core.network.SupabaseClient
import com.zikra.core.network.SupabaseConfig

/** Manual dependency wiring for the app process (a DI framework can replace it later, ADR-003). */
class AppContainer(context: Context) {
    val sessions = SessionHolder()
    private val config = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)

    /** False in builds without Supabase values: the app is then guest-only and hides sign-in. */
    val accountsAvailable: Boolean = config.isConfigured

    val supabase = SupabaseClient(config, GatedTransport(NetworkGate(sessions), OkHttpTransport()))
    val auth = AuthRepository(supabase, KeystoreTokenStore(context.applicationContext), sessions)
}

class ZikraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.auth.restore()
    }
}
