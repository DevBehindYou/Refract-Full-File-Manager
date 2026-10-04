package com.devbehindyou.atomicfilemanager.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Registers the app's seven custom Lint checks with the Lint runtime. Discovered via
 * `META-INF/services/com.android.tools.lint.client.api.IssueRegistry` (see
 * `src/main/resources`), which is how AGP's `lintChecks` configuration loads a
 * detector jar (architecture/MODULES.md §3).
 */
class AtomicIssueRegistry : IssueRegistry() {
    override val api: Int = CURRENT_API

    override val issues: List<Issue> =
        listOf(
            NoAndroidInDomainDetector.ISSUE,
            NoPlatformFileInUiDetector.ISSUE,
            NoFeatureCrossImportDetector.ISSUE,
            NoRawApiLevelDetector.ISSUE,
            NoHardcodedDpDetector.ISSUE,
            NoRunBlockingDetector.ISSUE,
            NoGlobalScopeDetector.ISSUE,
        )

    override val vendor: Vendor =
        Vendor(
            vendorName = "DevBehindYou",
            identifier = "com.devbehindyou.atomicfilemanager.lint-rules",
            feedbackUrl = "https://github.com/DevBehindYou/Refract-Full-File-Manager/issues",
        )
}
