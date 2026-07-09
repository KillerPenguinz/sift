package com.ironclinicgym.sift

import android.app.Application
import com.ironclinicgym.sift.di.AppContainer
import com.ironclinicgym.sift.work.RefreshScheduler

/**
 * Application root. Owns the manual DI [AppContainer] and schedules the periodic pull refresh.
 * The container is reached from workers and the redirect activity via the application context.
 */
class SiftApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (BuildConfig.DEBUG) container.seedDevTokenIfPresent(BuildConfig.SIFT_DEV_NOTION_TOKEN)
        RefreshScheduler.ensurePeriodic(this)
    }
}
