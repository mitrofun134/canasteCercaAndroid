package ar.canastacerca.core.storage.impl

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import ar.canastacerca.core.storage.KeyValueStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DataStoreKeyValueStore(application: Application) : KeyValueStore {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { application.preferencesDataStoreFile("canasta") },
    )

    private val data: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override fun observe(key: String): Flow<String?> =
        data.map { it[stringPreferencesKey(key)] }.distinctUntilChanged()

    override suspend fun get(key: String): String? = observe(key).first()

    override suspend fun put(key: String, value: String?) {
        update(key) { value }
    }

    override suspend fun update(key: String, transform: (String?) -> String?) {
        val prefKey = stringPreferencesKey(key)
        dataStore.edit { prefs ->
            val next = transform(prefs[prefKey])
            if (next == null) prefs.remove(prefKey) else prefs[prefKey] = next
        }
    }
}
