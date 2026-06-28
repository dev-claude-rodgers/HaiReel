package com.rodgers.haireel.ui

import android.app.Activity
import android.os.Bundle

/** カメラOCR伝票読込機能は廃止。住所履歴オートコンプリートに置き換え済み。 */
@Deprecated("Use address history autocomplete instead")
class ScanActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setResult(RESULT_CANCELED)
        finish()
    }
}
