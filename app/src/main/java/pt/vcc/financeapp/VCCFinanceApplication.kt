package pt.vcc.financeapp

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.usecase.SeedSystemCategoriesUseCase

@HiltAndroidApp
class VCCFinanceApplication : Application() {
    @Inject lateinit var seedSystemCategories: SeedSystemCategoriesUseCase

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Falha numa tarefa de arranque", error)
        },
    )

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { seedSystemCategories() }
    }

    private companion object {
        const val TAG = "VCCFinanceApp"
    }
}
