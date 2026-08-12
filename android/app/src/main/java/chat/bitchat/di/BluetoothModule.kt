package chat.bitchat.di

import chat.bitchat.core.bluetooth.BluetoothManager
import chat.bitchat.core.bluetooth.BluetoothRepository
import chat.bitchat.data.repository.BluetoothManagerImpl
import chat.bitchat.data.repository.BluetoothRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothModule {

    @Binds
    @Singleton
    abstract fun bindBluetoothManager(
        impl: BluetoothManagerImpl
    ): BluetoothManager

    @Binds
    @Singleton
    abstract fun bindBluetoothRepository(
        impl: BluetoothRepositoryImpl
    ): BluetoothRepository
}
