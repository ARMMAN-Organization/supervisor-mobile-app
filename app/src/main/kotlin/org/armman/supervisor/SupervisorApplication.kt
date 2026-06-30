package org.armman.supervisor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; enables Hilt dependency injection app-wide. */
@HiltAndroidApp
class SupervisorApplication : Application()
