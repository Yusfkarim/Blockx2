package com.agon.app.battery

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for the battery manager.
 *
 * Lets the existing Settings composable obtain the singleton without changing the signature of
 * the surrounding composable chain, keeping the integration minimal.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BatteryOptimizationEntryPoint {

    fun batteryOptimizationManager(): BatteryOptimizationManager

    companion object {
        /** Resolves the singleton from the application graph. */
        fun resolve(context: Context): BatteryOptimizationManager =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                BatteryOptimizationEntryPoint::class.java,
            ).batteryOptimizationManager()
    }
}
