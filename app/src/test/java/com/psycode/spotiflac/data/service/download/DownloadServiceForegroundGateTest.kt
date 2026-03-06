package com.psycode.spotiflac.data.service.download

import android.app.ActivityManager
import com.psycode.spotiflac.data.service.download.service.isUiForegroundImportance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadServiceForegroundGateTest {

    @Test
    fun `ui foreground importance values allow heavy suppression`() {
        assertTrue(
            isUiForegroundImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        )
        assertTrue(
            isUiForegroundImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
        )
    }

    @Test
    fun `non-ui foreground importance values do not suppress heavy recovery`() {
        assertFalse(
            isUiForegroundImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE)
        )
        assertFalse(
            isUiForegroundImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE)
        )
        assertFalse(isUiForegroundImportance(null))
    }
}

