# Foundation & Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the CoinTrail Android project skeleton and a fully tested local data layer (Money type, domain models, Room schema v1, repositories, preset seeding) that every later plan builds on.

**Architecture:** Single `:app` module. Pure-Kotlin domain models (with validation) sit above Room entities/DAOs; repositories map between them and expose Flow-based queries. Money is an integer-paisa value class — floats are forbidden. All timestamps are `java.time` values persisted as ISO strings via Room type converters.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, Jetpack Compose (BOM 2024.12.01), Room 2.6.1 + KSP 2.0.21-1.0.28, kotlinx-coroutines 1.9.0, JUnit 4.13.2, Robolectric 4.14.1. (Newer stable versions of the same artifacts are safe to bump if the environment requires it — do not switch libraries.)

**Spec:** `docs/SPEC.md` (read sections 3–5 before starting).

## Global Constraints

- minSdk 26, compileSdk/targetSdk 35, JVM target 17.
- Package / applicationId / namespace: `com.cointrail` (exact).
- Money is integer paisa (`Long`) everywhere. Floating point for money is forbidden.
- Single currency BDT (৳). Display: `৳1,250` whole taka unless paisa non-zero → `৳1,250.50`. English UI only.
- Dates/times: `java.time` (`LocalDateTime`, `YearMonth`), device-local timezone; persisted as ISO strings.
- IDs: `UUID.randomUUID().toString()` for user-created rows; presets use stable string IDs (`preset-*`, `pm-*`) exactly as listed in spec §6.8.
- Tombstones: `deletedAt` on `expenses`, `budgets`, `recurring_series`. `categories` and `payment_methods` are hidden (`isHidden`), never deleted.
- Every table carries `updatedAt` (needed by the later sync plan).
- Tests only for money-at-risk logic (this plan's domain + data layer). No UI tests.
- One task = one green test cycle = one commit. Never commit red.

## File Structure

- `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` — build scaffolding, version catalog.
- `app/build.gradle.kts` — app module config (Compose, Room/KSP, Robolectric test options, Room schema export).
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml` — manifest + minimal theme (Compose owns UI).
- `app/src/main/java/com/cointrail/MainActivity.kt` — placeholder entry point (real UI is Plan 2).
- `app/src/main/java/com/cointrail/core/Money.kt` — money value type: arithmetic, formatting, parsing.
- `app/src/main/java/com/cointrail/domain/model/Models.kt` — domain models + validation + query projections.
- `app/src/main/java/com/cointrail/data/db/Converters.kt` — `LocalDateTime` ↔ ISO string.
- `app/src/main/java/com/cointrail/data/db/Entities.kt` — five Room entities (schema v1, spec §5).
- `app/src/main/java/com/cointrail/data/db/Daos.kt` — DAOs + SQL projection rows.
- `app/src/main/java/com/cointrail/data/db/CoinTrailDatabase.kt` — Room database + builders.
- `app/src/main/java/com/cointrail/data/SeedData.kt` — preset categories/payment methods (stable IDs).
- `app/src/main/java/com/cointrail/data/repo/Mappers.kt` — entity ↔ domain mappers.
- `app/src/main/java/com/cointrail/data/repo/ExpenseRepository.kt` — expense persistence + totals queries.
- `app/src/main/java/com/cointrail/data/repo/CatalogRepositories.kt` — category, payment method, budget, recurring-series repositories.
- `app/src/test/java/com/cointrail/core/MoneyTest.kt`, `app/src/test/java/com/cointrail/domain/model/ModelsTest.kt` — pure unit tests.
- `app/src/test/java/com/cointrail/data/db/DaoTest.kt`, `app/src/test/java/com/cointrail/data/repo/ExpenseRepositoryTest.kt`, `app/src/test/java/com/cointrail/data/repo/CatalogRepositoriesTest.kt` — Robolectric + in-memory Room tests.

---

### Task 1: Gradle scaffold + placeholder app

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/java/com/cointrail/MainActivity.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: a building `:app` module with package `com.cointrail`, Room + KSP + Compose + Robolectric dependencies wired (consumed by Tasks 3–6).

- [ ] **Step 1: Initialize git and ignore build artifacts**

```bash
cd /Users/md.ahsanhabibsowmik/Documents/projects/CoinTrail
git init
```

Create `.gitignore`:

```gitignore
*.iml
.gradle/
build/
local.properties
.idea/
.DS_Store
captures/
.externalNativeBuild/
.cxx/
```

Note: `app/schemas/` must NOT be ignored — Room schema exports are committed for future migrations.

- [ ] **Step 2: Create the Gradle scaffolding**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CoinTrail"
include(":app")
```

`build.gradle.kts` (root):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
coroutines = "1.9.0"
room = "2.6.1"
junit = "4.13.2"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"
coreKtx = "1.15.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: Create the app module**

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cointrail"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cointrail"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.coroutines.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
```

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="CoinTrail"
        android:supportsRtl="true"
        android:theme="@style/Theme.CoinTrail">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

`app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.CoinTrail" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`app/src/main/java/com/cointrail/MainActivity.kt`:

```kotlin
package com.cointrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("CoinTrail")
                }
            }
        }
    }
}
```

- [ ] **Step 4: Generate the Gradle wrapper and build**

Run (requires a local Gradle installation or Android Studio's Gradle JDK):

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. (First run downloads dependencies; needs network.)

- [ ] **Step 5: Run the unit test task to confirm the harness is wired**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` (no tests exist yet — real tests start in Task 2).

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: scaffold CoinTrail app module" -m "Co-authored-by: CommandCodeBot <noreply@commandcode.ai>"
```

---

### Task 2: Money value type

**Files:**
- Test: `app/src/test/java/com/cointrail/core/MoneyTest.kt`
- Create: `app/src/main/java/com/cointrail/core/Money.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `data class Money(val paisa: Long)` in `com.cointrail.core` with `operator fun plus(other: Money): Money`, `operator fun minus(other: Money): Money`, `Comparable<Money>`, `fun format(withSymbol: Boolean = true): String`, and `companion object { val ZERO: Money; fun fromTaka(input: String): Money? }`. Consumed by every later task and every later plan.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/cointrail/core/MoneyTest.kt`:

```kotlin
package com.cointrail.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `formats whole taka without decimals`() {
        assertEquals("৳1,250", Money(125_000).format())
    }

    @Test
    fun `formats paisa when non-zero`() {
        assertEquals("৳1,250.50", Money(125_050).format())
    }

    @Test
    fun `formats zero and can omit symbol`() {
        assertEquals("৳0", Money.ZERO.format())
        assertEquals("1250", Money(125_000).format(withSymbol = false))
    }

    @Test
    fun `adds and subtracts`() {
        assertEquals(Money(300), Money(100) + Money(200))
        assertEquals(Money(100), Money(300) - Money(200))
    }

    @Test
    fun `compares by paisa`() {
        assertTrue(Money(200) > Money(100))
    }

    @Test
    fun `parses plain taka`() {
        assertEquals(Money(125_000), Money.fromTaka("1250"))
    }

    @Test
    fun `parses symbol separators and decimal paisa`() {
        assertEquals(Money(125_050), Money.fromTaka("৳1,250.5"))
        assertEquals(Money(125_055), Money.fromTaka("1250.55"))
        assertEquals(Money(50), Money.fromTaka("0.5"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(Money.fromTaka(""))
        assertNull(Money.fromTaka("৳"))
        assertNull(Money.fromTaka("12.a"))
        assertNull(Money.fromTaka("1.234"))
        assertNull(Money.fromTaka("12.5.5"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cointrail.core.MoneyTest"`
Expected: FAIL with compilation error `Unresolved reference: Money`.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/cointrail/core/Money.kt`:

```kotlin
package com.cointrail.core

import java.text.NumberFormat
import java.util.Locale

data class Money(val paisa: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(paisa + other.paisa)

    operator fun minus(other: Money): Money = Money(paisa - other.paisa)

    override fun compareTo(other: Money): Int = paisa.compareTo(other.paisa)

    fun format(withSymbol: Boolean = true): String {
        val sign = if (paisa < 0) "-" else ""
        val abs = if (paisa < 0) -paisa else paisa
        val taka = integerFormat.format(abs / 100)
        val remainder = (abs % 100).toInt()
        val number = if (remainder == 0) taka else "$taka.${remainder.toString().padStart(2, '0')}"
        val symbol = if (withSymbol) "৳" else ""
        return "$sign$symbol$number"
    }

    companion object {
        val ZERO: Money = Money(0)

        private val integerFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

        fun fromTaka(input: String): Money? {
            val cleaned = input.trim().replace("৳", "").replace(",", "").replace(" ", "")
            if (cleaned.isEmpty()) return null
            val negative = cleaned.startsWith("-")
            val unsigned = if (negative) cleaned.substring(1) else cleaned
            val parts = unsigned.split(".")
            if (parts.size > 2) return null
            val takaPart = parts[0]
            val paisaPart = if (parts.size == 2) parts[1] else ""
            if (takaPart.isEmpty() || !takaPart.all { it.isDigit() }) return null
            if (paisaPart.length > 2 || !paisaPart.all { it.isDigit() }) return null
            val taka = takaPart.toLongOrNull() ?: return null
            val paisa = paisaPart.padEnd(2, '0').toLong()
            val total = taka * 100 + paisa
            return Money(if (negative) -total else total)
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cointrail.core.MoneyTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cointrail/core/Money.kt app/src/test/java/com/cointrail/core/MoneyTest.kt
git commit -m "feat: add Money value type with paisa math and BDT formatting" -m "Co-authored-by: CommandCodeBot <noreply@commandcode.ai>"
```

---

### Task 3: Domain models with validation

**Files:**
- Test: `app/src/test/java/com/cointrail/domain/model/ModelsTest.kt`
- Create: `app/src/main/java/com/cointrail/domain/model/Models.kt`

**Interfaces:**
- Consumes: `Money` (Task 2).
- Produces (package `com.cointrail.domain.model`), consumed by Tasks 5–6 and all later plans:
  - `Expense(id: String, amount: Money, categoryId: String, note: String?, paymentMethodId: String?, occurredAt: LocalDateTime, createdAt: LocalDateTime, updatedAt: LocalDateTime, deletedAt: LocalDateTime? = null)` with `val isDeleted: Boolean`
  - `Category(id: String, name: String, isPreset: Boolean, isHidden: Boolean, sortOrder: Int, updatedAt: LocalDateTime)`
  - `PaymentMethod(id: String, name: String, isPreset: Boolean, isHidden: Boolean, sortOrder: Int, updatedAt: LocalDateTime)`
  - `Budget(id: String, categoryId: String?, monthlyLimit: Money, updatedAt: LocalDateTime, deletedAt: LocalDateTime? = null)`
  - `RecurringSeries(id: String, amount: Money, categoryId: String, note: String?, paymentMethodId: String?, dayOfMonth: Int, startMonth: YearMonth, lastGeneratedMonth: YearMonth?, isPaused: Boolean, updatedAt: LocalDateTime, deletedAt: LocalDateTime? = null)`
  - `DailyTotal(day: LocalDate, total: Money)`, `CategoryTotal(categoryId: String, total: Money)`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/cointrail/domain/model/ModelsTest.kt`:

```kotlin
package com.cointrail.domain.model

import com.cointrail.core.Money
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

class ModelsTest {

    private val ts: LocalDateTime = LocalDateTime.of(2026, 10, 1, 12, 0)

    private fun expense(amount: Money = Money(100), categoryId: String = "preset-food") = Expense(
        amount = amount,
        categoryId = categoryId,
        note = null,
        paymentMethodId = null,
        occurredAt = ts,
        createdAt = ts,
        updatedAt = ts,
    )

    @Test
    fun `expense rejects non-positive amount`() {
        assertThrows(IllegalArgumentException::class.java) { expense(amount = Money.ZERO) }
    }

    @Test
    fun `expense rejects blank category`() {
        assertThrows(IllegalArgumentException::class.java) { expense(categoryId = " ") }
    }

    @Test
    fun `expense isDeleted reflects tombstone`() {
        assertFalse(expense().isDeleted)
        assertTrue(expense().copy(deletedAt = ts).isDeleted)
    }

    @Test
    fun `category and payment method reject blank names`() {
        assertThrows(IllegalArgumentException::class.java) {
            Category(id = "c", name = "", isPreset = false, isHidden = false, sortOrder = 0, updatedAt = ts)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaymentMethod(id = "p", name = " ", isPreset = false, isHidden = false, sortOrder = 0, updatedAt = ts)
        }
    }

    @Test
    fun `budget rejects non-positive limit`() {
        assertThrows(IllegalArgumentException::class.java) {
            Budget(id = "b", categoryId = null, monthlyLimit = Money.ZERO, updatedAt = ts)
        }
    }

    @Test
    fun `recurring series rejects invalid day and amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurringSeries(
                id = "r", amount = Money(100), categoryId = "preset-rent", note = null,
                paymentMethodId = null, dayOfMonth = 32, startMonth = YearMonth.of(2026, 10),
                lastGeneratedMonth = null, isPaused = false, updatedAt = ts,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecurringSeries(
                id = "r", amount = Money.ZERO, categoryId = "preset-rent", note = null,
                paymentMethodId = null, dayOfMonth = 5, startMonth = YearMonth.of(2026, 10),
                lastGeneratedMonth = null, isPaused = false, updatedAt = ts,
            )
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cointrail.domain.model.ModelsTest"`
Expected: FAIL with compilation error `Unresolved reference: Expense` (and friends).

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/cointrail/domain/model/Models.kt`:

```kotlin
package com.cointrail.domain.model

import com.cointrail.core.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val amount: Money,
    val categoryId: String,
    val note: String? = null,
    val paymentMethodId: String? = null,
    val occurredAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
) {
    init {
        require(amount > Money.ZERO) { "Expense amount must be positive" }
        require(categoryId.isNotBlank()) { "Expense must have a category" }
    }

    val isDeleted: Boolean get() = deletedAt != null
}

data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isPreset: Boolean = false,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: LocalDateTime,
) {
    init {
        require(name.isNotBlank()) { "Category name must not be blank" }
    }
}

data class PaymentMethod(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isPreset: Boolean = false,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: LocalDateTime,
) {
    init {
        require(name.isNotBlank()) { "Payment method name must not be blank" }
    }
}

data class Budget(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String?,
    val monthlyLimit: Money,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
) {
    init {
        require(monthlyLimit > Money.ZERO) { "Budget limit must be positive" }
    }
}

data class RecurringSeries(
    val id: String = UUID.randomUUID().toString(),
    val amount: Money,
    val categoryId: String,
    val note: String? = null,
    val paymentMethodId: String? = null,
    val dayOfMonth: Int,
    val startMonth: YearMonth,
    val lastGeneratedMonth: YearMonth? = null,
    val isPaused: Boolean = false,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
) {
    init {
        require(amount > Money.ZERO) { "Recurring amount must be positive" }
        require(categoryId.isNotBlank()) { "Recurring series must have a category" }
        require(dayOfMonth in 1..31) { "dayOfMonth must be in 1..31" }
    }
}

data class DailyTotal(val day: LocalDate, val total: Money)

data class CategoryTotal(val categoryId: String, val total: Money)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cointrail.domain.model.ModelsTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cointrail/domain/model/Models.kt app/src/test/java/com/cointrail/domain/model/ModelsTest.kt
git commit -m "feat: add validated domain models" -m "Co-authored-by: CommandCodeBot <noreply@commandcode.ai>"
```

---

### Task 4: Room schema v1 — entities, converters, DAOs, database

**Files:**
- Test: `app/src/test/java/com/cointrail/data/db/DaoTest.kt`
- Create: `app/src/main/java/com/cointrail/data/db/Converters.kt`, `app/src/main/java/com/cointrail/data/db/Entities.kt`, `app/src/main/java/com/cointrail/data/db/Daos.kt`, `app/src/main/java/com/cointrail/data/db/CoinTrailDatabase.kt`

**Interfaces:**
- Consumes: Room deps + KSP from Task 1's `app/build.gradle.kts` (already configured with `room.schemaLocation`).
- Produces (package `com.cointrail.data.db`), consumed by Tasks 5–6 and the sync/backup plan:
  - Entities `ExpenseEntity`, `CategoryEntity`, `PaymentMethodEntity`, `BudgetEntity` (with `companion object { const val OVERALL = "__overall__" }`), `RecurringSeriesEntity` — all fields as in spec §5.
  - `ExpenseDao`: `suspend fun upsertAll(expenses: List<ExpenseEntity>)`, `suspend fun upsert(expense: ExpenseEntity)`, `suspend fun byId(id: String): ExpenseEntity?`, `fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<ExpenseEntity>>`, `fun observeTotalBetween(from: LocalDateTime, to: LocalDateTime): Flow<Long>`, `fun observeDailyTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<DailyTotalRow>>`, `fun observeCategoryTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<CategoryTotalRow>>`, `suspend fun changesSince(since: LocalDateTime): List<ExpenseEntity>`, `suspend fun softDelete(id: String, now: LocalDateTime)`
  - `CategoryDao` / `PaymentMethodDao`: `suspend fun upsertAll(...)`, `suspend fun upsert(...)`, `fun observeAll(): Flow<List<CategoryEntity>>` (ordered by `sortOrder`, `name`), `suspend fun byId(id: String)`, `suspend fun count(): Int`, `suspend fun changesSince(since: LocalDateTime)`
  - `BudgetDao`: `suspend fun upsert(budget: BudgetEntity)`, `suspend fun byScopeKey(scopeKey: String): BudgetEntity?`, `fun observeAll(): Flow<List<BudgetEntity>>` (non-deleted), `suspend fun softDelete(id: String, now: LocalDateTime)`, `suspend fun changesSince(since: LocalDateTime)`
  - `RecurringSeriesDao`: same shape as `BudgetDao` plus `suspend fun byId(id: String): RecurringSeriesEntity?` (scoped by `scopeKey` replaced with id).
  - Projection rows `DailyTotalRow(day: String, totalPaisa: Long)`, `CategoryTotalRow(categoryId: String, totalPaisa: Long)`.
  - `CoinTrailDatabase.create(context: Context): CoinTrailDatabase`, `CoinTrailDatabase.createInMemory(context: Context): CoinTrailDatabase`, with accessors `expenseDao()`, `categoryDao()`, `paymentMethodDao()`, `budgetDao()`, `recurringSeriesDao()`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/cointrail/data/db/DaoTest.kt`:

```kotlin
package com.cointrail.data.db

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaoTest {

    private lateinit var db: CoinTrailDatabase
    private val t0: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val dayStart: LocalDateTime = LocalDateTime.of(2026, 10, 5, 0, 0)
    private val dayEnd: LocalDateTime = LocalDateTime.of(2026, 10, 6, 0, 0)

    @Before
    fun setUp() {
        db = CoinTrailDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun expense(
        id: String,
        paisa: Long,
        category: String = "preset-food",
        at: LocalDateTime,
        deletedAt: LocalDateTime? = null,
    ) = ExpenseEntity(
        id = id,
        amountPaisa = paisa,
        categoryId = category,
        note = null,
        paymentMethodId = null,
        occurredAt = at,
        createdAt = t0,
        updatedAt = t0,
        deletedAt = deletedAt,
    )

    @Test
    fun `insert and observe range`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 13, 0)))
        db.expenseDao().upsert(expense("b", 200, at = LocalDateTime.of(2026, 10, 4, 13, 0)))
        val rows = db.expenseDao().observeBetween(dayStart, dayEnd).first()
        assertEquals(listOf("a"), rows.map { it.id })
    }

    @Test
    fun `tombstoned rows are hidden from live queries`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 13, 0), deletedAt = t0))
        assertTrue(db.expenseDao().observeBetween(dayStart, dayEnd).first().isEmpty())
    }

    @Test
    fun `softDelete stamps deletedAt and updatedAt`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 13, 0)))
        val now = LocalDateTime.of(2026, 10, 5, 22, 0)
        db.expenseDao().softDelete("a", now)
        val row = db.expenseDao().byId("a")!!
        assertEquals(now, row.deletedAt)
        assertEquals(now, row.updatedAt)
    }

    @Test
    fun `changesSince returns rows including tombstones`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 13, 0)))
        db.expenseDao().softDelete("a", t0.plusDays(1))
        assertEquals(listOf("a"), db.expenseDao().changesSince(t0).map { it.id })
        assertTrue(db.expenseDao().changesSince(LocalDateTime.of(2027, 1, 1, 0, 0)).isEmpty())
    }

    @Test
    fun `daily totals group by day and stay in range`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 9, 0)))
        db.expenseDao().upsert(expense("b", 250, at = LocalDateTime.of(2026, 10, 5, 20, 0)))
        db.expenseDao().upsert(expense("c", 400, at = LocalDateTime.of(2026, 10, 3, 8, 0)))
        db.expenseDao().upsert(expense("d", 999, at = LocalDateTime.of(2026, 11, 5, 8, 0)))
        val rows = db.expenseDao().observeDailyTotals(
            LocalDateTime.of(2026, 10, 1, 0, 0),
            LocalDateTime.of(2026, 11, 1, 0, 0),
        ).first()
        assertEquals(listOf("2026-10-03", "2026-10-05"), rows.map { it.day })
        assertEquals(350L, rows[1].totalPaisa)
    }

    @Test
    fun `category totals group by category ordered by total desc`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, category = "preset-food", at = LocalDateTime.of(2026, 10, 5, 9, 0)))
        db.expenseDao().upsert(expense("b", 250, category = "preset-food", at = LocalDateTime.of(2026, 10, 5, 10, 0)))
        db.expenseDao().upsert(expense("c", 400, category = "preset-transport", at = LocalDateTime.of(2026, 10, 5, 11, 0)))
        val rows = db.expenseDao().observeCategoryTotals(dayStart, dayEnd).first()
        assertEquals(listOf("preset-transport", "preset-food"), rows.map { it.categoryId })
        assertEquals(350L, rows[1].totalPaisa)
    }

    @Test
    fun `total between sums paisa`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 9, 0)))
        db.expenseDao().upsert(expense("b", 250, at = LocalDateTime.of(2026, 10, 5, 10, 0)))
        assertEquals(350L, db.expenseDao().observeTotalBetween(dayStart, dayEnd).first())
    }

    @Test
    fun `budget upsert on same scopeKey replaces the row`() = runBlocking {
        db.budgetDao().upsert(BudgetEntity("b1", BudgetEntity.OVERALL, 1000, t0, null))
        db.budgetDao().upsert(BudgetEntity("b2", BudgetEntity.OVERALL, 2000, t0, null))
        val all = db.budgetDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals("b2", all.first().id)
        assertEquals(2000L, all.first().monthlyLimitPaisa)
    }

    @Test
    fun `budget softDelete hides row and tombstone stays in changesSince`() = runBlocking {
        db.budgetDao().upsert(BudgetEntity("b1", "preset-food", 1000, t0, null))
        val now = LocalDateTime.of(2026, 10, 5, 22, 0)
        db.budgetDao().softDelete("b1", now)
        assertTrue(db.budgetDao().observeAll().first().isEmpty())
        val changes = db.budgetDao().changesSince(t0)
        assertEquals(1, changes.size)
        assertEquals(now, changes.first().deletedAt)
    }

    @Test
    fun `category rows round-trip and count works`() = runBlocking {
        val dao = db.categoryDao()
        dao.upsert(CategoryEntity("preset-food", "Food", true, false, 0, t0))
        dao.upsert(CategoryEntity("c1", "Pets", false, true, 9, t0))
        assertEquals(2, dao.count())
        assertEquals(listOf("preset-food", "c1"), dao.observeAll().first().map { it.id })
        assertEquals("Pets", dao.byId("c1")!!.name)
        assertNull(dao.byId("missing"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cointrail.data.db.DaoTest"`
Expected: FAIL with compilation error `Unresolved reference: CoinTrailDatabase` (and friends).

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/cointrail/data/db/Converters.kt`:

```kotlin
package com.cointrail.data.db

import androidx.room.TypeConverter
import java.time.LocalDateTime

class Converters {

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }
}
```

`app/src/main/java/com/cointrail/data/db/Entities.kt`:

```kotlin
package com.cointrail.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "expenses", indices = [Index("occurredAt"), Index("updatedAt")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amountPaisa: Long,
    val categoryId: String,
    val note: String?,
    val paymentMethodId: String?,
    val occurredAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
)

@Entity(tableName = "categories", indices = [Index("updatedAt")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val updatedAt: LocalDateTime,
)

@Entity(tableName = "payment_methods", indices = [Index("updatedAt")])
data class PaymentMethodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val updatedAt: LocalDateTime,
)

@Entity(tableName = "budgets", indices = [Index(value = ["scopeKey"], unique = true), Index("updatedAt")])
data class BudgetEntity(
    @PrimaryKey val id: String,
    val scopeKey: String,
    val monthlyLimitPaisa: Long,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
) {
    companion object {
        const val OVERALL: String = "__overall__"
    }
}

@Entity(tableName = "recurring_series", indices = [Index("updatedAt")])
data class RecurringSeriesEntity(
    @PrimaryKey val id: String,
    val amountPaisa: Long,
    val categoryId: String,
    val note: String?,
    val paymentMethodId: String?,
    val dayOfMonth: Int,
    val startMonth: String,
    val lastGeneratedMonth: String?,
    val isPaused: Boolean,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
)
```

`app/src/main/java/com/cointrail/data/db/Daos.kt`:

```kotlin
package com.cointrail.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

data class DailyTotalRow(val day: String, val totalPaisa: Long)

data class CategoryTotalRow(val categoryId: String, val totalPaisa: Long)

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun byId(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt DESC")
    fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<ExpenseEntity>>

    @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM expenses WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to")
    fun observeTotalBetween(from: LocalDateTime, to: LocalDateTime): Flow<Long>

    @Query("SELECT substr(occurredAt, 1, 10) AS day, SUM(amountPaisa) AS totalPaisa FROM expenses WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to GROUP BY day ORDER BY day ASC")
    fun observeDailyTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<DailyTotalRow>>

    @Query("SELECT categoryId, SUM(amountPaisa) AS totalPaisa FROM expenses WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to GROUP BY categoryId ORDER BY totalPaisa DESC")
    fun observeCategoryTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<CategoryTotalRow>>

    @Query("SELECT * FROM expenses WHERE updatedAt >= :since ORDER BY updatedAt ASC")
    suspend fun changesSince(since: LocalDateTime): List<ExpenseEntity>

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: LocalDateTime)
}

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE updatedAt >= :since ORDER BY updatedAt ASC")
    suspend fun changesSince(since: LocalDateTime): List<CategoryEntity>
}

@Dao
interface PaymentMethodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(paymentMethods: List<PaymentMethodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(paymentMethod: PaymentMethodEntity)

    @Query("SELECT * FROM payment_methods ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun byId(id: String): PaymentMethodEntity?

    @Query("SELECT COUNT(*) FROM payment_methods")
    suspend fun count(): Int

    @Query("SELECT * FROM payment_methods WHERE updatedAt >= :since ORDER BY updatedAt ASC")
    suspend fun changesSince(since: LocalDateTime): List<PaymentMethodEntity>
}

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE scopeKey = :scopeKey")
    suspend fun byScopeKey(scopeKey: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE deletedAt IS NULL ORDER BY scopeKey ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("UPDATE budgets SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: LocalDateTime)

    @Query("SELECT * FROM budgets WHERE updatedAt >= :since ORDER BY updatedAt ASC")
    suspend fun changesSince(since: LocalDateTime): List<BudgetEntity>
}

@Dao
interface RecurringSeriesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(series: RecurringSeriesEntity)

    @Query("SELECT * FROM recurring_series WHERE id = :id")
    suspend fun byId(id: String): RecurringSeriesEntity?

    @Query("SELECT * FROM recurring_series WHERE deletedAt IS NULL ORDER BY dayOfMonth ASC")
    fun observeAll(): Flow<List<RecurringSeriesEntity>>

    @Query("UPDATE recurring_series SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: LocalDateTime)

    @Query("SELECT * FROM recurring_series WHERE updatedAt >= :since ORDER BY updatedAt ASC")
    suspend fun changesSince(since: LocalDateTime): List<RecurringSeriesEntity>
}
```

`app/src/main/java/com/cointrail/data/db/CoinTrailDatabase.kt`:

```kotlin
package com.cointrail.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
        BudgetEntity::class,
        RecurringSeriesEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CoinTrailDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringSeriesDao(): RecurringSeriesDao

    companion object {
        fun create(context: Context): CoinTrailDatabase =
            Room.databaseBuilder(context, CoinTrailDatabase::class.java, "cointrail.db").build()

        fun createInMemory(context: Context): CoinTrailDatabase =
            Room.inMemoryDatabaseBuilder(context, CoinTrailDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cointrail.data.db.DaoTest"`
Expected: PASS (10 tests). The first Robolectric run downloads Android SDK jars (network required).
Also verify the schema export appeared: `app/schemas/com.cointrail.data.db.CoinTrailDatabase/1.json` exists.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cointrail/data/db app/src/test/java/com/cointrail/data/db app/schemas
git commit -m "feat: add Room schema v1 with DAOs and type converters" -m "Co-authored-by: CommandCodeBot <noreply@commandcode.ai>"
```

---

### Task 5: ExpenseRepository + entity/domain mappers

**Files:**
- Test: `app/src/test/java/com/cointrail/data/repo/ExpenseRepositoryTest.kt`
- Create: `app/src/main/java/com/cointrail/data/repo/Mappers.kt`, `app/src/main/java/com/cointrail/data/repo/ExpenseRepository.kt`

**Interfaces:**
- Consumes: `Money` (Task 2), domain models (Task 3), `ExpenseDao` (Task 4).
- Produces (package `com.cointrail.data.repo`):
  - `ExpenseRepository(dao: ExpenseDao, now: () -> LocalDateTime = { LocalDateTime.now() })` with:
    - `suspend fun add(amount: Money, categoryId: String, note: String?, paymentMethodId: String?, occurredAt: LocalDateTime): String` (returns the new id)
    - `suspend fun update(expense: Expense)`
    - `suspend fun softDelete(id: String)`
    - `fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<Expense>>`
    - `fun observeTotalBetween(from: LocalDateTime, to: LocalDateTime): Flow<Money>`
    - `fun observeDailyTotals(month: YearMonth): Flow<List<DailyTotal>>`
    - `fun observeCategoryTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<CategoryTotal>>`
    - `suspend fun changesSince(since: LocalDateTime): List<Expense>`
  - Internal mappers `ExpenseEntity.toDomain()` / `Expense.toEntity()`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/cointrail/data/repo/ExpenseRepositoryTest.kt`:

```kotlin
package com.cointrail.data.repo

import androidx.test.core.app.ApplicationProvider
import com.cointrail.core.Money
import com.cointrail.data.db.CoinTrailDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpenseRepositoryTest {

    private lateinit var db: CoinTrailDatabase
    private lateinit var repo: ExpenseRepository
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 5, 21, 30)
    private val dayStart: LocalDateTime = LocalDateTime.of(2026, 10, 5, 0, 0)
    private val dayEnd: LocalDateTime = LocalDateTime.of(2026, 10, 6, 0, 0)

    @Before
    fun setUp() {
        db = CoinTrailDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repo = ExpenseRepository(db.expenseDao()) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `added expense appears in range and total`() = runBlocking {
        val id = repo.add(Money(125_050), "preset-food", "lunch", "pm-cash", LocalDateTime.of(2026, 10, 5, 13, 0))

        val day = repo.observeBetween(dayStart, dayEnd).first()
        assertEquals(listOf(id), day.map { it.id })
        assertEquals(Money(125_050), day.first().amount)
        assertEquals(Money(125_050), repo.observeTotalBetween(dayStart, dayEnd).first())
    }

    @Test
    fun `range excludes other days`() = runBlocking {
        repo.add(Money(100), "preset-food", null, null, LocalDateTime.of(2026, 10, 4, 13, 0))
        assertTrue(repo.observeBetween(dayStart, dayEnd).first().isEmpty())
    }

    @Test
    fun `update replaces amount`() = runBlocking {
        repo.add(Money(100), "preset-food", null, null, LocalDateTime.of(2026, 10, 5, 13, 0))
        val expense = repo.observeBetween(dayStart, dayEnd).first().first()

        repo.update(expense.copy(amount = Money(250)))

        assertEquals(Money(250), repo.observeTotalBetween(dayStart, dayEnd).first())
    }

    @Test
    fun `soft delete hides row but keeps tombstone for sync`() = runBlocking {
        val id = repo.add(Money(100), "preset-food", null, null, LocalDateTime.of(2026, 10, 5, 13, 0))

        repo.softDelete(id)

        assertTrue(repo.observeBetween(dayStart, dayEnd).first().isEmpty())
        val changes = repo.changesSince(LocalDateTime.of(2026, 1, 1, 0, 0))
        assertEquals(1, changes.size)
        assertNotNull(changes.first().deletedAt)
    }

    @Test
    fun `daily totals group by day within month`() = runBlocking {
        repo.add(Money(100), "preset-food", null, null, LocalDateTime.of(2026, 10, 1, 9, 0))
        repo.add(Money(250), "preset-food", null, null, LocalDateTime.of(2026, 10, 1, 20, 0))
        repo.add(Money(400), "preset-transport", null, null, LocalDateTime.of(2026, 10, 3, 8, 0))
        repo.add(Money(999), "preset-food", null, null, LocalDateTime.of(2026, 11, 1, 8, 0))

        val totals = repo.observeDailyTotals(YearMonth.of(2026, 10)).first()

        assertEquals(2, totals.size)
        assertEquals(LocalDate.of(2026, 10, 1), totals[0].day)
        assertEquals(Money(350), totals[0].total)
        assertEquals(LocalDate.of(2026, 10, 3), totals[1].day)
        assertEquals(Money(400), totals[1].total)
    }

    @Test
    fun `category totals sum per category ordered by total desc`() = runBlocking {
        repo.add(Money(100), "preset-food", null, null, LocalDateTime.of(2026, 10, 5, 9, 0))
        repo.add(Money(250), "preset-food", null, null, LocalDateTime.of(2026, 10, 5, 10, 0))
        repo.add(Money(400), "preset-transport", null, null, LocalDateTime.of(2026, 10, 5, 11, 0))

        val totals = repo.observeCategoryTotals(dayStart, dayEnd).first()

        assertEquals(2, totals.size)
        assertEquals("preset-transport", totals[0].categoryId)
        assertEquals(Money(400), totals[0].total)
        assertEquals("preset-food", totals[1].categoryId)
        assertEquals(Money(350), totals[1].total)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cointrail.data.repo.ExpenseRepositoryTest"`
Expected: FAIL with compilation error `Unresolved reference: ExpenseRepository`.

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/cointrail/data/repo/Mappers.kt` (Task 6 appends the remaining mappers):

```kotlin
package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.data.db.ExpenseEntity
import com.cointrail.domain.model.Expense

internal fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    amount = Money(amountPaisa),
    categoryId = categoryId,
    note = note,
    paymentMethodId = paymentMethodId,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    amountPaisa = amount.paisa,
    categoryId = categoryId,
    note = note,
    paymentMethodId = paymentMethodId,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
```

`app/src/main/java/com/cointrail/data/repo/ExpenseRepository.kt`:

```kotlin
package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.data.db.ExpenseDao
import com.cointrail.domain.model.CategoryTotal
import com.cointrail.domain.model.DailyTotal
import com.cointrail.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

class ExpenseRepository(
    private val dao: ExpenseDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {

    suspend fun add(
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        occurredAt: LocalDateTime,
    ): String {
        val ts = now()
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            amount = amount,
            categoryId = categoryId,
            note = note,
            paymentMethodId = paymentMethodId,
            occurredAt = occurredAt,
            createdAt = ts,
            updatedAt = ts,
        )
        dao.upsert(expense.toEntity())
        return expense.id
    }

    suspend fun update(expense: Expense) {
        dao.upsert(expense.copy(updatedAt = now()).toEntity())
    }

    suspend fun softDelete(id: String) {
        dao.softDelete(id, now())
    }

    fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<Expense>> =
        dao.observeBetween(from, to).map { rows -> rows.map { it.toDomain() } }

    fun observeTotalBetween(from: LocalDateTime, to: LocalDateTime): Flow<Money> =
        dao.observeTotalBetween(from, to).map { Money(it) }

    fun observeDailyTotals(month: YearMonth): Flow<List<DailyTotal>> {
        val from = month.atDay(1).atStartOfDay()
        val to = month.plusMonths(1).atDay(1).atStartOfDay()
        return dao.observeDailyTotals(from, to)
            .map { rows -> rows.map { DailyTotal(LocalDate.parse(it.day), Money(it.totalPaisa)) } }
    }

    fun observeCategoryTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<CategoryTotal>> =
        dao.observeCategoryTotals(from, to)
            .map { rows -> rows.map { CategoryTotal(it.categoryId, Money(it.totalPaisa)) } }

    suspend fun changesSince(since: LocalDateTime): List<Expense> =
        dao.changesSince(since).map { it.toDomain() }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cointrail.data.repo.ExpenseRepositoryTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cointrail/data/repo app/src/test/java/com/cointrail/data/repo
git commit -m "feat: add ExpenseRepository with totals queries" -m "Co-authored-by: CommandCodeBot <noreply@commandcode.ai>"
```

---

### Task 6: Catalog repositories + preset seeding

**Files:**
- Test: `app/src/test/java/com/cointrail/data/repo/CatalogRepositoriesTest.kt`
- Create: `app/src/main/java/com/cointrail/data/SeedData.kt`, `app/src/main/java/com/cointrail/data/repo/CatalogRepositories.kt`
- Modify: `app/src/main/java/com/cointrail/data/repo/Mappers.kt` (append catalog mappers)

**Interfaces:**
- Consumes: domain models (Task 3), all DAOs (Task 4).
- Produces (package `com.cointrail.data.repo`), consumed by Plan 2 (UI) and later plans:
  - `CategoryRepository(dao: CategoryDao, now: () -> LocalDateTime = { LocalDateTime.now() })`: `fun observeAll(): Flow<List<Category>>`, `suspend fun ensureSeeded()`, `suspend fun add(name: String): String`, `suspend fun rename(id: String, name: String)`, `suspend fun setHidden(id: String, hidden: Boolean)`, `suspend fun changesSince(since: LocalDateTime): List<Category>`
  - `PaymentMethodRepository(dao: PaymentMethodDao, now: () -> LocalDateTime = { LocalDateTime.now() })`: same shape as `CategoryRepository`.
  - `BudgetRepository(dao: BudgetDao, now: () -> LocalDateTime = { LocalDateTime.now() })`: `fun observeAll(): Flow<List<Budget>>`, `suspend fun set(categoryId: String?, monthlyLimit: Money): String` (returns id; upserts by scope key), `suspend fun clear(id: String)`, `suspend fun changesSince(since: LocalDateTime): List<Budget>`
  - `RecurringSeriesRepository(dao: RecurringSeriesDao, now: () -> LocalDateTime = { LocalDateTime.now() })`: `fun observeAll(): Flow<List<RecurringSeries>>`, `suspend fun upsert(series: RecurringSeries)`, `suspend fun clear(id: String)`, `suspend fun changesSince(since: LocalDateTime): List<RecurringSeries>`
  - `SeedData.categories: List<Category>` (9 presets) and `SeedData.paymentMethods: List<PaymentMethod>` (4 presets) with the stable IDs from spec §6.8.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/cointrail/data/repo/CatalogRepositoriesTest.kt`:

```kotlin
package com.cointrail.data.repo

import androidx.test.core.app.ApplicationProvider
import com.cointrail.core.Money
import com.cointrail.data.db.CoinTrailDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogRepositoriesTest {

    private lateinit var db: CoinTrailDatabase
    private lateinit var categories: CategoryRepository
    private lateinit var paymentMethods: PaymentMethodRepository
    private lateinit var budgets: BudgetRepository
    private lateinit var recurring: RecurringSeriesRepository
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 5, 21, 30)

    @Before
    fun setUp() {
        db = CoinTrailDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        categories = CategoryRepository(db.categoryDao()) { now }
        paymentMethods = PaymentMethodRepository(db.paymentMethodDao()) { now }
        budgets = BudgetRepository(db.budgetDao()) { now }
        recurring = RecurringSeriesRepository(db.recurringSeriesDao()) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `ensureSeeded inserts category presets exactly once`() = runBlocking {
        categories.ensureSeeded()
        categories.ensureSeeded()

        val all = categories.observeAll().first()

        assertEquals(9, all.size)
        assertTrue(all.any { it.id == "preset-food" && it.name == "Food" })
        assertTrue(all.all { it.isPreset })
    }

    @Test
    fun `ensureSeeded inserts payment method presets`() = runBlocking {
        paymentMethods.ensureSeeded()

        val all = paymentMethods.observeAll().first()

        assertEquals(4, all.size)
        assertTrue(all.any { it.id == "pm-bkash" && it.name == "bKash" })
    }

    @Test
    fun `add rename and hide category`() = runBlocking {
        val id = categories.add("Pets")

        categories.rename(id, "Pet care")
        categories.setHidden(id, true)

        val pets = categories.observeAll().first().first { it.id == id }
        assertEquals("Pet care", pets.name)
        assertTrue(pets.isHidden)
    }

    @Test
    fun `overall budget round-trips null categoryId and clear tombstones`() = runBlocking {
        budgets.set(null, Money(2_000_000))

        val all = budgets.observeAll().first()
        assertEquals(1, all.size)
        assertNull(all.first().categoryId)
        assertEquals(Money(2_000_000), all.first().monthlyLimit)

        budgets.clear(all.first().id)
        assertTrue(budgets.observeAll().first().isEmpty())
    }

    @Test
    fun `setting same scope twice replaces the limit`() = runBlocking {
        budgets.set("preset-food", Money(500_000))
        budgets.set("preset-food", Money(600_000))

        val all = budgets.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(Money(600_000), all.first().monthlyLimit)
    }

    @Test
    fun `recurring series upsert clear and tombstone in changesSince`() = runBlocking {
        val series = RecurringSeries(
            id = "r1",
            amount = Money(150_000),
            categoryId = "preset-utilities",
            note = null,
            paymentMethodId = "pm-bkash",
            dayOfMonth = 5,
            startMonth = YearMonth.of(2026, 10),
            lastGeneratedMonth = null,
            isPaused = false,
            updatedAt = now,
        )
        recurring.upsert(series)
        assertEquals(listOf("r1"), recurring.observeAll().first().map { it.id })

        recurring.clear("r1")

        assertTrue(recurring.observeAll().first().isEmpty())
        val changes = recurring.changesSince(LocalDateTime.of(2026, 1, 1, 0, 0))
        assertEquals(1, changes.size)
        assertNotNull(changes.first().deletedAt)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cointrail.data.repo.CatalogRepositoriesTest"`
Expected: FAIL with compilation error `Unresolved reference: CategoryRepository` (and friends).

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/cointrail/data/SeedData.kt`:

```kotlin
package com.cointrail.data

import com.cointrail.domain.model.Category
import com.cointrail.domain.model.PaymentMethod
import java.time.LocalDateTime

object SeedData {

    private val SEED_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    val categories: List<Category> = listOf(
        Category(id = "preset-food", name = "Food", isPreset = true, sortOrder = 0, updatedAt = SEED_TIME),
        Category(id = "preset-groceries", name = "Groceries", isPreset = true, sortOrder = 1, updatedAt = SEED_TIME),
        Category(id = "preset-transport", name = "Transport", isPreset = true, sortOrder = 2, updatedAt = SEED_TIME),
        Category(id = "preset-utilities", name = "Utilities", isPreset = true, sortOrder = 3, updatedAt = SEED_TIME),
        Category(id = "preset-rent", name = "Rent", isPreset = true, sortOrder = 4, updatedAt = SEED_TIME),
        Category(id = "preset-health", name = "Health", isPreset = true, sortOrder = 5, updatedAt = SEED_TIME),
        Category(id = "preset-shopping", name = "Shopping", isPreset = true, sortOrder = 6, updatedAt = SEED_TIME),
        Category(id = "preset-entertainment", name = "Entertainment", isPreset = true, sortOrder = 7, updatedAt = SEED_TIME),
        Category(id = "preset-other", name = "Other", isPreset = true, sortOrder = 8, updatedAt = SEED_TIME),
    )

    val paymentMethods: List<PaymentMethod> = listOf(
        PaymentMethod(id = "pm-cash", name = "Cash", isPreset = true, sortOrder = 0, updatedAt = SEED_TIME),
        PaymentMethod(id = "pm-bkash", name = "bKash", isPreset = true, sortOrder = 1, updatedAt = SEED_TIME),
        PaymentMethod(id = "pm-nagad", name = "Nagad", isPreset = true, sortOrder = 2, updatedAt = SEED_TIME),
        PaymentMethod(id = "pm-card", name = "Card", isPreset = true, sortOrder = 3, updatedAt = SEED_TIME),
    )
}
```

`app/src/main/java/com/cointrail/data/repo/CatalogRepositories.kt`:

```kotlin
package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.data.SeedData
import com.cointrail.data.db.BudgetDao
import com.cointrail.data.db.BudgetEntity
import com.cointrail.data.db.CategoryDao
import com.cointrail.data.db.PaymentMethodDao
import com.cointrail.data.db.RecurringSeriesDao
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

class CategoryRepository(
    private val dao: CategoryDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {

    fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            dao.upsertAll(SeedData.categories.map { it.copy(updatedAt = now()).toEntity() })
        }
    }

    suspend fun add(name: String): String {
        val category = Category(name = name, updatedAt = now())
        dao.upsert(category.toEntity())
        return category.id
    }

    suspend fun rename(id: String, name: String) {
        val existing = dao.byId(id) ?: return
        dao.upsert(existing.toDomain().copy(name = name, updatedAt = now()).toEntity())
    }

    suspend fun setHidden(id: String, hidden: Boolean) {
        val existing = dao.byId(id) ?: return
        dao.upsert(existing.toDomain().copy(isHidden = hidden, updatedAt = now()).toEntity())
    }

    suspend fun changesSince(since: LocalDateTime): List<Category> =
        dao.changesSince(since).map { it.toDomain() }
}

class PaymentMethodRepository(
    private val dao: PaymentMethodDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {

    fun observeAll(): Flow<List<PaymentMethod>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            dao.upsertAll(SeedData.paymentMethods.map { it.copy(updatedAt = now()).toEntity() })
        }
    }

    suspend fun add(name: String): String {
        val paymentMethod = PaymentMethod(name = name, updatedAt = now())
        dao.upsert(paymentMethod.toEntity())
        return paymentMethod.id
    }

    suspend fun rename(id: String, name: String) {
        val existing = dao.byId(id) ?: return
        dao.upsert(existing.toDomain().copy(name = name, updatedAt = now()).toEntity())
    }

    suspend fun setHidden(id: String, hidden: Boolean) {
        val existing = dao.byId(id) ?: return
        dao.upsert(existing.toDomain().copy(isHidden = hidden, updatedAt = now()).toEntity())
    }

    suspend fun changesSince(since: LocalDateTime): List<PaymentMethod> =
        dao.changesSince(since).map { it.toDomain() }
}

class BudgetRepository(
    private val dao: BudgetDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {

    fun observeAll(): Flow<List<Budget>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun set(categoryId: String?, monthlyLimit: Money): String {
        val scopeKey = categoryId ?: BudgetEntity.OVERALL
        val existing = dao.byScopeKey(scopeKey)?.takeIf { it.deletedAt == null }
        val budget = Budget(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            categoryId = categoryId,
            monthlyLimit = monthlyLimit,
            updatedAt = now(),
        )
        dao.upsert(budget.toEntity())
        return budget.id
    }

    suspend fun clear(id: String) {
        dao.softDelete(id, now())
    }

    suspend fun changesSince(since: LocalDateTime): List<Budget> =
        dao.changesSince(since).map { it.toDomain() }
}

class RecurringSeriesRepository(
    private val dao: RecurringSeriesDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {

    fun observeAll(): Flow<List<RecurringSeries>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun upsert(series: RecurringSeries) {
        dao.upsert(series.copy(updatedAt = now()).toEntity())
    }

    suspend fun clear(id: String) {
        dao.softDelete(id, now())
    }

    suspend fun changesSince(since: LocalDateTime): List<RecurringSeries> =
        dao.changesSince(since).map { it.toDomain() }
}
```

Append to `app/src/main/java/com/cointrail/data/repo/Mappers.kt`:

```kotlin
import com.cointrail.data.db.BudgetEntity
import com.cointrail.data.db.CategoryEntity
import com.cointrail.data.db.PaymentMethodEntity
import com.cointrail.data.db.RecurringSeriesEntity
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import java.time.YearMonth

internal fun CategoryEntity.toDomain(): Category = Category(
    id = id, name = name, isPreset = isPreset, isHidden = isHidden, sortOrder = sortOrder, updatedAt = updatedAt,
)

internal fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id, name = name, isPreset = isPreset, isHidden = isHidden, sortOrder = sortOrder, updatedAt = updatedAt,
)

internal fun PaymentMethodEntity.toDomain(): PaymentMethod = PaymentMethod(
    id = id, name = name, isPreset = isPreset, isHidden = isHidden, sortOrder = sortOrder, updatedAt = updatedAt,
)

internal fun PaymentMethod.toEntity(): PaymentMethodEntity = PaymentMethodEntity(
    id = id, name = name, isPreset = isPreset, isHidden = isHidden, sortOrder = sortOrder, updatedAt = updatedAt,
)

internal fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = if (scopeKey == BudgetEntity.OVERALL) null else scopeKey,
    monthlyLimit = Money(monthlyLimitPaisa),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    scopeKey = categoryId ?: BudgetEntity.OVERALL,
    monthlyLimitPaisa = monthlyLimit.paisa,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun RecurringSeriesEntity.toDomain(): RecurringSeries = RecurringSeries(
    id = id,
    amount = Money(amountPaisa),
    categoryId = categoryId,
    note = note,
    paymentMethodId = paymentMethodId,
    dayOfMonth = dayOfMonth,
    startMonth = YearMonth.parse(startMonth),
    lastGeneratedMonth = lastGeneratedMonth?.let { YearMonth.parse(it) },
    isPaused = isPaused,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun RecurringSeries.toEntity(): RecurringSeriesEntity = RecurringSeriesEntity(
    id = id,
    amountPaisa = amount.paisa,
    categoryId = categoryId,
    note = note,
    paymentMethodId = paymentMethodId,
    dayOfMonth = dayOfMonth,
    startMonth = startMonth.toString(),
    lastGeneratedMonth = lastGeneratedMonth?.toString(),
    isPaused = isPaused,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
```

(The imports belong at the top of `Mappers.kt`, merged with the existing ones.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cointrail.data.repo.CatalogRepositoriesTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Run the full suite green**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — 36 tests total across 5 test classes.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cointrail/data app/src/test/java/com/cointrail/data
git commit -m "feat: add catalog repositories and preset seeding" -m "Co-authored-by: CommandCodeBot <noreply@commandcode.ai>"
```

---

## Plan Complete

The foundation and data layer are done when all 6 tasks are checked off and `./gradlew :app:testDebugUnitTest` is green. Next plan in the series: **Plan 2 — Core UI** (Today screen, quick-add keypad, edit/delete + undo, category & payment-method management), per `docs/SPEC.md` §12.
