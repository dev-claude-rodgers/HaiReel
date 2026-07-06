package com.rodgers.haireel.util

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SignatureStorageTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        // 前テストのファイルをクリア
        SignatureStorage.clear(ctx, SignatureStorage.TYPE_DRIVER)
        SignatureStorage.clear(ctx, SignatureStorage.TYPE_CLIENT)
    }

    @Test
    fun `fileForはsig_typepng形式のパスを返す`() {
        val file = SignatureStorage.fileFor(ctx, "driver")
        assertTrue(file.name.endsWith("sig_driver.png"))
    }

    @Test
    fun `existsは保存前はfalseを返す`() {
        assertFalse(SignatureStorage.exists(ctx, SignatureStorage.TYPE_DRIVER))
    }

    @Test
    fun `saveでファイルが作成されexistsがtrueになる`() {
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        SignatureStorage.save(ctx, SignatureStorage.TYPE_DRIVER, bmp)
        assertTrue(SignatureStorage.exists(ctx, SignatureStorage.TYPE_DRIVER))
    }

    @Test
    fun `clearで削除後はexistsがfalseになる`() {
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        SignatureStorage.save(ctx, SignatureStorage.TYPE_DRIVER, bmp)
        SignatureStorage.clear(ctx, SignatureStorage.TYPE_DRIVER)
        assertFalse(SignatureStorage.exists(ctx, SignatureStorage.TYPE_DRIVER))
    }

    @Test
    fun `TYPE_DRIVERとTYPE_CLIENTは独立したファイルを持つ`() {
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        SignatureStorage.save(ctx, SignatureStorage.TYPE_DRIVER, bmp)
        assertFalse(SignatureStorage.exists(ctx, SignatureStorage.TYPE_CLIENT))
        assertTrue(SignatureStorage.exists(ctx, SignatureStorage.TYPE_DRIVER))
    }
}
