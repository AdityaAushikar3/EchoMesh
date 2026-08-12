package chat.bitchat.di

import android.content.Context
import androidx.room.Room
import chat.bitchat.data.database.EchoMeshDatabase
import chat.bitchat.data.database.MessageDao
import chat.bitchat.data.database.PeerDao
import chat.bitchat.data.database.UserProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): EchoMeshDatabase {
        return Room.databaseBuilder(
            context,
            EchoMeshDatabase::class.java,
            "echomesh_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideUserProfileDao(database: EchoMeshDatabase): UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    fun providePeerDao(database: EchoMeshDatabase): PeerDao {
        return database.peerDao()
    }

    @Provides
    fun provideMessageDao(database: EchoMeshDatabase): MessageDao {
        return database.messageDao()
    }
}
