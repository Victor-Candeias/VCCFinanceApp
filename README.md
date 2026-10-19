# VCCFinanceApp — Arquitetura e Plano de Implementação Android

> **Versão:** 1.0 · **Data:** 19/10/2026 · **Plataforma:** Android · **Linguagem:** Kotlin
>
> **Objetivo:** aplicação de gestão financeira pessoal, inicialmente para uso próprio, preparada para publicação futura. Funciona em modo **híbrido**: movimentos manuais, importação de extratos e sincronização bancária quando disponível.

## 1. Visão geral e âmbito

A **VCCFinanceApp** permite gerir contas bancárias, cartões de refeição, dinheiro, cartões de crédito e poupanças. Deve funcionar integralmente offline, com sincronização opcional, PIN e biometria, relatórios e exportação.

### Funcionalidades

| Área | Funcionalidades |
|---|---|
| Dashboard | Saldos por conta, receitas, despesas, tendências e alertas |
| Contas | Bancos, cartões de refeição, dinheiro, poupanças e cartões de crédito |
| Movimentos | Receitas, despesas, transferências, pesquisa, filtros e anexos |
| Importação | CSV, XLSX, OFX, QIF, JSON; PDF numa fase posterior |
| Sincronização | Open Banking via backend e fornecedor autorizado; cartões de refeição quando suportados |
| Orçamentos | Limites por categoria, períodos, notificações |
| Relatórios | Mensal, anual, por conta, categoria, estabelecimento e cartão de refeição |
| Exportação | CSV, XLSX, PDF, JSON |
| Segurança | PIN, biometria, bloqueio automático, encriptação, backup protegido |
| Futuro | Perfis, contas cloud, sincronização entre dispositivos e publicação Google Play |

**Regras de negócio fundamentais:** não contar transferências internas como despesa/receita global; não duplicar movimentos importados; distinguir movimentos pendentes de confirmados; preservar saldo comunicado pela instituição separadamente do saldo calculado; não somar moedas distintas sem conversão explícita.

## 2. Stack tecnológica

| Camada | Tecnologia |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Linguagem | Kotlin |
| Arquitetura | Clean Architecture + MVVM, offline-first |
| Navegação | Navigation Compose com rotas tipadas |
| Injeção | Hilt |
| Persistência | Room / SQLite |
| Preferências | DataStore (sem segredos em claro) |
| Concorrência | Coroutines, Flow, StateFlow |
| Segurança | BiometricPrompt, Android Keystore, AES-GCM, PIN com KDF robusta |
| Rede futura | Retrofit, OkHttp |
| Jobs | WorkManager |
| Gráficos | Vico ou alternativa Compose compatível |
| Documentos | Android Storage Access Framework; PDF/CSV/XLSX/JSON |
| Backend futuro | ASP.NET Core ou Ktor; integração Open Banking autorizada |

**Dinheiro:** guardar montantes em `Long` como unidades mínimas da moeda (ex.: 12,50 EUR = 1250). Evitar `Float`/`Double` em cálculos financeiros. No domínio usar `Money(amountMinor: Long, currency: String)` e validar moeda/overflow.

## 3. Organização em módulos Gradle

Módulos iniciais:

1. `:app` — inicialização, `MainActivity`, `VCCFinanceApplication`, navegação.
2. `:core:model` — modelos e enums independentes de Android.
3. `:core:database` — Room, DAOs, entidades, converters e migrations.
4. `:core:security` — PIN, biometria, Keystore e sessão.
5. `:core:data` — contratos, implementações dos repositórios e mappers.
6. `:core:designsystem` — Material 3, tema e componentes comuns.
7. `:feature:finance` — dashboard, contas, movimentos, relatórios, definições e autenticação.

Dividir `:feature:finance` em módulos especializados apenas quando o crescimento justificar.

```text
VCCFinanceApp/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── app/src/main/
│   ├── AndroidManifest.xml
│   └── java/pt/vcc/financeapp/
│       ├── VCCFinanceApplication.kt
│       ├── MainActivity.kt
│       └── navigation/AppNavHost.kt
├── core/
│   ├── model/src/main/java/pt/vcc/financeapp/core/model/
│   │   ├── Account.kt
│   │   ├── Transaction.kt
│   │   ├── Category.kt
│   │   └── Money.kt
│   ├── database/src/main/java/pt/vcc/financeapp/core/database/
│   │   ├── VCCFinanceDatabase.kt
│   │   ├── DatabaseModule.kt
│   │   ├── converters/FinanceConverters.kt
│   │   ├── entity/   (uma entidade por tabela da v1 — ver §5)
│   │   └── dao/      (um DAO por entidade)
│   ├── data/src/main/java/pt/vcc/financeapp/core/data/
│   │   ├── repository/
│   │   ├── mapper/
│   │   └── di/
│   ├── security/src/main/java/pt/vcc/financeapp/core/security/
│   │   ├── PinManager.kt
│   │   ├── BiometricAuthenticator.kt
│   │   ├── SessionManager.kt
│   │   └── SecurityPreferences.kt
│   └── designsystem/src/main/java/pt/vcc/financeapp/core/designsystem/
│       ├── VCCFinanceTheme.kt
│       └── components/
├── feature/finance/src/main/java/pt/vcc/financeapp/feature/finance/
│   ├── authentication/
│   ├── dashboard/
│   ├── accounts/
│   ├── transactions/
│   ├── mealcard/
│   ├── budgets/
│   ├── reports/
│   ├── importexport/
│   └── settings/
└── README.md
```

### Dependências entre módulos

```text
:app ──> :feature:finance ──> :core:data ──> :core:database
  │              │                 │                 │
  └──────────────┴─────────────────┴──────────────> :core:model
  │              │
  └──────────────┴──> :core:designsystem
  └─────────────────> :core:security
```

Não permitir que `:core:model` dependa de Android ou de Room. Idealmente, `:core:data` mapeia entidades de persistência para modelos de domínio.

## 4. Configuração Gradle

### Matriz de versões fixada

Fixada a 19/10/2026 a partir do ambiente instalado no PC de desenvolvimento e da [tabela de compatibilidade do AGP 9.2](https://developer.android.com/build/releases/agp-9-2-0-release-notes) (Gradle ≥ 9.4.1, Build Tools ≥ 36.0.0, JDK 17, KGP ≥ 2.2.10, API máx. 37).

| Componente | Versão | Estado |
|---|---|---|
| Android Studio | 2026.1.3 (build 261.26222.65) | Instalado |
| JDK do Gradle | 17 (Temurin 17.0.20.1, `%USERPROFILE%\.jdks\jdk-17.0.20.1+1`) | Instalado |
| Gradle Wrapper | 9.4.1 | Instalado |
| AGP | 9.2.1 | Em cache local |
| `compileSdk` / `targetSdk` | 37 | Plataforma `android-37.0` instalada |
| `minSdk` | 26 | — |
| SDK Build Tools | 36.0.0 | Instalado |
| Kotlin (KGP e plugin Compose) | 2.4.20 | Em cache local |
| KSP | 2.3.12 | Em cache local |
| Hilt | 2.60.1 | Em cache local |
| Compose BOM | 2026.09.00 | Em cache local |
| Room | 2.8.5 | Em cache local |
| Navigation Compose | 2.10.2 | Em cache local |
| Activity Compose | 1.13.0 | Em cache local |
| Lifecycle | 2.10.0 | Em cache local |
| Hilt Navigation Compose 1.4.0, DataStore 1.2.1, Biometric 1.1.0 | — | Não estão em cache; transferidas no primeiro sync |

> **Notas:** o Android Studio usa por omissão a JBR 25 (`JAVA_HOME`); o ficheiro `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=17`) obriga o daemon Gradle a usar um JDK 17 instalado localmente, mesmo que o launcher arranque com outra JVM. Em alternativa, configurar o **Gradle JDK = 17** (Settings → Build Tools → Gradle). A combinação Kotlin 2.4.20 + KSP 2.3.12 + Hilt 2.60.1 é a única ainda não comprovada em conjunto: confirmar com `./gradlew assembleDebug` no Sprint 1 e, se falhar, ajustar apenas estas versões.

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "VCCFinanceApp"
include(":app", ":core:model", ":core:database", ":core:data",
        ":core:security", ":core:designsystem", ":feature:finance")
```

### `gradle/libs.versions.toml` — versões fixadas

```toml
[versions]
agp = "9.2.1"
kotlin = "2.4.20"
ksp = "2.3.12"
composeBom = "2026.09.00"
room = "2.8.5"
navigation = "2.10.2"
hilt = "2.60.1"
hiltNavigation = "1.4.0"
activityCompose = "1.13.0"
lifecycle = "2.10.0"
datastore = "1.2.1"
biometric = "1.1.0"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-tooling = { module = "androidx.compose.ui:ui-tooling" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigation" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### `app/build.gradle.kts` — esqueleto

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
android {
    namespace = "pt.vcc.financeapp"
    compileSdk = 37
    defaultConfig {
        applicationId = "pt.vcc.financeapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:finance"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    debugImplementation(libs.compose.tooling)
}
```

Cada módulo Android Library requer o seu `build.gradle.kts`, `namespace`, SDK, plugins e dependências. `:core:model` pode ser Kotlin/JVM puro. Para rotas tipadas, adicionar plugin Kotlin Serialization e `kotlinx-serialization-json`; para `collectAsStateWithLifecycle`, `lifecycle-runtime-compose`. A forma de aplicar Kotlin/AGP deve respeitar a versão AGP escolhida, nomeadamente as alterações de integração Kotlin em AGP 9+.

## 5. Modelo de dados

### Tabelas da v1

**Decisão:** todas as tabelas abaixo entram na versão 1 do schema Room, para evitar migrations logo nas primeiras iterações.

| Tabela | Função |
|---|---|
| `accounts` | Contas e cartões |
| `transactions` | Movimentos |
| `categories` | Categorias |
| `merchants` | Estabelecimentos |
| `budgets` | Orçamentos |
| `recurring_transactions` | Recorrências |
| `financial_connections` | Ligações financeiras e estado do consentimento |
| `sync_history` | Auditoria de sincronizações |
| `import_batches` | Histórico de importações |
| `transaction_rules` | Categorização automática |
| `attachments` | Recibos e comprovativos |
| `account_balance_snapshots` | Saldos comunicados e datas de referência |

### Tipos de conta e movimento

```kotlin
enum class AccountType { BANK_ACCOUNT, MEAL_CARD, CREDIT_CARD, CASH, SAVINGS, OTHER }
enum class TransactionType { INCOME, EXPENSE, TRANSFER }
enum class TransactionSource { MANUAL, CSV_IMPORT, EXCEL_IMPORT, OFX_IMPORT, QIF_IMPORT, BANK_SYNC, MEAL_CARD_SYNC }
enum class TransactionStatus { PENDING, BOOKED, CANCELLED }
enum class ConnectionStatus { DISCONNECTED, PENDING_AUTHORIZATION, CONNECTED, EXPIRED, ERROR }
```

### `AccountEntity.kt`

```kotlin
@Entity(tableName = "accounts", indices = [Index("connectionId")])
data class AccountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: AccountType,
    val institutionName: String? = null,
    val ibanEncrypted: String? = null,
    val currency: String = "EUR",
    val openingBalanceMinor: Long = 0L,
    val connectionId: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

### `TransactionEntity.kt`

```kotlin
@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"], childColumns = ["accountId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [
        Index("accountId"), Index("bookingDate"), Index("categoryId"),
        Index(value = ["accountId", "source", "externalId"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String = "EUR",
    val description: String,
    val categoryId: String? = null,
    val merchantId: String? = null,
    val bookingDate: Long,
    val valueDate: Long? = null,
    val status: TransactionStatus = TransactionStatus.BOOKED,
    val source: TransactionSource,
    val externalId: String? = null,
    val transferGroupId: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

**Convenção:** `amountMinor` sempre positivo; `type` indica direção. Transferência entre contas próprias = dois movimentos vinculados por `transferGroupId`, um de saída e outro de entrada; para relatórios globais, excluir ambas as pernas do total de receitas/despesas. O índice externo não substitui deduplicação de ficheiros sem IDs. Para sincronização real, incluir `providerId`/namespace estável no identificador externo.

### `CategoryEntity.kt`

```kotlin
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: TransactionType,
    val parentCategoryId: String? = null,
    val isSystem: Boolean = false,
    val isActive: Boolean = true
)
```

Categorias iniciais: Alimentação (supermercado, café, restaurante), Habitação, Transportes, Saúde, Educação, Lazer, Subscrições, Ordenado, Subsídio, Reembolso e Outros. Permitir subcategorias, cores, ícones e regras por estabelecimento.

### `FinancialConnectionEntity.kt`

```kotlin
@Entity(tableName = "financial_connections")
data class FinancialConnectionEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val institutionId: String,
    val institutionName: String,
    val status: ConnectionStatus,
    val consentExpiresAt: Long? = null,
    val lastSyncAt: Long? = null,
    val nextSyncAt: Long? = null,
    val lastErrorCode: String? = null
)
```

**Nunca** guardar tokens bancários em claro nesta tabela; tokens de integração devem ficar no backend seguro.

## 6. Room: DAOs, converters e injeção

### `VCCFinanceDatabase.kt`

```kotlin
@Database(
    entities = [
        AccountEntity::class, TransactionEntity::class, CategoryEntity::class,
        MerchantEntity::class, BudgetEntity::class, RecurringTransactionEntity::class,
        FinancialConnectionEntity::class, SyncHistoryEntity::class, ImportBatchEntity::class,
        TransactionRuleEntity::class, AttachmentEntity::class, AccountBalanceSnapshotEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(FinanceConverters::class)
abstract class VCCFinanceDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    // Um DAO por cada uma das restantes entidades (Sprint 2).
}
```

### `FinanceConverters.kt`

```kotlin
class FinanceConverters {
    @TypeConverter fun accountTypeToString(v: AccountType): String = v.name
    @TypeConverter fun stringToAccountType(v: String): AccountType = AccountType.valueOf(v)
    @TypeConverter fun transactionTypeToString(v: TransactionType): String = v.name
    @TypeConverter fun stringToTransactionType(v: String): TransactionType = TransactionType.valueOf(v)
    @TypeConverter fun sourceToString(v: TransactionSource): String = v.name
    @TypeConverter fun stringToSource(v: String): TransactionSource = TransactionSource.valueOf(v)
    @TypeConverter fun statusToString(v: TransactionStatus): String = v.name
    @TypeConverter fun stringToStatus(v: String): TransactionStatus = TransactionStatus.valueOf(v)
    @TypeConverter fun connectionStatusToString(v: ConnectionStatus): String = v.name
    @TypeConverter fun stringToConnectionStatus(v: String): ConnectionStatus = ConnectionStatus.valueOf(v)
}
```

### `AccountDao.kt`

```kotlin
@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY name")
    fun observeActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Update suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}
```

### `TransactionDao.kt`

```kotlin
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY bookingDate DESC, id DESC")
    fun observeByAccount(accountId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update suspend fun update(transaction: TransactionEntity)

    @Query("""
        SELECT COALESCE(SUM(CASE
            WHEN type = 'INCOME' THEN amountMinor
            WHEN type = 'EXPENSE' THEN -amountMinor
            ELSE 0 END), 0)
        FROM transactions
        WHERE accountId = :accountId AND status = 'BOOKED'
    """)
    fun observeNetMovement(accountId: String): Flow<Long>
}
```

A consulta `observeNetMovement` **não** é o saldo final: exclui transferências e requer evolução para tratar movimentos associados, saldo inicial, ajustes e diferentes moedas. Implementar `AccountBalanceCalculator` com testes de consistência.

### `CategoryDao.kt`

```kotlin
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?
}
```

### `DatabaseModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VCCFinanceDatabase =
        Room.databaseBuilder(context, VCCFinanceDatabase::class.java, "vcc_finance.db").build()

    @Provides fun accountDao(db: VCCFinanceDatabase): AccountDao = db.accountDao()
    @Provides fun transactionDao(db: VCCFinanceDatabase): TransactionDao = db.transactionDao()
    @Provides fun categoryDao(db: VCCFinanceDatabase): CategoryDao = db.categoryDao()
}
```

Não usar `fallbackToDestructiveMigration()`. Exportar schemas Room, versionar migrations e executar testes de migração.

## 7. Repositórios e Use Cases

Fluxo: **Compose → ViewModel → Use Case → Repository → Room**. Importadores e sincronizadores escrevem na mesma fonte de verdade (Room), que expõe `Flow` à UI.

```kotlin
interface AccountRepository {
    fun observeAccounts(): Flow<List<AccountEntity>>
    suspend fun create(account: AccountEntity)
    suspend fun getById(id: String): AccountEntity?
}

class LocalAccountRepository @Inject constructor(
    private val dao: AccountDao
) : AccountRepository {
    override fun observeAccounts() = dao.observeActiveAccounts()
    override suspend fun create(account: AccountEntity) = dao.insert(account)
    override suspend fun getById(id: String) = dao.getById(id)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindAccountRepository(
        implementation: LocalAccountRepository
    ): AccountRepository
}
```

Este contrato inicial usa entidades Room por simplicidade. Na evolução para sincronização, trocar para modelos de domínio e mappers sem expor Room à UI. Use Cases recomendados: `CreateAccount`, `CreateTransaction`, `TransferBetweenAccounts`, `GetAccountBalance`, `ImportStatement`, `GenerateReport`, `ReconcileTransactions`, `SyncAccount`.

Operações multi-registo devem ser atómicas com `RoomDatabase.withTransaction`, especialmente transferências, importações e reconciliações.

## 8. Hilt e Application

### `VCCFinanceApplication.kt`

```kotlin
@HiltAndroidApp
class VCCFinanceApplication : Application()
```

### `AndroidManifest.xml` — fragmento

```xml
<application
    android:name=".VCCFinanceApplication"
    android:allowBackup="false"
    android:theme="@style/Theme.VCCFinanceApp">
    <activity android:name=".MainActivity" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

O tema XML da Activity tem de existir nos recursos Android, mesmo com UI Compose. Rever política de backup ao implementar backups encriptados.

## 9. Navigation Compose e ecrãs

### Navegação principal

Cinco separadores: **Início**, **Contas**, **Movimentos**, **Relatórios**, **Mais**. Ecrãs adicionais: onboarding, PIN, desbloqueio biométrico, detalhe de conta, criar/editar movimento, importação, orçamento, cartão de refeição, sincronização, exportação e definições.

### Rotas tipadas

```kotlin
@Serializable object DashboardRoute
@Serializable object AccountsRoute
@Serializable object TransactionsRoute
@Serializable object ReportsRoute
@Serializable object SettingsRoute
```

```kotlin
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = DashboardRoute) {
        composable<DashboardRoute> {
            DashboardScreen(onAccountsClick = { navController.navigate(AccountsRoute) })
        }
        composable<AccountsRoute> { AccountsScreen() }
        composable<TransactionsRoute> { TransactionsScreen() }
        composable<ReportsRoute> { ReportsScreen() }
        composable<SettingsRoute> { SettingsScreen() }
    }
}
```

**Segurança da navegação:** apresentar primeiro onboarding/bloqueio conforme o `SessionState`; impedir acesso aos destinos financeiros enquanto bloqueado. O exemplo acima ilustra apenas as rotas principais.

### ViewModel de contas

```kotlin
data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    repository: AccountRepository
) : ViewModel() {
    val uiState: StateFlow<AccountsUiState> = repository.observeAccounts()
        .map { AccountsUiState(isLoading = false, accounts = it) }
        .catch { emit(AccountsUiState(isLoading = false, error = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsUiState())
}
```

```kotlin
@Composable
fun AccountsScreen(viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        state.isLoading -> CircularProgressIndicator()
        state.error != null -> Text("Erro ao carregar contas")
        else -> LazyColumn {
            items(state.accounts, key = { it.id }) { account ->
                ListItem(headlineContent = { Text(account.name) },
                         supportingContent = { Text(account.type.name) })
            }
        }
    }
}
```

Importar `collectAsStateWithLifecycle` de Lifecycle Compose; assegurar as dependências correspondentes no módulo.

## 10. Autenticação, PIN e biometria

Componentes: `PinManager`, `BiometricAuthenticator`, `SessionManager` e `SecurityPreferences`.

```kotlin
interface PinManager {
    suspend fun isPinConfigured(): Boolean
    suspend fun configurePin(pin: CharArray)
    suspend fun verifyPin(pin: CharArray): Boolean
    suspend fun changePin(currentPin: CharArray, newPin: CharArray): Boolean
}

sealed interface SessionState {
    data object SetupRequired : SessionState
    data object Locked : SessionState
    data object Unlocked : SessionState
}
```

### Exemplo de BiometricPrompt

```kotlin
val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Desbloquear VCCFinanceApp")
    .setSubtitle("Confirma a tua identidade")
    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    .setNegativeButtonText("Usar PIN")
    .build()
biometricPrompt.authenticate(promptInfo)
```

`biometricPrompt` deve ser inicializado numa Activity/Fragment compatível e ter callback de sucesso/erro. O PIN da app é um mecanismo separado da credencial do dispositivo. Guardar apenas verificador derivado por KDF apropriada com salt aleatório; limitar tentativas; não registar PIN em logs. Usar Keystore para proteger chaves criptográficas; a existência do Keystore **não** encripta automaticamente o ficheiro SQLite. Escolher e testar solução de encriptação em repouso. Bloquear após inatividade e proteger ecrã de aplicações recentes (`FLAG_SECURE` onde apropriado). Proteger exportações e backups com reautenticação.

Fluxo: iniciar → verificar configuração de PIN → configurar ou desbloquear → biometria ou PIN → `Unlocked` → dashboard.

## 11. Importação de movimentos

Prioridades: CSV e XLSX primeiro; OFX, QIF e JSON depois; PDF só com validação reforçada.

Fluxo:

```text
Selecionar ficheiro (SAF)
  → detetar formato
  → interpretar colunas e registos
  → normalizar datas/valores/descrições
  → mapear conta e categorias
  → detetar duplicados
  → pré-visualizar diferenças
  → confirmar
  → gravar numa transação Room
  → atualizar relatórios
```

Mapeamento exemplo: `Data Movimento → bookingDate`, `Descritivo → description`, `Débito → EXPENSE`, `Crédito → INCOME`, `Referência → externalId`.

Suportar `dd/MM/yyyy`, vírgula decimal, separador de milhares e diferentes codificações CSV. Guardar perfis de importação por banco. Deduplicação em duas etapas: IDs externos quando existam e heurística por data, valor, conta e descrição para revisão do utilizador. Não eliminar automaticamente movimentos semelhantes.

## 12. Sincronização automática / Open Banking

```text
VCCFinanceApp Android
  ↕ API autenticada
Backend próprio (ASP.NET Core ou Ktor)
  ↕ credenciais e consentimento
Fornecedor Open Banking autorizado
  ↕
Banco / instituição financeira
```

O backend gere credenciais do fornecedor, consentimentos, expiração, autenticação, normalização e paginação. A app sincroniza resultados para Room e mantém a UI funcional offline. Não incorporar segredos do fornecedor no APK.

Endpoints sugeridos:

```http
GET    /api/v1/connections
POST   /api/v1/connections
GET    /api/v1/connections/{id}/authorization
POST   /api/v1/connections/{id}/sync
GET    /api/v1/accounts
GET    /api/v1/accounts/{id}/transactions
DELETE /api/v1/connections/{id}
```

Contrato de adaptador:

```kotlin
interface FinancialProviderAdapter {
    suspend fun getAccounts(): List<RemoteAccount>
    suspend fun getBalances(accountId: String): List<RemoteBalance>
    suspend fun getTransactions(
        accountId: String,
        from: Instant,
        to: Instant
    ): List<RemoteTransaction>
}
```

`RemoteAccount`, `RemoteBalance` e `RemoteTransaction` são DTOs a definir na implementação. Usar WorkManager para sincronização periódica sujeita a restrições do Android; não prometer horários exatos. O suporte a cartões de refeição (Edenred, Pluxee, Coverflex, etc.) depende de APIs e permissões efetivamente disponibilizadas: prever importação/manual como fallback.

## 13. Relatórios, gráficos e exportações

Relatórios: mensal, anual, por categoria, conta, cartão de refeição, estabelecimento, orçamento, recorrências e evolução do património.

```kotlin
data class MonthlyFinancialReport(
    val year: Int,
    val month: Int,
    val currency: String,
    val totalIncomeMinor: Long,
    val totalExpensesMinor: Long,
    val balanceMinor: Long,
    val expensesByCategory: List<CategoryTotal>,
    val transactions: List<Transaction>
)
```

`CategoryTotal` e `Transaction` são modelos de domínio a criar. Fluxo: `ReportViewModel → GenerateReportUseCase → ReportRepository → ReportData → Chart/PdfExporter/ExcelExporter/CsvExporter/JsonExporter`. Separar a geração dos dados do formato de saída.

Exportações: CSV para interoperabilidade, XLSX com folhas `Movimentos`, `Contas`, `Categorias`, `Resumo`, PDF com gráficos e tabelas, JSON para backup estruturado. Usar Android Storage Access Framework para escolher destino e partilhar ficheiros. Atenção a valores sensíveis, passwords dos backups, permissões e retenção de ficheiros temporários.

## 14. Testes essenciais

| Cenário | Critério de aceitação |
|---|---|
| Importar mesmo extrato duas vezes | Não duplica movimentos |
| Transferência interna | Não altera despesa/receita global |
| Valor português `1.234,56` | Guarda 123456 cêntimos |
| Perda de Internet | Consultas e movimentos locais continuam disponíveis |
| Consentimento expirado | Estado `EXPIRED` e pedido de renovação |
| Movimento manual semelhante a bancário | Sugere reconciliação, não apaga automaticamente |
| PIN errado repetidamente | Aplica bloqueio progressivo/limite |
| Aplicação em segundo plano | Bloqueia conforme política configurada |
| Restaurar backup | Preserva integridade e evita duplicados |
| Upgrade da base de dados | Migration preserva dados |

Adicionar testes unitários (cálculo financeiro e regras), testes de Room em dispositivo/emulador, testes de UI Compose e testes de integração de importação.

## 15. Plano de implementação por sprints

### Sprint 1 — Fundação
- [ ] Criar projeto **VCCFinanceApp** no Android Studio.
- [ ] Definir package `pt.vcc.financeapp` e Gradle Wrapper.
- [ ] Confirmar a matriz de versões fixada (§4) com `./gradlew assembleDebug`.
- [ ] Criar os sete módulos Gradle.
- [ ] Configurar Compose, Material 3, Hilt e Navigation.
- [ ] Compilar e executar no emulador.

### Sprint 2 — Persistência
- [ ] Criar as entidades das 12 tabelas da v1 (§5).
- [ ] Criar TypeConverters e DAOs.
- [ ] Configurar `VCCFinanceDatabase` e injeção Hilt.
- [ ] Exportar schema e testar migrations.
- [ ] Criar repositórios e mappers de domínio.

### Sprint 3 — Ecrãs e lógica financeira
- [ ] Dashboard, Contas, Movimentos e Definições.
- [ ] Criar/editar/desativar contas.
- [ ] Criar receitas e despesas.
- [ ] Implementar transferências internas atómicas.
- [ ] Validar cálculo de saldos.

### Sprint 4 — Segurança
- [ ] Onboarding e configuração de PIN.
- [ ] Verificação segura e limite de tentativas.
- [ ] BiometricPrompt e SessionManager.
- [ ] Encriptação de dados sensíveis e política de bloqueio.
- [ ] Testar cenários de recuperação.

### Sprint 5 — Importações
- [ ] CSV e XLSX.
- [ ] Mapeamento de colunas e perfis por banco.
- [ ] Pré-visualização, validação e deduplicação.
- [ ] Histórico de importações e reconciliação.

### Sprint 6 — Relatórios e backup
- [ ] Dashboard com gráficos.
- [ ] Relatórios mensais, anuais e por categoria.
- [ ] Orçamentos e alertas.
- [ ] Exportação CSV, XLSX, PDF e JSON.
- [ ] Backup e restauro encriptados.

### Sprint 7+ — Sincronização e publicação
- [ ] Confirmar fornecedor Open Banking e condições de acesso.
- [ ] Implementar backend autenticado.
- [ ] Consentimento e renovação de ligações.
- [ ] Sincronização WorkManager e reconciliação.
- [ ] Rever privacidade, política de dados e requisitos Google Play.
- [ ] Preparar autenticação multiutilizador e isolamento de dados no backend.

## 16. Preparação para futura publicação

Desde o início: UUIDs, migrations, isolamento por camadas, interfaces de integração, histórico de importações/sincronização, backup seguro, minimização de dados, testes automatizados, CI/CD e documentação. Não introduzir prematuramente Firebase Authentication, MongoDB, Redis ou infraestrutura cloud; a primeira versão não depende deles. Para publicação, avaliar políticas Google Play, proteção de dados, consentimentos e requisitos regulatórios aplicáveis.

## 17. Entregável da primeira milestone

A primeira milestone deve produzir um **projeto Android Studio compilável** com os sete módulos, Gradle validado, Hilt, Room, Navigation Compose, dashboard simples, lista/criação de contas e testes básicos. Os blocos de código deste documento são **esqueletos de arquitetura**, não um projeto integral já compilado: faltam imports, `build.gradle.kts` de cada biblioteca, recursos, DTOs, modelos de domínio, algumas implementações de segurança e testes.

**Próxima ação recomendada:** gerar o projeto Android Studio completo de base, abrir no Android Studio, executar `./gradlew assembleDebug` e corrigir qualquer incompatibilidade antes de implementar funcionalidades financeiras adicionais.

## 18. Registo de decisões

| Data | Decisão |
|---|---|
| 19/10/2026 | Nome da app: **VCCFinanceApp**; classe `Application`: `VCCFinanceApplication`. |
| 19/10/2026 | Todas as 12 tabelas de §5 entram na versão 1 do schema Room. |
| 19/10/2026 | Matriz de versões fixada a partir do ambiente local (§4); Kotlin/KSP/Hilt a confirmar no primeiro build. |
