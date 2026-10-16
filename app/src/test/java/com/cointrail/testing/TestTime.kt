package com.cointrail.testing

import java.time.LocalDateTime

/**
 * The wall-clock stamp the store-backed fixtures use for rows whose `updatedAt` is carried but
 * never asserted: only seeding tests assert timestamps, and they read [com.cointrail.data.SeedData].
 */
val TEST_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
