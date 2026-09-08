package com.devbehindyou.refract

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Phase 1: no bindings are wired here yet — this class exists solely to give Hilt a
 * component root to generate. Real modules arrive with the data layer in Phase 2+.
 */
@HiltAndroidApp
class RefractApp : Application()
