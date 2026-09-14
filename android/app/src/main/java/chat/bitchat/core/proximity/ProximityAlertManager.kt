package chat.bitchat.core.proximity

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import chat.bitchat.core.bluetooth.BluetoothRepository
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.data.database.EchoMeshDatabase
import chat.bitchat.data.database.Peer
import chat.bitchat.data.database.UserProfile
import chat.bitchat.domain.repository.ProfileRepository
import chat.bitchat.ui.util.parseInterests
import chat.bitchat.ui.util.rssiToMeters
import chat.bitchat.ui.util.sharedInterests
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

object ProximityEvaluator {
    const val COOLDOWN_MILLIS = 30 * 60 * 1000L // 30 minutes

    /**
     * Pure evaluator for mutual passion proximity alerts.
     * Reuses EchoMesh's rssiToMeters() < 5m and stable peer identity.
     */
    fun evaluatePeerAlert(
        device: NearbyDevice,
        myProfile: UserProfile?,
        peers: List<Peer>,
        threshold: Int,
        alertedPeers: MutableMap<String, Long>,
        now: Long = System.currentTimeMillis()
    ): String? {
        if (myProfile == null) return null

        // 1. Proximity check: Reuses EchoMesh rssiToMeters() < 5m
        val distanceMeters = rssiToMeters(device.rssi)
        if (distanceMeters >= 5) return null

        // 2. Resolve stable peer identity (dev_* or canonical peerID)
        val peerByIdMap = peers.associateBy { it.peerID.lowercase() }
        val peerByNameMap = peers.filter { it.nickname.isNotBlank() }.associateBy { it.nickname.lowercase() }

        val peer = peerByIdMap[device.identity.lowercase()]
            ?: peerByIdMap[device.id.lowercase()]
            ?: peerByNameMap[device.name.lowercase()]
            ?: return null

        val stablePeerId = peer.peerID.takeIf { it.isNotBlank() }
            ?: device.identity.takeIf { it.startsWith("dev_") }
            ?: return null // Cannot alert without stable identity

        // 3. Cooldown check: 30 minutes (1,800,000 ms) per stable peer identity
        val lastAlert = alertedPeers[stablePeerId]
        if (lastAlert != null && (now - lastAlert < COOLDOWN_MILLIS)) {
            return null
        }

        // 4. Mutual interest calculation
        val myInterestsStr = listOfNotNull(
            myProfile.interests,
            myProfile.favoriteMusic,
            myProfile.favoriteMovies,
            myProfile.singers,
            myProfile.career
        ).joinToString(", ")
        val myInterests = parseInterests(myInterestsStr)

        val theirInterestsStr = listOfNotNull(
            peer.interests,
            peer.favoriteMusic,
            peer.favoriteMovies,
            peer.singers,
            peer.career
        ).joinToString(", ")
        val theirInterests = parseInterests(theirInterestsStr)

        val shared = sharedInterests(myInterests, theirInterests)

        // 5. Threshold match check
        if (shared.size >= threshold) {
            alertedPeers[stablePeerId] = now
            return stablePeerId
        }

        return null
    }
}

@Singleton
class ProximityAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothRepository: BluetoothRepository,
    private val profileRepository: ProfileRepository,
    private val database: EchoMeshDatabase
) {
    private val TAG = "ProximityAlertManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var observingJob: Job? = null

    private val prefs = context.getSharedPreferences("echomesh_proximity_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _threshold = MutableStateFlow(prefs.getInt(KEY_THRESHOLD, 3))
    val threshold: StateFlow<Int> = _threshold.asStateFlow()

    // 30-minute cooldown cache per stable peer identity (dev_* or canonical peerID)
    val alertedPeers = ConcurrentHashMap<String, Long>()

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrator service unavailable: ${e.message}")
            null
        }
    }

    @Synchronized
    fun startObserving() {
        if (observingJob?.isActive == true) {
            Log.d(TAG, "Observer already active — skipping redundant startup.")
            return
        }

        observingJob = scope.launch {
            Log.i(TAG, "Starting proximity mutual interest alert collector")
            combine(
                bluetoothRepository.getDiscoveredDevices(),
                profileRepository.getProfile(),
                database.peerDao().getAllPeers()
            ) { devices, myProfile, peers ->
                if (!_enabled.value || myProfile == null) return@combine
                val currentThreshold = _threshold.value

                for (device in devices) {
                    val alertedPeerId = ProximityEvaluator.evaluatePeerAlert(
                        device = device,
                        myProfile = myProfile,
                        peers = peers,
                        threshold = currentThreshold,
                        alertedPeers = alertedPeers,
                        now = System.currentTimeMillis()
                    )
                    if (alertedPeerId != null) {
                        Log.i(TAG, "Mutual interest alert triggered for peer: $alertedPeerId (threshold: $currentThreshold)")
                        triggerHaptic()
                    }
                }
            }.collect()
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    fun setThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(1, 3)
        prefs.edit().putInt(KEY_THRESHOLD, clamped).apply()
        _threshold.value = clamped
    }

    fun triggerHaptic() {
        try {
            val v = vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 75ms pulse -> 95ms pause -> 75ms pulse
                val timings = longArrayOf(0, 75, 95, 75)
                val amplitudes = intArrayOf(0, 180, 0, 180)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(75)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to execute haptic vibration: ${e.message}")
        }
    }

    companion object {
        const val KEY_ENABLED = "proximity_alerts_enabled"
        const val KEY_THRESHOLD = "proximity_alerts_threshold"
    }
}
