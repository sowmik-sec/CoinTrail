package com.cointrail.data.repo

import androidx.test.core.app.ApplicationProvider
import com.cointrail.data.SeedData
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.domain.model.visible
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one catalog store, verified at its seam: both lists observe, add, rename, hide and seed
 * identically, and a list's `visible()` rows — what pickers take — leave hidden items out.
 * (ticket 24; prior art: the catalog repository and DAO tests, kept out of the store's assertions.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogStoreTest {

    private lateinit var db: CoinTrailDatabase
    private lateinit var catalog: CatalogStore

    @Before
    fun setUp() {
        db = CoinTrailDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        catalog = CatalogRepository(db.catalogDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seeding keeps the fixed preset timestamp so synced edits win`() = runBlocking {
        catalog.ensureSeeded(CatalogKind.CATEGORIES)
        catalog.ensureSeeded(CatalogKind.PAYMENT_METHODS)

        val food = catalog.observeAll(CatalogKind.CATEGORIES).first().first { it.id == "preset-food" }
        val cash = catalog.observeAll(CatalogKind.PAYMENT_METHODS).first().first { it.id == "pm-cash" }

        assertEquals(SeedData.categories.first { it.id == "preset-food" }.updatedAt, food.updatedAt)
        assertEquals(SeedData.paymentMethods.first { it.id == "pm-cash" }.updatedAt, cash.updatedAt)
    }

    @Test
    fun `each kind seeds its presets exactly once`() = runBlocking {
        for (kind in CatalogKind.entries) {
            catalog.ensureSeeded(kind)
            catalog.ensureSeeded(kind)
        }

        val categories = catalog.observeAll(CatalogKind.CATEGORIES).first()
        val paymentMethods = catalog.observeAll(CatalogKind.PAYMENT_METHODS).first()
        assertEquals(
            SeedData.categories.map { it.id },
            categories.map { it.id },
        )
        assertEquals(
            SeedData.paymentMethods.map { it.id },
            paymentMethods.map { it.id },
        )
        assertTrue(categories.all { it.isPreset })
        assertTrue(paymentMethods.all { it.isPreset })
    }

    @Test
    fun `add rename and hide behave identically for both kinds`() = runBlocking {
        for (kind in CatalogKind.entries) {
            val id = catalog.add(kind, "Added")
            catalog.rename(kind, id, "Renamed")
            catalog.setHidden(kind, id, true)

            val items = catalog.observeAll(kind).first()
            val added = items.first { it.id == id }
            assertEquals("Renamed", added.name)
            assertTrue(added.isHidden)

            // Hidden items are still observed (the manage screen toggles them)…
            assertEquals(1, items.size)
            // …but a picker's visible rows leave them out.
            assertTrue(items.visible().isEmpty())
        }
    }

    @Test
    fun `a custom item is appended after the kind's presets`() = runBlocking {
        catalog.ensureSeeded(CatalogKind.CATEGORIES)
        catalog.ensureSeeded(CatalogKind.PAYMENT_METHODS)

        for (kind in CatalogKind.entries) {
            val id = catalog.add(kind, "Custom")
            val all = catalog.observeAll(kind).first()
            assertEquals("Custom", all.last().name)
            assertEquals(id, all.last().id)
        }
    }

    @Test
    fun `hidden items stay out of the visible rows on both lists`() = runBlocking {
        catalog.ensureSeeded(CatalogKind.CATEGORIES)
        catalog.ensureSeeded(CatalogKind.PAYMENT_METHODS)
        val food = catalog.observeAll(CatalogKind.CATEGORIES).first().first()
        val cash = catalog.observeAll(CatalogKind.PAYMENT_METHODS).first().first()

        catalog.setHidden(CatalogKind.CATEGORIES, food.id, true)
        catalog.setHidden(CatalogKind.PAYMENT_METHODS, cash.id, true)

        assertEquals(
            SeedData.categories.size - 1,
            catalog.observeAll(CatalogKind.CATEGORIES).first().visible().size,
        )
        assertEquals(
            SeedData.paymentMethods.size - 1,
            catalog.observeAll(CatalogKind.PAYMENT_METHODS).first().visible().size,
        )
    }
}
