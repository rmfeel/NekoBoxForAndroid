package io.nekohasekai.sagernet.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.view.KeyEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceDataStore
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.PluginEntry
import io.nekohasekai.sagernet.group.GroupInterfaceAdapter
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.isPlay
import io.nekohasekai.sagernet.ktx.isPreview
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.parseProxies
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import moe.matsuri.nb4a.utils.Util

class MainActivity : ThemedActivity(),
    SagerConnection.Callback,
    OnPreferenceDataStoreChangeListener {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var vpnSwitch: SwitchCompat
    private lateinit var vpnStatusText: TextView
    private lateinit var mainContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (DataStore.authToken.isNullOrBlank()) {
            startActivity(Intent(this, io.nekohasekai.sagernet.ui.auth.LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.layout_main)

        mainContainer = findViewById(R.id.main_container)
        bottomNav = findViewById(R.id.bottom_navigation)
        vpnSwitch = findViewById(R.id.vpnSwitch)
        vpnStatusText = findViewById(R.id.vpnStatusText)

        // Setup bottom navigation
        bottomNav.setOnItemSelectedListener { item ->
            displayFragmentWithId(item.itemId)
        }

        // Setup VPN Switch
        vpnSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !DataStore.serviceState.canStop) {
                if (DataStore.selectedProxy == 0L) {
                    vpnSwitch.isChecked = false
                    snackbar(getString(R.string.please_select_node)).show()
                } else {
                    connect.launch(null)
                }
            } else if (!isChecked && DataStore.serviceState.canStop) {
                SagerNet.stopService()
            }
        }

        if (savedInstanceState == null) {
            displayFragmentWithId(R.id.nav_configuration)
        }

        onBackPressedDispatcher.addCallback {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_holder)
            if (currentFragment is DashboardFragment) {
                moveTaskToBack(true)
            } else {
                displayFragmentWithId(R.id.nav_configuration)
            }
        }

        changeState(BaseService.State.Idle)
        connection.connect(this, this)
        DataStore.configurationStore.registerChangeListener(this)
        GroupManager.userInterface = GroupInterfaceAdapter(this)

        if (intent?.action == Intent.ACTION_VIEW) {
            onNewIntent(intent)
        }

        // SDK 33 notification permission
        if (Build.VERSION.SDK_INT >= 33) {
            val checkPermission = ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS)
            if (checkPermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(POST_NOTIFICATIONS), 0)
            }
        }

        if (isPreview) {
            MaterialAlertDialogBuilder(this)
                .setTitle(BuildConfig.PRE_VERSION_NAME)
                .setMessage(R.string.preview_version_hint)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return
        runOnDefaultDispatcher {
            if (uri.scheme == "sn" && uri.host == "subscription" || uri.scheme == "clash") {
                importSubscription(uri)
            } else {
                try {
                    val proxies = parseProxies(Util.getContentFromUri(this@MainActivity, uri))
                    if (!proxies.isNullOrEmpty()) {
                        for (proxy in proxies) {
                            importProfile(proxy)
                        }
                    }
                } catch (e: Exception) {
                    onMainDispatcher {
                        alert(e.readableMessage).show()
                    }
                }
            }
        }
    }

    suspend fun importSubscription(uri: Uri) {
        val group: ProxyGroup
        val url = uri.getQueryParameter("url")
        if (!url.isNullOrBlank()) {
            group = ProxyGroup(type = GroupType.SUBSCRIPTION)
            val subscription = SubscriptionBean()
            group.subscription = subscription
            subscription.link = url
            group.name = uri.getQueryParameter("name")
        } else {
            val data = uri.encodedQuery.takeIf { !it.isNullOrBlank() } ?: return
            try {
                group = KryoConverters.deserialize(
                    ProxyGroup().apply {
                        export = false
                    }, Util.zlibDecompress(Util.b64Decode(data))
                ).apply {
                    export = false
                }
            } catch (e: Exception) {
                onMainDispatcher {
                    alert(e.readableMessage).show()
                }
                return
            }
        }

        group.name = group.name.takeIf { !it.isNullOrBlank() }
            ?: ("Subscription #" + System.currentTimeMillis())

        // Auto-import without confirmation dialog
        finishImportSubscription(group)

        onMainDispatcher {
            displayFragmentWithId(R.id.nav_configuration)
            snackbar(getString(R.string.subscription_import) + ": " + group.name).show()
        }
    }

    private suspend fun finishImportSubscription(subscription: ProxyGroup) {
        GroupManager.createGroup(subscription)
        GroupUpdater.startUpdate(subscription, true)
    }

    private suspend fun importProfile(profile: AbstractBean) {
        val targetId = DataStore.selectedGroupForImport()
        ProfileManager.createProfile(targetId, profile)
        onMainDispatcher {
            displayFragmentWithId(R.id.nav_configuration)
            snackbar(resources.getQuantityString(R.plurals.added, 1, 1)).show()
        }
    }

    override fun missingPlugin(profileName: String, pluginName: String) {
        val pluginEntity = PluginEntry.find(pluginName)
        if (pluginEntity == null) {
            snackbar(getString(R.string.plugin_unknown, pluginName)).show()
            return
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.missing_plugin)
            .setMessage(getString(R.string.profile_requiring_plugin, profileName, pluginEntity.displayName))
            .setPositiveButton(R.string.action_download) { _, _ ->
                launchCustomTab(pluginEntity.downloadSource.downloadLink)
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    @SuppressLint("CommitTransaction")
    private fun displayFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_holder, fragment)
            .commitAllowingStateLoss()
    }

    fun displayFragmentWithId(@IdRes id: Int): Boolean {
        when (id) {
            R.id.nav_configuration -> displayFragment(DashboardFragment())
            R.id.nav_route -> displayFragment(RouteFragment())
            R.id.nav_logcat -> displayFragment(LogcatFragment())
            R.id.nav_settings -> displayFragment(SettingsFragment())
            else -> return false
        }
        return true
    }

    private fun changeState(
        state: BaseService.State,
        msg: String? = null,
        animate: Boolean = false,
    ) {
        DataStore.serviceState = state

        runOnUiThread {
            when (state) {
                BaseService.State.Connected -> {
                    vpnSwitch.isChecked = true
                    vpnStatusText.text = getString(R.string.vpn_connected)
                    vpnStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    // Play pulse animation when connected
                    if (animate) {
                        val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.vpn_switch_pulse)
                        vpnSwitch.startAnimation(pulseAnimation)
                    }
                }
                BaseService.State.Connecting -> {
                    vpnStatusText.text = getString(R.string.connecting)
                }
                BaseService.State.Stopping -> {
                    vpnStatusText.text = getString(R.string.stopping)
                }
                else -> {
                    vpnSwitch.isChecked = false
                    vpnStatusText.text = getString(R.string.not_connected)
                    vpnStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                }
            }
        }

        if (msg != null) snackbar(getString(R.string.vpn_error, msg)).show()
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(mainContainer, text, Snackbar.LENGTH_LONG)
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        changeState(state, msg, true)
    }

    val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)

    override fun onServiceConnected(service: ISagerNetService) = changeState(
        try {
            BaseService.State.values()[service.state]
        } catch (_: RemoteException) {
            BaseService.State.Idle
        }
    )

    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }

    private val connect = registerForActivityResult(VpnRequestActivity.StartService()) {
        if (it) snackbar(R.string.vpn_permission_denied).show()
    }

    override fun cbSpeedUpdate(stats: SpeedDisplayData) {
        // Speed updates - could be shown in UI if needed
    }

    override fun cbTrafficUpdate(data: TrafficData) {
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(data)
        }
    }

    override fun cbSelectorUpdate(id: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = id
        DataStore.currentProfile = id
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(id, true)
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.SERVICE_MODE -> onBinderDied()
            Key.PROXY_APPS, Key.BYPASS_MODE, Key.INDIVIDUAL -> {
                if (DataStore.serviceState.canStop) {
                    snackbar(getString(R.string.need_reload)).setAction(R.string.apply) {
                        SagerNet.reloadService()
                    }.show()
                }
            }
        }
    }

    override fun onStart() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
        super.onStart()
    }

    override fun onStop() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        GroupManager.userInterface = null
        DataStore.configurationStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }
}
