package chat.bitchat.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.bitchat.core.bluetooth.BluetoothRepository
import chat.bitchat.data.database.UserProfile
import chat.bitchat.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val bluetoothRepository: BluetoothRepository
) : ViewModel() {

    val localIdentity: String = bluetoothRepository.localIdentity

    val profileState: StateFlow<UserProfile?> = profileRepository.getProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateProfile(name: String, bio: String, tags: List<String>) {
        viewModelScope.launch {
            val serializedInterests = tags.joinToString(", ")
            val updated = UserProfile(
                id = 1,
                name = name,
                bio = bio,
                interests = serializedInterests,
                favoriteMovies = "",
                favoriteMusic = "",
                singers = "",
                career = ""
            )
            profileRepository.saveProfile(updated)
            // Re-advertise so nearby phones see the new profile/nickname
            bluetoothRepository.refreshAdvertisingIdentity()
        }
    }
}
