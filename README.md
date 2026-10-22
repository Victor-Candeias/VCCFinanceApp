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
| Persistência | Room / SQLite cifrado com SQLCipher |
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
| JDK do Gradle | 17 (Temurin 17.0.20.1, `%USERPROFILE%\.jdks\jdk-17.0.20.1+1`) | Instalado; fixado em `gradle/gradle-daemon-jvm.properties` |
| Gradle Wrapper | 9.4.1 | Confirmado |
| AGP | 9.2.1 | Confirmado |
| `compileSdk` / `targetSdk` | 37 | Confirmado (plataforma `android-37.0`) |
| `minSdk` | 26 | — |
| SDK Build Tools | 36.0.0 | Instalado |
| Kotlin (KGP e plugin Compose) | 2.4.20 | Confirmado |
| KSP | 2.3.12 | Confirmado |
| Hilt | 2.60.1 | Confirmado |
| Compose BOM | 2026.09.00 | Confirmado |
| Room | 2.8.5 | Confirmado |
| Navigation Compose | 2.10.2 | Confirmado |
| Activity Compose | 1.13.0 | Confirmado |
| Lifecycle | 2.10.0 | Confirmado |
| Hilt Navigation Compose | 1.4.0 | Confirmado |
| DataStore | 1.2.1 | Confirmado |
| Biometric | 1.1.0 | Confirmado |
| Complementares: Core KTX 1.19.1, Coroutines 1.11.0, kotlinx-serialization-json 1.11.0, JUnit 4.13.2, AndroidX Test JUnit 1.3.0, Espresso 3.7.0 | — | Confirmado |

> **Notas:** o Android Studio usa por omissão a JBR 25 (`JAVA_HOME`); o ficheiro `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=17`) obriga o daemon Gradle a usar um JDK 17 instalado localmente, mesmo que o launcher arranque com outra JVM. Em alternativa, configurar o **Gradle JDK = 17** (Settings → Build Tools → Gradle).
>
> **Confirmação (S1-03, 19/10/2026):** `./gradlew :app:assembleDebug :app:testDebugUnitTest` passou com todas as bibliotecas do catálogo aplicadas em conjunto (KSP a gerar componentes Hilt, `@HiltViewModel` e `RoomDatabase_Impl`; rotas tipadas com Kotlin Serialization; DataStore; Biometric). A combinação Kotlin 2.4.20 + KSP 2.3.12 + Hilt 2.60.1 fica comprovada; não foi necessário ajustar versões. A fonte de verdade das versões é `gradle/libs.versions.toml`.

### Compilar e executar

```powershell
./gradlew assembleDebug testDebugUnitTest :app:lintDebug   # compilar, testes unitários e lint
./gradlew :app:installDebug                                # instalar no emulador/dispositivo ligado
adb shell am start -n pt.vcc.financeapp/.MainActivity      # abrir a app
./gradlew :core:database:connectedDebugAndroidTest         # testes Room (requer emulador/dispositivo ligado)
```

Validado no Sprint 1 (S1-06) no AVD `Pixel_10a` (Android 17, API 37): arranque sem erros, navegação entre os cinco separadores e temas claro/escuro.

### APK de release assinado

`./gradlew :app:assembleRelease` gera `app/build/outputs/apk/release/VCCFinanceApp-<versionName>-<versionCode>-release.apk`. A versão está em `appVersionCode`/`appVersionName` no topo de `app/build.gradle.kts` (incrementar `versionCode` em cada release).

As credenciais de assinatura nunca entram no git. O Gradle lê a primeira que existir de:

1. `keystore.properties` na raiz do repositório (gitignored);
2. `~/.keystores/vcc-finance-release.properties`.

Chaves esperadas: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Sem ficheiro, o APK de release sai sem assinatura. Fazer backup da keystore e do `.properties` — sem eles não é possível atualizar a app instalada. Verificar com `apksigner verify --print-certs <apk>`.

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

Enums auxiliares das restantes tabelas (em `:core:model`, `FinanceEnums.kt`): `BudgetPeriod`, `RecurrenceFrequency`, `SyncStatus`, `ImportFormat`, `ImportStatus`, `RuleMatchField`, `RuleMatchType` e `BalanceSource`. Os nomes das constantes são persistidos em texto: renomear exige migration.

**Implementação (S2-01):** as entidades vivem em `:core:database` (`entity/`). Para além dos esqueletos abaixo, a v1 inclui: chaves estrangeiras com índice em todas as colunas de referência (`RESTRICT` para movimentos de uma conta, `SET_NULL` para categorias/estabelecimentos/lotes opcionais, `CASCADE` para metadados dependentes); `transactions.importBatchId` para ligar cada movimento ao lote de importação; `merchants.normalizedName` único; `import_batches.fileSha256` para detetar reimportação do mesmo ficheiro; e `recurring_transactions.transferAccountId` para recorrências de transferência.

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

**Implementação (S3-05):** o saldo vem de `AccountBalanceCalculator` (`:core:model`): saldo inicial + receitas − despesas `BOOKED` da conta, incluindo as pernas de transferências internas; os pendentes ficam num valor separado (`pending`/`projected`), os cancelados e o tipo legado `TRANSFER` não contam, e movimentos noutra moeda são ignorados e contados (`ignoredForeignMovements`), nunca convertidos. `TransactionDao.observeBalanceMovements` agrega por conta/tipo/estado/moeda numa só consulta e `ObserveAccountBalancesUseCase` junta-a às contas. Contas mostra o saldo de cada conta (e os pendentes) e o saldo total por moeda das contas ativas; Início mostra o mesmo total. Testes de consistência: resultado agregado = resultado movimento a movimento, transferências não alteram o total, moedas nunca se misturam, e a agregação SQL é verificada em `VCCFinanceDatabaseTest`.

### `CategoryDao.kt`

```kotlin
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?
}
```

**Upsert vs. REPLACE:** usar `@Upsert` (UPDATE in-place) e não `OnConflictStrategy.REPLACE`, que apaga e reinsere a linha e dispara `SET_NULL`/`CASCADE` nas tabelas filhas (ex.: perderia a categoria dos movimentos). Existe um DAO por entidade em `core/database/.../dao/` e os conversores de todos os enums em `converters/FinanceConverters.kt` (S2-02).

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

**Encriptação em repouso (S4-04):** o `DatabaseModule` real usa `openHelperFactory(EncryptedOpenHelperFactory(...))`: a base de dados é aberta com SQLCipher (`net.zetetic:sqlcipher-android`) usando uma chave aleatória de 256 bits no formato raw. Uma base de dados em claro de versões anteriores é convertida uma única vez com `sqlcipher_export` (mantendo `user_version`) na primeira abertura, fora da main thread.

**Schema e migrations (S2-04):** o plugin Gradle `androidx.room` exporta o schema para `core/database/schemas/` (versionado em git e exposto como asset do `androidTest`). Ao subir `VCCFinanceDatabase.VERSION`: compilar para gerar o novo `<versão>.json`, acrescentar a `Migration` a `VCCFinanceMigrations.ALL` (usado pelo `DatabaseModule`) e cobrir os dados migrados em `VCCFinanceMigrationTest`, que cria a base de dados na versão 1 com `MigrationTestHelper` e a abre com o Room atual — falha se faltar uma migration ou se o schema resultante divergir das entidades.

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

O esqueleto acima usa entidades Room por simplicidade. **Implementação (S2-05):** os contratos em `:core:data/repository` expõem modelos de domínio de `:core:model` (`Account`, `Transaction`, `Category`, …, com `Money` e `Instant`); os mappers `asDomain()`/`asEntity()` em `:core:data/mapper` cobrem as 12 entidades; `LocalAccountRepository`, `LocalTransactionRepository` e `LocalCategoryRepository` são `internal` e ligados por `di/RepositoryModule`, pelo que a UI nunca vê Room. Os repositórios das restantes tabelas (orçamentos, importações, ligações, regras, anexos, recorrências, saldos comunicados) são criados nos sprints que os usam. Use Cases recomendados: `CreateAccount`, `CreateTransaction`, `TransferBetweenAccounts`, `GetAccountBalance`, `ImportStatement`, `GenerateReport`, `ReconcileTransactions`, `SyncAccount`.

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

**Implementação (S3-01):** cada separador tem um `@HiltViewModel` que combina os `Flow` dos repositórios (`DashboardViewModel`, `AccountsViewModel`, `TransactionsViewModel`). Início mostra o resumo do mês corrente (receitas, despesas e saldo do período por moeda, via `PeriodTotals.byCurrency` em `:core:model`) e os últimos movimentos; Movimentos lista o mês selecionado (navegação mês a mês, limites calculados no fuso do `Clock` injetado); Mais lista as áreas de definições (as ainda não implementadas aparecem desativadas) e a versão. Valores formatados em pt-PT sem `Float`/`Double` (`feature/finance/ui/Formatting.kt`).

**Implementação (S3-02):** Contas tem um botão para criar conta e uma secção de contas inativas; tocar numa conta abre o editor (`AccountEditorRoute(accountId: String?)`). A validação vive em `SaveAccountUseCase` (`:core:data/usecase`): nome obrigatório (máx. 60 caracteres), moeda ISO 4217 de 3 letras e moeda bloqueada quando a conta já tem movimentos. O saldo inicial pode ser negativo e é lido com `AmountParser` (`:core:model`), que aceita o formato português (`1.234,56`) sem `Float`/`Double`. As contas nunca são apagadas: desativar/reativar pede confirmação e preserva os movimentos. Os fakes dos repositórios são partilhados entre módulos via `testFixtures` de `:core:data`.

**Implementação (S3-03):** Movimentos tem um botão para criar receita/despesa e tocar num movimento abre o editor (`TransactionEditorRoute(transactionId: String?)`), com conta, tipo, valor, descrição, data, categoria (filtrada pelo tipo, subcategorias como `Alimentação › Café`), estado pendente e notas; apagar pede confirmação. `SaveTransactionUseCase` valida conta ativa, valor positivo, descrição (máx. 140), categoria compatível com o tipo e usa sempre a moeda da conta; rejeita `TRANSFER` e pernas de transferência (tratadas em conjunto no S3-04). Datas escolhidas sem hora ficam ao meio-dia local, para não mudarem de dia com variações de fuso; ao editar sem mudar a data mantém-se o instante original. As categorias iniciais (§5) são semeadas no arranque por `SeedSystemCategoriesUseCase`, com IDs estáveis `system-*` e `INSERT … IGNORE`, sem sobrepor alterações do utilizador.

**Implementação (S3-04):** uma transferência interna são dois movimentos com o mesmo `transferGroupId`: `EXPENSE` na conta de origem e `INCOME` na de destino, com o mesmo valor, moeda, data e estado. `SaveTransferUseCase` valida contas diferentes, ativas e na mesma moeda, valor positivo e descrição; as pernas são gravadas, editadas e apagadas numa única transação Room (`insertAtomically`/`updateAtomically`/`deleteTransferGroup`). As pernas contam para o saldo de cada conta e ficam fora dos totais de receitas/despesas (`Transaction.isInternalTransfer`). Em Movimentos, o botão de adicionar abre um menu (receita/despesa ou transferência); tocar numa perna abre `TransferEditorRoute(transferGroupId)` e a lista mostra "Transferência para/de <conta>".

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

**Implementação (S4-01):** módulo `:core:security`. `PinPolicy` aceita 4 a 8 algarismos e rejeita PINs triviais (todos iguais ou sequências como 1234/9876). O verificador guardado é `HMAC(PBKDF2-HMAC-SHA256(pin, salt 16 B, 210 000 iterações))`, onde o HMAC usa uma chave não exportável do Android Keystore (`KeystorePinPepper`), para que uma cópia do verificador não permita ataques de força bruta fora do dispositivo. Salt, verificador (Base64) e iterações ficam num DataStore próprio (`security`); o PIN só existe em `CharArray` limpos depois de usados. `SessionManager` expõe `SessionState` (`INITIALIZING`/`SETUP_REQUIRED`/`LOCKED`/`UNLOCKED`) e `AuthenticationGate` só compõe a navegação da app em `UNLOCKED`; antes mostra o onboarding (boas-vindas → criar PIN → confirmar) ou o ecrã de desbloqueio. `isPinConfigured` é um `Flow<Boolean>`.

**Implementação (S4-02):** `verifyPin` devolve `PinVerification` (`Success` / `Wrong(remainingAttempts)` / `LockedOut(until)`) e `changePin` devolve o mesmo resultado. As tentativas falhadas são persistidas antes da derivação (fechar a app a meio não dá tentativas grátis) e só voltam a zero com o PIN certo: 5 tentativas livres; depois, cada falha bloqueia por 30 s, duplicando a cada falha até 1 h. Durante o bloqueio nenhum PIN é verificado e o ecrã de desbloqueio mostra uma contagem decrescente, mantida depois de reiniciar a app. `Mais → Segurança → Alterar PIN` pede o PIN atual (sujeito ao mesmo limite; ao atingir o bloqueio a sessão é bloqueada), o PIN novo e a confirmação. O bloqueio usa o relógio do sistema; adiantar o relógio encurta-o (limitação aceite numa app local).

**Implementação (S4-03):** `BiometricAuthenticator` usa `BiometricPrompt` (apenas `BIOMETRIC_STRONG`) com um `CryptoObject` sobre uma chave AES-GCM do Keystore com `setUserAuthenticationRequired(true)` e `setInvalidatedByBiometricEnrollment(true)`; o desbloqueio só conta se a cifra autenticada funcionar. Ativar em Mais → Segurança exige uma autenticação biométrica bem-sucedida; se a chave for invalidada (novas biometrias registadas), a opção é desativada e o utilizador entra com o PIN. O prompt aparece automaticamente no ecrã de desbloqueio, com botão "Usar PIN" e "Usar biometria" para o reabrir; fica indisponível durante o bloqueio por tentativas. `AutoLockController` aplica o bloqueio automático configurável (imediatamente, 30 s, 1, 5 ou 15 min; 1 min por omissão): ao voltar do segundo plano bloqueia se passou esse tempo (com "imediatamente" bloqueia logo ao sair) e em primeiro plano bloqueia após esse tempo sem interação (mínimo 30 s). A `MainActivity` passa a `FragmentActivity` e o estado da navegação é restaurado depois de desbloquear (`SaveableStateHolder`), sem manter os dados compostos por baixo do ecrã de bloqueio.

**Implementação (S4-04):** solução de encriptação em repouso: SQLCipher para toda a base de dados (inclui `-wal`), com a chave de 32 bytes gerada por `SecureRandom`, embrulhada por AES-GCM com uma chave não exportável do Keystore (`DatabaseKeyProvider`) e guardada em `no_backup/database.key`; a chave nunca entra no texto SQL (parâmetros ligados), para não aparecer em registos de erro do SQLite. Campos sensíveis têm ainda cifra própria (`FieldCipher`, AES-GCM com outra chave do Keystore, formato `v1:<Base64>`): o IBAN da conta é opcional, validado (ISO 13616, mod 97), guardado em `ibanEncrypted` e mostrado mascarado (`PT50 •••• •••• 0154`) no editor até tocar em "Mostrar". `FLAG_SECURE` na `MainActivity` impede capturas de ecrã e esconde a app nas recentes. A política de bloqueio (tentativas, bloqueio automático e biometria) é a de S4-02/S4-03. Se a chave do Keystore se perder, a base de dados deixa de abrir (`DatabaseKeyUnavailableException`); a recuperação é tratada em S4-05.

**Implementação (S4-05):** cenários de recuperação. Ao arrancar, o `AppRecoveryManager` (`:core:data`) verifica se a chave do pepper do PIN ainda existe (`PinManager.isPinKeyAvailable`) e se a base de dados pode ser aberta (`DatabaseStorage.isReadable`: sem ficheiro, ainda em claro, ou chave guardada decifrável); se não, a sessão entra em `SessionState.RECOVERY_REQUIRED` e mostra um ecrã que explica a perda das chaves e só permite apagar os dados. Uma base de dados cifrada sem `database.key` faz a abertura falhar com `DatabaseKeyUnavailableException` em vez de gerar uma chave nova. `verifyPin` devolve `PinVerification.KeyUnavailable` (sem consumir tentativas) se o pepper desaparecer com a app aberta, o que também leva ao ecrã de recuperação. No desbloqueio, "Esqueci-me do PIN" abre a mesma confirmação: escrever `APAGAR` apaga a base de dados (incluindo `-wal`/`-shm`), o DataStore de segurança e todas as chaves do Keystore (`SecurityReset`), e a app reinicia num processo novo no onboarding. Sem backup (S6-05) os dados não são recuperáveis.

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

**Implementação (S5-01):** leitura de extratos em `core/data/.../importer`, sem bibliotecas externas. O ficheiro é escolhido com o seletor do sistema (`OpenDocument`, sem permissões de armazenamento) e lido pelo `ContentResolver` com limite de 10 MB. O formato é detetado pela assinatura (`PK` → XLSX; contentor OLE do `.xls` antigo/XLSX com palavra-passe e PDF são recusados com mensagem própria) e, para texto, pela extensão. O `CsvParser` deteta a codificação (BOM UTF-8/UTF-16, UTF-8 válido ou Windows-1252) e o separador (`;`, `,`, tabulação ou `|`, pelo número de campos mais frequente nas primeiras linhas, com `;` em caso de empate) e segue a RFC 4180 (aspas, `""` e quebras de linha dentro de campos). O `XlsxParser` lê a primeira folha com ZIP + SAX (strings partilhadas, *inline* e fórmulas, sistema de datas 1900/1904, formatos de data incorporados e personalizados), recusa XML com `DOCTYPE` e limita o conteúdo descomprimido a 50 MB e 50 000 linhas. O resultado é uma `ImportTable` só com texto: os números do Excel saem com vírgula decimal e sem ruído de vírgula flutuante (`-12,3`) e as datas como `yyyy-MM-dd`, para a interpretação ser a mesma do CSV no passo seguinte (S5-02). O ecrã **Mais → Importar extratos** mostra o nome, o formato, o número de linhas e colunas e as primeiras 20 linhas; guarda-se também o SHA-256 do ficheiro para avisar de importações repetidas (S5-03).

**Implementação (S5-02):** o `ColumnMappingDetector` procura a linha de cabeçalho nas primeiras 15 linhas (ignorando linhas de apresentação do banco) por palavras-chave PT/EN normalizadas sem acentos (`data mov`, `data valor`, `descritivo`, `débito`, `crédito`, `montante`, `saldo`, `referência`…), propõe o `ColumnMapping` (data, descrição, valor com sinal **ou** débito/crédito, e opcionalmente data-valor, referência e saldo) e deteta o formato da data nas primeiras linhas (`dd/MM/yyyy`, `dd-MM-yyyy`, `dd.MM.yyyy`, `dd/MM/yy`, `yyyy-MM-dd`, `yyyy/MM/dd`, com validação estrita). Sem cabeçalho, infere as colunas pelo conteúdo. O `ImportRowParser` converte cada linha com o `AmountParser` (ignora códigos de moeda como `EUR`), devolve o valor absoluto com o tipo `EXPENSE`/`INCOME` e marca — sem descartar — as linhas com data inválida, valor inválido ou zero, ou sem descrição. A opção **Inverter sinal** serve para extratos de cartão em que as compras são positivas. Os perfis ficam na tabela `import_profiles` (migração 1 → 2, mapeamento em colunas explícitas) e são reconhecidos pela assinatura do cabeçalho normalizado (`data mov|descritivo|debito|credito`); ao abrir um extrato com o mesmo cabeçalho, o perfil é aplicado automaticamente. No ecrã de importação é possível ajustar cada coluna, ver o resultado (movimentos válidos, primeiras linhas com erro), guardar/atualizar o perfil e apagar perfis.

**Implementação (S5-03):** depois do mapeamento escolhe-se a **conta de destino** (pré-selecionada quando só há uma conta ativa), cujas casas decimais da moeda passam a ser usadas na leitura dos valores. **Rever movimentos** classifica cada linha (`ImportDeduplicator`): cada linha válida recebe um `externalId` estável (`h:` + SHA-256 da data, tipo, valor, descrição normalizada, referência e ordem entre linhas iguais do mesmo ficheiro), pelo que reimportar o mesmo extrato ou um extrato sobreposto marca as linhas como **já importadas**, e estas nunca são gravadas outra vez. Linhas com o mesmo tipo e valor de um movimento existente a ±1 dia (por exemplo, introduzido à mão) ficam como **possível duplicado**, mostram o movimento semelhante e não ficam selecionadas; cada movimento existente só justifica um possível duplicado. As linhas inválidas são listadas com o motivo. Se o mesmo ficheiro (SHA-256) já foi importado nessa conta, aparece um aviso com a data. Ao confirmar, o `ImportBatch` (`COMPLETED`, com totais de importados, duplicados e erros) e os movimentos escolhidos (`CSV_IMPORT`/`EXCEL_IMPORT`, `importBatchId`, data às 12:00 locais) são gravados numa única transação Room (`ImportBatchDao.insertWithTransactions`): se uma linha falhar, nada fica gravado.

**Implementação (S5-04):** quando a coluna de saldo está mapeada, o saldo final do extrato (linha do último movimento, com a ordem do ficheiro deduzida pelo encadeamento saldo anterior + valor = saldo e, em último caso, pelas datas) é gravado como `AccountBalanceSnapshot` de origem `IMPORT` ligado ao lote (`importBatchId`, migração 2→3), na mesma transação dos movimentos. O ecrã final compara-o com o saldo calculado pela app no fim desse dia (saldo inicial + receitas − despesas confirmadas) e mostra a diferença com sugestões de verificação. Sem ficheiro escolhido, o ecrã lista as **importações anteriores** (ficheiro, data, conta, contagens); **Reverter**, após confirmação, apaga numa transação os movimentos do lote (mesmo editados, com os anexos), o saldo do extrato e marca o lote como `REVERTED` — o mesmo ficheiro pode depois voltar a ser importado.

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

**Implementação (S6-01):** o Início mostra, abaixo do resumo do mês, um gráfico circular das despesas do mês por categoria principal (subcategorias somadas na principal, cinco maiores e "Outras", cor da categoria) e um gráfico de barras de receitas e despesas dos últimos 6 meses (meses sem movimentos incluídos). As agregações são funções puras em `core:model` (`FinancialAggregations`, `CategoryTotal`, `MonthTotals`) reutilizáveis nos relatórios; contam só movimentos confirmados e excluem transferências internas. Sem conversão de moedas, os gráficos usam a moeda com mais movimentos e avisam quando há outras. Cada gráfico tem descrição completa para leitores de ecrã.

**Implementação (S6-02):** o separador Relatórios gera relatórios mensais e anuais (seletor Mês/Ano e navegação entre períodos) a partir de `FinancialReport.build` (`core/model`): totais de receitas, despesas e saldo do período, despesas e receitas por categoria principal (gráfico circular e lista com percentagens), evolução mensal no relatório anual e totais por conta. Contam só movimentos confirmados, sem transferências internas; não há conversão de moeda, por isso há um relatório por moeda e um seletor quando existem várias. `ReportsViewModel` observa os movimentos do período e resolve os nomes de contas e categorias. A exportação fica para S6-04.

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
| 19/10/2026 | Matriz de versões confirmada com `assembleDebug` (S1-03), sem ajustes. |
| 21/10/2026 | Transferências internas guardadas como duas pernas `EXPENSE`/`INCOME` ligadas por `transferGroupId` (sem usar `TransactionType.TRANSFER` nem migração); só entre contas da mesma moeda. |
| 21/10/2026 | Verificador do PIN = HMAC (chave do Keystore) sobre PBKDF2-HMAC-SHA256 com 210 000 iterações; `SessionState` implementado como enum com `INITIALIZING` (estado antes de ler o DataStore) e `isPinConfigured` como `Flow`. |
| 21/10/2026 | `verifyPin`/`changePin` devolvem `PinVerification` (sealed) em vez de `Boolean`, para a UI distinguir PIN errado (com tentativas restantes) de bloqueio temporário; bloqueio progressivo 5 tentativas → 30 s a duplicar até 1 h. |
| 21/10/2026 | Um único tempo de bloqueio automático configurável para segundo plano e inatividade (em primeiro plano com mínimo de 30 s); a inatividade conta apenas toques na janela da activity (menus e diálogos são janelas próprias). Biometria só `BIOMETRIC_STRONG` com `CryptoObject`, indisponível durante o bloqueio por tentativas e sem repor o contador de tentativas do PIN; chave invalidada com novas biometrias desativa a opção. |
| 21/10/2026 | Encriptação em repouso com SQLCipher em vez de depender só da FBE do Android (opcional em Android 8/9, `minSdk` 26): a base de dados inteira fica cifrada com uma chave aleatória protegida pelo Keystore, à custa de ~2 MB por ABI no APK. O IBAN tem também cifra de campo; `FLAG_SECURE` aplicado sempre, por ser uma app de dados financeiros. |
| 21/10/2026 | Recuperação: PIN esquecido ou chaves do Keystore perdidas só se resolvem apagando todos os dados locais (não há PIN mestre nem chave de recuperação, para não enfraquecer a cifra), com confirmação escrita `APAGAR` e reinício do processo. Nunca se gera uma chave nova por cima de uma base de dados cifrada existente, para não destruir uma hipótese de recuperação. |
| 21/10/2026 | Importação CSV/XLSX implementada sem bibliotecas (Apache POI ou semelhantes acrescentariam vários MB e dependências com historial de vulnerabilidades): XLSX lido com ZIP + SAX limitado à primeira folha, `.xls` (Excel 97-2003) não suportado — o utilizador deve guardar como `.xlsx` ou CSV. |
| 21/10/2026 | Perfis de importação reconhecidos pelo cabeçalho normalizado (sem acentos/pontuação) e guardados com o mapeamento em colunas explícitas da tabela `import_profiles` em vez de JSON, para as migrações futuras serem SQL simples. Extratos sem cabeçalho não são reconhecidos automaticamente (o perfil aplica-se manualmente). |
| 22/10/2026 | Deduplicação de importações: duplicados exatos por `externalId` sintético (hash da linha normalizada mais a ordem entre linhas iguais), comparado na conta independentemente da origem CSV/XLSX; possíveis duplicados só por tipo, valor e data a ±1 dia, porque os movimentos manuais raramente têm a mesma descrição do banco. Os possíveis duplicados ficam por importar até o utilizador os marcar. |
| 22/10/2026 | Reverter uma importação apaga todos os movimentos do lote, mesmo os editados depois (a alternativa de manter os editados deixaria saldos inconsistentes com o extrato), e o saldo do extrato; o lote fica no histórico como `REVERTED`. A reconciliação compara apenas o saldo final do extrato com o calculado no fim desse dia, sem ajustar nada automaticamente. |
| 22/10/2026 | Gráficos desenhados com `Canvas` do Compose em vez de Vico: só são precisos gráficos circulares e de barras simples, evita-se uma dependência e o mesmo desenho pode ser replicado no PDF das exportações. Valores em unidades mínimas (`Long`) até ao desenho, percentagens com aritmética inteira. |
