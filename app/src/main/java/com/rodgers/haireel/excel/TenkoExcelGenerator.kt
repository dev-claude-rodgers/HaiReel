package com.rodgers.haireel.excel

import android.content.Context
import com.rodgers.haireel.model.TenkoRecord
import com.rodgers.haireel.util.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 点呼記録簿 Excel 出力 (18列)
 * A    : 日付
 * B-I  : 業務前 (実施日時/点呼方法/執行者/検知器/酒気帯び/疾病疲労/日常点検/指示事項)
 * J-Q  : 業務後 (実施日時/点呼方法/執行者/検知器/酒気帯び/疾病疲労/運行状況/指示事項)
 * R    : 特記事項
 *
 * 枠線ルール:
 *  ・セクション境界 (A|B, I|J, Q|R, 外縁): medium
 *  ・セクション内セル間: thin
 *  ・大ヘッダー下辺: thin（小ヘッダー上辺に続くため）
 *  ・小ヘッダー下辺: medium
 */
class TenkoExcelGenerator(private val context: Context) {

    private val ss = mutableListOf<String>()
    private fun si(v: String): Int {
        val i = ss.indexOf(v); return if (i >= 0) i else { ss.add(v); ss.size - 1 }
    }

    fun generate(records: List<TenkoRecord>, yearMonth: String, portrait: Boolean = false): File {
        ss.clear()
        val ym    = java.time.YearMonth.parse(yearMonth)
        val year  = ym.year
        val month = ym.monthValue
        val dir  = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "点呼記録簿_${year}年${month}月.xlsx")

        val company = AppSettings.getCompanyName(context)
        val driver  = AppSettings.getDriverName(context)
        val vehicle = AppSettings.getVehicleNumber(context)

        val recordMap   = records.associateBy { it.date }
        val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
        val merges      = mutableListOf<String>()

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        sb.append("""<sheetViews><sheetView workbookViewId="0" tabSelected="1" zoomScale="80"/></sheetViews>""")
        sb.append("""<sheetFormatPr defaultRowHeight="18"/>""")

        // 列幅
        val widths = listOf(11.0,9.0,9.0,10.0,7.0,7.0,12.0,7.0,12.0,9.0,9.0,10.0,7.0,7.0,12.0,12.0,12.0,14.0)
        sb.append("<cols>")
        widths.forEachIndexed { i, w -> sb.append("""<col min="${i+1}" max="${i+1}" width="$w" customWidth="1"/>""") }
        sb.append("</cols>")

        sb.append("<sheetData>")

        // ── ヘルパー ──
        fun col(c: Int): String {
            var n = c; val b = StringBuilder()
            while (n > 0) { n--; b.insert(0, 'A' + (n % 26)); n /= 26 }
            return b.toString()
        }
        fun ref(r: Int, c: Int) = "${col(c)}$r"
        fun cs(r: Int, c: Int, v: String, s: Int) = """<c r="${ref(r,c)}" t="s" s="$s"><v>${si(v)}</v></c>"""
        fun ce(r: Int, c: Int, s: Int) = """<c r="${ref(r,c)}" s="$s"/>"""
        fun mg(r1: Int, c1: Int, r2: Int, c2: Int) {
            if (r1 != r2 || c1 != c2) merges.add("${ref(r1,c1)}:${ref(r2,c2)}")
        }

        // ── 行1: タイトル ──
        sb.append("""<row r="1" ht="28" customHeight="1">""")
        sb.append(cs(1, 1, "点　呼　記　録　簿　　${year}年${month}月分（貨物軽自動車運送事業）", S_TITLE))
        (2..18).forEach { sb.append(ce(1, it, S_TITLE)) }
        sb.append("</row>"); mg(1, 1, 1, 18)

        // ── 行2: 事業者情報 ──
        sb.append("""<row r="2" ht="20" customHeight="1">""")
        sb.append(cs(2, 1, "事業者名", S_LABEL));  sb.append(ce(2, 2, S_LABEL));  mg(2, 1, 2, 2)
        sb.append(cs(2, 3, company, S_INFO));       (4..6).forEach { sb.append(ce(2, it, S_INFO)) };  mg(2, 3, 2, 6)
        sb.append(cs(2, 7, "運転者名", S_LABEL));   sb.append(ce(2, 8, S_LABEL));  mg(2, 7, 2, 8)
        sb.append(cs(2, 9, driver, S_INFO));        (10..12).forEach { sb.append(ce(2, it, S_INFO)) }; mg(2, 9, 2, 12)
        sb.append(cs(2, 13, "車両番号", S_LABEL));  sb.append(ce(2, 14, S_LABEL)); mg(2, 13, 2, 14)
        sb.append(cs(2, 15, vehicle, S_INFO));      (16..18).forEach { sb.append(ce(2, it, S_INFO)) }; mg(2, 15, 2, 18)
        sb.append("</row>")

        // ── 行3: 大ヘッダー ──
        sb.append("""<row r="3" ht="16" customHeight="1">""")
        sb.append(cs(3, 1, "日付", S_DATE_HDR))
        sb.append(cs(3, 2, "業務前点呼", S_BEF_BIG))
        (3..9).forEach { sb.append(ce(3, it, S_BEF_BIG)) }; mg(3, 2, 3, 9)
        sb.append(cs(3, 10, "業務後点呼", S_AFT_BIG))
        (11..17).forEach { sb.append(ce(3, it, S_AFT_BIG)) }; mg(3, 10, 3, 17)
        sb.append(cs(3, 18, "特記事項", S_NOTE_HDR))
        sb.append("</row>")
        mg(3, 1, 4, 1)   // 日付 A3:A4
        mg(3, 18, 4, 18) // 特記事項 R3:R4

        // ── 行4: 小ヘッダー ──
        // A4, R4 を明示して縦結合下端の枠線を確定する
        val bSubs = listOf("実施\n日時","点呼\n方法","点呼\n執行者","検知器\n使用","酒気帯び\nの有無","疾病・疲労・\n睡眠不足等","日常\n点検","指示事項")
        val aSubs = listOf("実施\n日時","点呼\n方法","点呼\n執行者","検知器\n使用","酒気帯び\nの有無","疾病・疲労・\n睡眠不足等","運行\n状況","指示事項")
        sb.append("""<row r="4" ht="38" customHeight="1">""")
        sb.append(ce(4, 1, S_DATE_HDR))                                      // A4 (縦結合下端)
        bSubs.forEachIndexed { i, h -> sb.append(cs(4, i + 2, h, S_BEF_SUB)) }
        // J4 は業務後先頭 → 左辺medium のスタイルを使用
        sb.append(cs(4, 10, aSubs[0], S_AFT_SUB_FIRST))
        aSubs.drop(1).forEachIndexed { i, h -> sb.append(cs(4, i + 11, h, S_AFT_SUB)) }
        sb.append(ce(4, 18, S_NOTE_HDR))                                     // R4 (縦結合下端)
        sb.append("</row>")

        // ── データ行 ──
        val wd = listOf("月","火","水","木","金","土","日")
        for (day in 1..daysInMonth) {
            val er  = day + 4
            val alt = day % 2 == 0
            val ds  = if (alt) S_DATA_ALT  else S_DATA_NRM
            val ins = if (alt) S_INSTR_ALT else S_INSTR_NRM
            val ns  = if (alt) S_NOTE_ALT  else S_NOTE_NRM
            val js  = if (alt) S_DATA_ALT_J else S_DATA_NRM_J  // J列 (業務後先頭, 左辺medium)

            val dk  = "%04d-%02d-%02d".format(year, month, day)
            val rec = recordMap[dk]
            val dow = wd[LocalDate.of(year, month, day).dayOfWeek.value - 1]
            val dStr = "${month}/${day}（${dow}）"

            // ── 値変換 ──
            val alcB   = toAlcPresence(rec?.beforeAlcohol)
            val drunkB = toDrunk(rec?.beforeAlcohol)
            val condB  = toCondition(rec?.beforeHealth, rec?.beforeFatigue)
            val inspB  = toInspection(rec?.beforeInspection)

            val alcA   = toAlcPresence(rec?.afterAlcohol)
            val drunkA = toDrunk(rec?.afterAlcohol)
            val condA  = toCondition(rec?.afterHealth, rec?.afterFatigue)
            val runS   = toRunStatus(rec?.afterAccident, rec?.afterVehicle)

            val bVals = listOf(rec?.beforeTime ?: "", rec?.beforeMethod ?: "", rec?.beforeChecker ?: "",
                alcB, drunkB, condB, inspB, rec?.beforeInstruction ?: "")
            val aVals = listOf(rec?.afterTime ?: "", rec?.afterMethod ?: "", rec?.afterChecker ?: "",
                alcA, drunkA, condA, runS, rec?.afterInstruction ?: "")
            val noteVal = rec?.note ?: ""

            val maxLen = listOf(bVals[7], aVals[7], noteVal).maxOf { it.length }
            val ht = when {
                maxLen > 60 -> 72.0
                maxLen > 40 -> 52.0
                maxLen > 20 -> 34.0
                else -> 18.0
            }

            sb.append("""<row r="$er" ht="$ht" customHeight="1">""")
            sb.append(cs(er, 1, dStr, S_DATE_CELL))                // A: 日付 (L/R=med)
            bVals.forEachIndexed { i, v ->
                sb.append(cs(er, i + 2, v, if (i == 7) ins else ds))
            }
            // J列 (i=0): 業務後先頭 → 左辺medium スタイル
            sb.append(cs(er, 10, aVals[0], js))
            aVals.drop(1).forEachIndexed { i, v ->
                sb.append(cs(er, i + 11, v, if (i == 6) ins else ds))
            }
            sb.append(cs(er, 18, noteVal, ns))
            sb.append("</row>")
        }

        // ── フッター ──
        val fr = daysInMonth + 5
        sb.append("""<row r="$fr" ht="28" customHeight="1">""")
        sb.append(cs(fr, 1,
            "【記載要領】点呼方法：対面・電話・IT点呼・自己点呼等を記入　" +
            "酒気帯び：有または無を記入（検知器使用時は数値も保管）　" +
            "日常点検：実施=○ 未実施=×　記録は1年間保存（貨物自動車運送事業輸送安全規則）",
            S_FOOTER))
        (2..18).forEach { sb.append(ce(fr, it, S_FOOTER)) }
        sb.append("</row>"); mg(fr, 1, fr, 18)

        sb.append("</sheetData>")
        if (merges.isNotEmpty()) {
            sb.append("""<mergeCells count="${merges.size}">""")
            merges.forEach { sb.append("""<mergeCell ref="$it"/>""") }
            sb.append("</mergeCells>")
        }
        sb.append("""<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/>""")
        sb.append("""<pageSetup paperSize="9" orientation="${if (portrait) "portrait" else "landscape"}" fitToWidth="1" fitToHeight="0"/>""")
        sb.append("</worksheet>")

        // Shared strings
        val ssXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${ss.size}" uniqueCount="${ss.size}">""")
            ss.forEach { append("""<si><t xml:space="preserve">${it.xe()}</t></si>""") }
            append("</sst>")
        }

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun e(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            e("[Content_Types].xml", CT_XML)
            e("_rels/.rels", RELS_XML)
            e("xl/workbook.xml", WB_XML)
            e("xl/_rels/workbook.xml.rels", WB_RELS_XML)
            e("xl/worksheets/sheet1.xml", sb.toString())
            e("xl/sharedStrings.xml", ssXml)
            e("xl/styles.xml", STYLES_XML)
        }
        return file
    }

    private fun String.xe() =
        replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")

    companion object {
        // ── スタイルインデックス ──
        private const val S_TITLE        = 1
        private const val S_BEF_BIG      = 2
        private const val S_BEF_SUB      = 3
        private const val S_AFT_BIG      = 4
        private const val S_AFT_SUB      = 5
        private const val S_DATE_HDR     = 6
        private const val S_NOTE_HDR     = 7
        private const val S_LABEL        = 8
        private const val S_INFO         = 9
        private const val S_DATE_CELL    = 10  // A列データ (L/R=med)
        private const val S_DATA_NRM     = 11
        private const val S_DATA_ALT     = 12
        private const val S_INSTR_NRM    = 13
        private const val S_INSTR_ALT    = 14
        private const val S_NOTE_NRM     = 15
        private const val S_NOTE_ALT     = 16
        private const val S_FOOTER       = 17
        private const val S_AFT_SUB_FIRST = 18  // J4: 業務後先頭小ヘッダー (L=med)
        private const val S_DATA_NRM_J   = 19  // J列データ奇数行 (L=med)
        private const val S_DATA_ALT_J   = 20  // J列データ偶数行 (L=med)

        internal fun toAlcPresence(alcohol: Double?): String = if (alcohol != null) "有" else ""
        internal fun toDrunk(alcohol: Double?): String = alcohol?.let { if (it <= 0.0) "無" else "有" } ?: ""
        internal fun toCondition(health: Boolean?, fatigue: Boolean?): String = when {
            health == null && fatigue == null -> ""
            health == false || fatigue == true -> "要確認"
            else -> "異常なし"
        }
        internal fun toRunStatus(accident: Boolean?, vehicle: Boolean?): String = when {
            accident == null && vehicle == null -> ""
            accident == true || vehicle == false -> "要確認"
            else -> "異常なし"
        }
        internal fun toInspection(v: Boolean?): String = when (v) { true -> "○"; false -> "×"; else -> "" }

        private fun bdr(l: String="", r: String="", t: String="", b: String=""): String {
            fun s(tag: String, style: String) =
                if (style.isEmpty()) "<$tag/>" else """<$tag style="$style"><color rgb="FF000000"/></$tag>"""
            return "<border>${s("left",l)}${s("right",r)}${s("top",t)}${s("bottom",b)}<diagonal/></border>"
        }

        private fun xf(font: Int, fill: Int, border: Int, h: String = "center", v: String = "center", wrap: Boolean = false): String {
            val a = buildString {
                append("""horizontal="$h" vertical="$v"""")
                if (wrap) append(""" wrapText="1"""")
            }
            return """<xf numFmtId="0" fontId="$font" fillId="$fill" borderId="$border" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment $a/></xf>"""
        }

        private fun fill(hex: String) =
            """<fill><patternFill patternType="solid"><fgColor rgb="FF${hex.removePrefix("#")}"/><bgColor indexed="64"/></patternFill></fill>"""

        // fill indices:
        //  2=#1A252F, 3=#154360, 4=#1A5276, 5=#145A32, 6=#196F3D,
        //  7=#515A5A, 8=#4A235A, 9=#D5D8DC, 10=#FFFFFF, 11=#EBF5FB,
        //  12=#F8F9FA, 13=#F2F3F4, 14=#FAF5FF, 15=#F3E5F5
        val STYLES_XML: String = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

            // ── フォント (9種) ──
            append("""<fonts count="9">""")
            append("""<font><sz val="11"/><name val="Calibri"/></font>""")
            append("""<font><sz val="12"/><b/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>""") // 1 title
            append("""<font><sz val="10"/><b/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>""") // 2 big hdr
            append("""<font><sz val="8"/><b/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>""")  // 3 sub hdr
            append("""<font><sz val="9"/><b/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>""")  // 4 col hdr
            append("""<font><sz val="9"/><b/><color rgb="FF1A252F"/><name val="Calibri"/></font>""")  // 5 label/date
            append("""<font><sz val="10"/><name val="Calibri"/></font>""")                            // 6 info val
            append("""<font><sz val="9"/><name val="Calibri"/></font>""")                             // 7 data
            append("""<font><sz val="7"/><color rgb="FF515A5A"/><name val="Calibri"/></font>""")      // 8 footer
            append("</fonts>")

            // ── フィル (16種) ──
            append("""<fills count="16">""")
            append("""<fill><patternFill patternType="none"/></fill>""")
            append("""<fill><patternFill patternType="gray125"/></fill>""")
            listOf("1A252F","154360","1A5276","145A32","196F3D","515A5A","4A235A",
                   "D5D8DC","FFFFFF","EBF5FB","F8F9FA","F2F3F4","FAF5FF","F3E5F5")
                .forEach { append(fill("#$it")) }
            append("</fills>")

            // ── ボーダー (9種) ──
            // 0: なし  1: 全thin  2: 全medium
            // 3: big-hdr (L/R/T=med, B=thin)  4: sub-hdr inner (L/R/T=thin, B=med)
            // 5: note-data (L/R=med, T/B=thin) ← 旧date用も兼ねる
            // 6: date-cell (L/R=med, T/B=thin) ← #5と同じ内容なので #5を流用
            // 7: aft-sub-first (L=med, R/T=thin, B=med)
            // 8: J-data (L=med, R/T/B=thin)
            append("""<borders count="9">""")
            append(bdr())                                       // 0 なし
            append(bdr("thin","thin","thin","thin"))            // 1 全thin
            append(bdr("medium","medium","medium","medium"))    // 2 全medium
            append(bdr("medium","medium","medium","thin"))      // 3 big-hdr (B=thin)
            append(bdr("thin","thin","thin","medium"))          // 4 sub-hdr inner (B=med)
            append(bdr("medium","medium","thin","thin"))        // 5 note/date-cell (L/R=med)
            append(bdr("medium","thin","thin","thin"))          // 6 (予備)
            append(bdr("medium","thin","thin","medium"))        // 7 aft-sub-first (L=med,B=med)
            append(bdr("medium","thin","thin","thin"))          // 8 J-data (L=med)
            append("</borders>")

            append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")

            // ── セルスタイル (21種, インデックス0〜20) ──
            append("""<cellXfs count="21">""")
            append("""<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""") // 0 default

            append(xf(1, 2, 2))                      // 1  S_TITLE      全medium, center
            append(xf(2, 3, 3))                      // 2  S_BEF_BIG    big-hdr
            append(xf(3, 4, 4, wrap = true))         // 3  S_BEF_SUB    sub-hdr inner, wrap
            append(xf(2, 5, 3))                      // 4  S_AFT_BIG    big-hdr
            append(xf(3, 6, 4, wrap = true))         // 5  S_AFT_SUB    sub-hdr inner, wrap
            append(xf(4, 7, 2))                      // 6  S_DATE_HDR   全medium, center
            append(xf(4, 8, 2))                      // 7  S_NOTE_HDR   全medium, center
            append(xf(5, 9, 1))                      // 8  S_LABEL      全thin, center
            append(xf(6, 10, 1, h = "left"))         // 9  S_INFO       全thin, left
            append(xf(5, 11, 5))                     // 10 S_DATE_CELL  L/R=med, center
            append(xf(7, 10, 1))                     // 11 S_DATA_NRM   全thin, center
            append(xf(7, 12, 1))                     // 12 S_DATA_ALT   全thin, center (alt bg)
            append(xf(7, 10, 1, h = "left", wrap = true)) // 13 S_INSTR_NRM
            append(xf(7, 12, 1, h = "left", wrap = true)) // 14 S_INSTR_ALT
            append(xf(7, 14, 5, h = "left", wrap = true)) // 15 S_NOTE_NRM  L/R=med
            append(xf(7, 15, 5, h = "left", wrap = true)) // 16 S_NOTE_ALT  L/R=med (alt bg)
            append(xf(8, 13, 2, h = "left", wrap = true)) // 17 S_FOOTER    全medium
            append(xf(3, 6, 7, wrap = true))         // 18 S_AFT_SUB_FIRST J4: L=med,B=med
            append(xf(7, 10, 8))                     // 19 S_DATA_NRM_J   J列奇数行: L=med
            append(xf(7, 12, 8))                     // 20 S_DATA_ALT_J   J列偶数行: L=med

            append("</cellXfs>")
            append("</styleSheet>")
        }

        private const val CT_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

        private const val RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        private const val WB_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="点呼記録簿" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        private const val WB_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }
}
