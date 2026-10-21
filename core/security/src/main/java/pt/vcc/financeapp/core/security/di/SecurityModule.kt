package pt.vcc.financeapp.core.security.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import pt.vcc.financeapp.core.security.AndroidBiometricAuthenticator
import pt.vcc.financeapp.core.security.BiometricAuthenticator
import pt.vcc.financeapp.core.security.DataStoreSecurityStore
import pt.vcc.financeapp.core.security.KeystorePinPepper
import pt.vcc.financeapp.core.security.PinPepper
import pt.vcc.financeapp.core.security.SecurityStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SecurityDataStore

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SecurityModule {
    @Binds
    abstract fun bindSecurityStore(impl: DataStoreSecurityStore): SecurityStore

    @Binds
    abstract fun bindPinPepper(impl: KeystorePinPepper): PinPepper

    @Binds
    abstract fun bindBiometricAuthenticator(impl: AndroidBiometricAuthenticator): BiometricAuthenticator

    companion object {
        @Provides
        @Singleton
        @SecurityDataStore
        fun provideSecurityDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("security") }
    }
}
