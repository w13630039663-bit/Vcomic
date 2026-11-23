package com.venera.desktop.platform

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSingleInstanceTest {

    @Test
    fun `secondary launch forwards its callback arguments to the primary`() {
        val root = Files.createTempDirectory("venera-single-instance").toFile()
        val primary = DesktopSingleInstance.acquireOrForward(
            arguments = listOf("--primary"),
            dataRoot = root,
        ) as DesktopSingleInstance.Acquisition.Primary
        val received = arrayOfNulls<List<String>>(1)
        val latch = CountDownLatch(1)
        val listener = primary.coordinator.addLaunchListener {
            received[0] = it
            latch.countDown()
        }

        try {
            val second = DesktopSingleInstance.acquireOrForward(
                arguments = listOf("pixiv://account/login?code=abc123"),
                dataRoot = root,
            )
            assertEquals(DesktopSingleInstance.Acquisition.ForwardedToPrimary, second)
            assertTrue("secondary launch was not forwarded", latch.await(3, TimeUnit.SECONDS))
            assertEquals(
                listOf("pixiv://account/login?code=abc123"),
                received[0],
            )
        } finally {
            listener.close()
            primary.coordinator.close()
            root.deleteRecursively()
        }
    }
}
