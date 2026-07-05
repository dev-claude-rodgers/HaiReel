package com.rodgers.haireel.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class TtsDictionaryTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("haireel_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── hiraganaToKatakana（Context 不要の純粋変換）──────────────

    @Test
    fun `ひらがなをカタカナに変換する`() {
        assertEquals("アイウエオ", TtsDictionary.hiraganaToKatakana("あいうえお"))
    }

    @Test
    fun `小文字ひらがなも変換される`() {
        assertEquals("ァィゥ", TtsDictionary.hiraganaToKatakana("ぁぃぅ"))
    }

    @Test
    fun `カタカナはそのまま`() {
        assertEquals("アイウ", TtsDictionary.hiraganaToKatakana("アイウ"))
    }

    @Test
    fun `漢字や数字はそのまま`() {
        assertEquals("東京123", TtsDictionary.hiraganaToKatakana("東京123"))
    }

    @Test
    fun `空文字は空文字`() {
        assertEquals("", TtsDictionary.hiraganaToKatakana(""))
    }

    @Test
    fun `混在文字列はひらがな部分だけ変換される`() {
        assertEquals("東京アイウ123", TtsDictionary.hiraganaToKatakana("東京あいう123"))
    }

    // ── apply と辞書操作（SharedPreferences 経由）────────────────

    @Test
    fun `辞書が空ならapplyはテキストをそのまま返す`() {
        assertEquals("ハナマサ", TtsDictionary.apply(ctx, "ハナマサ"))
    }

    @Test
    fun `putで登録した表記はapplyで読みに置換される`() {
        TtsDictionary.put(ctx, "はなまさ", "ハナマサ")
        assertEquals("ハナマサ", TtsDictionary.apply(ctx, "はなまさ"))
    }
}
