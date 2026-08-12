package chat.bitchat.data.repository

import chat.bitchat.data.database.UserProfile
import chat.bitchat.data.database.UserProfileDao
import chat.bitchat.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao
) : ProfileRepository {

    override fun getProfile(): Flow<UserProfile?> {
        return userProfileDao.getProfile()
    }

    override suspend fun saveProfile(profile: UserProfile) {
        userProfileDao.insertOrUpdateProfile(profile)
    }
}
