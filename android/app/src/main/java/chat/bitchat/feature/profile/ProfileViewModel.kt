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

    val profileState: StateFlow<UserProfile?> = profileRepository.getProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateProfile(name: String, bio: String, movies: String, music: String, interests: String, singers: String, career: String) {
        viewModelScope.launch {
            val updated = UserProfile(
                id = 1,
                name = name,
                bio = bio,
                favoriteMovies = movies,
                favoriteMusic = music,
                interests = interests,
                singers = singers,
                career = career
            )
            profileRepository.saveProfile(updated)
            // Re-advertise so nearby phones see the new nickname
            bluetoothRepository.refreshAdvertisingIdentity()
        }
    }
}
