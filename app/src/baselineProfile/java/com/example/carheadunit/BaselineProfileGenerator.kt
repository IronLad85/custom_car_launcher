package com.example.carheadunit

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the startup baseline profile for the release build. Run on the
 * head unit (release variant, device connected):
 *
 *   ./gradlew :app:generateBaselineProfile
 *   ./gradlew :app:collectReleaseBaselineProfile
 *
 * The first run writes the profile, the second merges it into
 * src/main/baselineProfiles so every later release build embeds it.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.example.carheadunit",
        maxIterations = 3,
    ) {
        pressHome()
        startActivityAndWait()
        // Let the home screen settle so the steady-state composition work
        // (tiles, gauges, dock) lands in the profile as well.
        device.waitForIdle()
    }
}
