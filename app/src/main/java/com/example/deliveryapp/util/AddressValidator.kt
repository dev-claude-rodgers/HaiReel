package com.rodgers.routist.util

/** 住所欄に入力されたテキストを検証・クリーニングするユーティリティ */
object AddressValidator {

    /** 住所キーワード: いずれかが含まれていれば住所と判断 */
    private val ADDRESS_KEYWORDS = listOf(
        "都", "道", "府", "県", "市", "区", "町", "村", "丁目", "番地", "番", "号"
    )

    /** 郵便番号パターン（〒123-4567 / 1234567 / 123－4567 等） */
    val POSTAL_REGEX = Regex("""[〒]?\s*[\d０-９]{3}\s*[-－ー]\s*[\d０-９]{4}""")

    /** 電話番号パターン（0X0-XXXX-XXXX 等） */
    val PHONE_REGEX = Regex("""0\d{1,4}[-－ー]\d{2,4}[-－ー]\d{4}""")

    enum class Issue {
        POSTAL_CODE,      // 郵便番号が含まれている
        PHONE_NUMBER,     // 電話番号が含まれている
        NO_ADDRESS_KEYWORD // 住所キーワードがひとつもない
    }

    data class Result(
        val issues: List<Issue>,
        val extractedPostalCode: String?,  // 検出した郵便番号の数字部分
        val cleaned: String                // 郵便番号・電話番号を除去したテキスト
    ) {
        val hasIssue: Boolean get() = issues.isNotEmpty()
    }

    fun validate(input: String): Result {
        val issues = mutableListOf<Issue>()

        val postalMatch = POSTAL_REGEX.find(input)
        val phoneMatch  = PHONE_REGEX.find(input)

        // 郵便番号を抽出（数字7桁＋ハイフン形式に正規化）
        val postalCode = postalMatch?.value
            ?.replace(Regex("[〒　\\s]"), "")
            ?.replace(Regex("[－ー]"), "-")

        // 郵便番号・電話番号を除去してクリーン文字列を作成
        var cleaned = input
        postalMatch?.let { cleaned = cleaned.replace(it.value, "") }
        phoneMatch?.let  { cleaned = cleaned.replace(it.value, "") }
        cleaned = cleaned.replace(Regex("""\s{2,}"""), " ").trim()

        if (postalMatch != null) issues.add(Issue.POSTAL_CODE)
        if (phoneMatch  != null) issues.add(Issue.PHONE_NUMBER)
        if (!ADDRESS_KEYWORDS.any { cleaned.contains(it) }) issues.add(Issue.NO_ADDRESS_KEYWORD)

        return Result(
            issues             = issues,
            extractedPostalCode = postalCode,
            cleaned            = cleaned
        )
    }

    /** 数字7桁の郵便番号文字列からハイフン付き形式に変換 */
    fun toHyphenFormat(code: String): String {
        val digits = code.replace(Regex("[^\\d]"), "")
        return if (digits.length == 7) "${digits.substring(0, 3)}-${digits.substring(3)}" else code
    }
}
