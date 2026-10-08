// Read-only file access that runs as the shell user through Shizuku (ALL_IN_ONE_PLAN.md 4.3).
// Every call refuses paths outside Android/data and Android/obb.
package com.devbehindyou.atomicfilemanager.data.backend.shizuku;

import android.os.ParcelFileDescriptor;

interface IShizukuFileService {
    // Shizuku calls this transaction code to stop a user service.
    void destroy() = 16777114;

    // The folder's children, encoded by ShizukuEntries.
    String list(String path) = 1;

    // The single entry for path, encoded by ShizukuEntries; empty when it doesn't exist.
    String stat(String path) = 2;

    // A read-only descriptor for a file.
    ParcelFileDescriptor openRead(String path) = 3;
}
