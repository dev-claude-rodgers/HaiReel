package com.rodgers.haireel.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.GeminiClient
import com.rodgers.haireel.util.themeColor
import com.rodgers.haireel.viewmodel.DeliveryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class AiChatSheet : BottomSheetDialogFragment() {

    private val viewModel: DeliveryViewModel by activityViewModels()

    private val history = mutableListOf<GeminiClient.Message>()
    private lateinit var bubbleContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var etInput: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var progressBar: ProgressBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        d.setOnShowListener {
            val bs = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            BottomSheetBehavior.from(bs!!).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
        return d
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorSurface))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // ヘッダー
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        }
        header.addView(TextView(ctx).apply {
            text = "🤖  AIアシスタント"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(ctx).apply {
            text = "✕"; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            val ripple = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId
            background = ContextCompat.getDrawable(ctx, ripple)
            layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt())
            setOnClickListener { dismiss() }
        })
        root.addView(header)

        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
            setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant))
        })

        // 会話エリア
        scrollView = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            clipToPadding = false
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        }
        bubbleContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(bubbleContainer)
        root.addView(scrollView)

        // プログレス
        progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }
        root.addView(progressBar)

        // 入力エリア
        val inputRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt())
        }
        etInput = EditText(ctx).apply {
            hint = "ルートについて質問する…"
            textSize = 15f
            maxLines = 4
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginEnd = (8 * dp).toInt() }
        }
        btnSend = MaterialButton(ctx).apply {
            text = "送信"
            textSize = 14f
            insetTop = 0; insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (48 * dp).toInt()
            )
        }
        inputRow.addView(etInput)
        inputRow.addView(btnSend)
        root.addView(inputRow)

        // 初回ヒント
        addBubble("ルートの最適化提案、閉店前に回る順番、メモの確認など、配達業務に関することを何でも聞いてください。", isUser = false)

        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
        btnSend.setOnClickListener { sendMessage() }

        return root
    }

    private fun sendMessage() {
        val text = etInput.text.toString().trim()
        if (text.isBlank()) return
        val ctx = requireContext()

        val apiKey = AppSettings.getGeminiApiKey(ctx)
        if (apiKey.isBlank()) {
            Toast.makeText(ctx, "設定 → Gemini APIキー を先に登録してください", Toast.LENGTH_LONG).show()
            return
        }

        etInput.setText("")
        addBubble(text, isUser = true)
        history.add(GeminiClient.Message("user", buildUserMessage(ctx, text)))

        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val reply = GeminiClient.chat(apiKey, SYSTEM_PROMPT, history)
                history.add(GeminiClient.Message("model", reply))
                addBubble(reply, isUser = false)
            } catch (e: Exception) {
                addBubble("エラーが発生しました。\n${e.message}", isUser = false, isError = true)
                // 失敗したメッセージはhistoryから削除（再送できるよう）
                if (history.isNotEmpty()) history.removeAt(history.size - 1)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun buildUserMessage(ctx: Context, userText: String): String {
        val cal = Calendar.getInstance()
        val timeStr = "%02d:%02d".format(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
        val deliveries = viewModel.deliveries.value
        val listStr = if (deliveries.isEmpty()) {
            "（配達先なし）"
        } else {
            deliveries.mapIndexed { i, d ->
                buildString {
                    append("${i + 1}. ${d.displayTitle}")
                    if (d.displayAddress.isNotBlank() && d.displayAddress != d.displayTitle)
                        append("（${d.displayAddress}）")
                    if (!d.closeTime.isNullOrBlank()) append(" 閉店:${d.closeTime}")
                    if (!d.openTime.isNullOrBlank())  append(" 開店:${d.openTime}")
                    if (!d.timeSlot.isNullOrBlank())  append(" 時間帯:${d.timeSlot}")
                    if (d.isCompleted) append(" [完了]")
                }
            }.joinToString("\n")
        }

        return """
現在時刻: $timeStr
配達リスト（${deliveries.size}件）:
$listStr

質問: $userText
        """.trimIndent()
    }

    private fun addBubble(text: String, isUser: Boolean, isError: Boolean = false) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        val tv = TextView(ctx).apply {
            this.text = text
            textSize = 15f
            setTextColor(when {
                isError -> Color.parseColor("#C62828")
                isUser  -> Color.WHITE
                else    -> ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
            })
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(when {
                    isError -> Color.parseColor("#FFEBEE")
                    isUser  -> Color.parseColor("#1565C0")
                    else    -> ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
                })
            }
            setOnLongClickListener {
                val clip = ClipData.newPlainText("AI返答", text)
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                Toast.makeText(ctx, "コピーしました", Toast.LENGTH_SHORT).show()
                true
            }
        }

        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (6 * dp).toInt()
            layoutParams = lp
        }
        val tvLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.85f)
        wrapper.addView(tv, tvLp)
        bubbleContainer.addView(wrapper)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !loading
        etInput.isEnabled = !loading
    }

    companion object {
        const val TAG = "AiChatSheet"

        private const val SYSTEM_PROMPT = """
あなたは配達ドライバー向けルート管理アプリ「HaiReel」のAIアシスタントです。
ユーザーは軽貨物ドライバーで、毎日多数の配達先を効率よく回っています。
日本語で簡潔かつ実用的なアドバイスをしてください。

できること:
- 配達リストを見て効率的な回る順番を提案する
- 営業時間・閉店時間を考慮した優先度を判断する
- 配達メモや時間帯の確認
- ルートや業務に関する一般的な質問への回答

回答は箇条書きを活用し、500文字以内を目安にしてください。
"""
    }
}
