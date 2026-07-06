package com.rodgers.haireel.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Android標準TTSのシングルトンラッパー（追加ライブラリなし） */
object TtsManager {

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var isReady = false

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.JAPANESE
                isReady = true
            }
        }
    }

    /**
     * テキストを読み上げる。
     * ユーザー辞書を適用してから読み上げる。
     * 全体的なひらがな→カタカナ変換は「次は」などの助詞を壊すため行わない。
     * 誤読する固有名詞は「📖 読み替え辞書」でカタカナ登録してください。
     */
    fun speak(text: String, ctx: android.content.Context? = null) {
        if (!isReady) return
        val processed = if (ctx != null) TtsDictionary.apply(ctx, text) else text
        tts?.speak(processed, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
