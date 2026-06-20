package com.rodgers.routist.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.rodgers.routist.model.ReportPattern
import com.rodgers.routist.model.TenkoRecord
import com.rodgers.routist.model.WorkRecord
import com.rodgers.routist.util.AppSettings
import java.io.File
import java.time.LocalDate

object PdfGenerator {

    private const val MARGIN = 28f
    private val WEEKDAYS = listOf("日", "月", "火", "水", "木", "金", "土")

    // Paint.breakText を使って日本語も正確にクリップ
    private fun clipToWidth(p: Paint, text: String, maxW: Float): String {
        if (text.isEmpty() || p.measureText(text) <= maxW) return text
        val ell = "…"
        val ellW = p.measureText(ell)
        val count = p.breakText(text, true, (maxW - ellW).coerceAtLeast(0f), null)
        return if (count <= 0) ell else text.take(count) + ell
    }

    // コンテンツを実測してA4幅に収まる列幅リストを返す
    private fun autoWidths(
        headers: List<String>,
        rows: List<List<String>>,
        tableW: Float,
        minW: Float = 14f
    ): List<Float> {
        val mp = Paint(Paint.ANTI_ALIAS_FLAG)
        val n = headers.size
        val widths = FloatArray(n) { minW }
        mp.textSize = 8.5f; mp.typeface = Typeface.DEFAULT_BOLD
        for (i in 0 until n) widths[i] = maxOf(widths[i], mp.measureText(headers[i]) + 8f)
        mp.textSize = 8f; mp.typeface = Typeface.DEFAULT
        for (row in rows) {
            for (i in 0 until minOf(n, row.size)) {
                widths[i] = maxOf(widths[i], mp.measureText(row[i]) + 6f)
            }
        }
        val total = widths.sum()
        return if (total <= tableW) widths.toList()
        else widths.map { it * tableW / total }
    }

    // ────────────────────────────────────────────
    // 日報PDF
    // ────────────────────────────────────────────
    fun generateReportPdf(
        context: Context,
        records: List<WorkRecord>,
        yearMonth: String,
        assignmentName: String = "",
        portrait: Boolean = false,
        pattern: ReportPattern = ReportPattern(id = -1)
    ): File {
        val pageW = if (portrait) 595 else 842
        val pageH = if (portrait) 842 else 595
        val (y, m) = yearMonth.split("-").map { it.toInt() }
        val daysInMonth = java.time.YearMonth.of(y, m).lengthOfMonth()
        val recordMap = records.associateBy { it.date }
        val companyName = AppSettings.getCompanyName(context)
        val driverName  = pattern.driverName.ifBlank { AppSettings.getDriverName(context) }
        val clientName  = pattern.clientName
        val workingDays = records.sumOf { 1 + it.endDateOffset }

        // 列定義: (ヘッダー, データ取得関数, 合計値)
        val cols: List<Triple<String, (WorkRecord?) -> String, String>> = buildList {
            add(Triple("日付", { _: WorkRecord? -> "" }, "合計(${workingDays}日)"))
            add(Triple("曜",   { _: WorkRecord? -> "" }, ""))
            if (pattern.showTime) {
                val totalHours = records.sumOf { it.workingMinutes }.let { t ->
                    if (t > 0) "%d時間%02d分".format(t / 60, t % 60) else ""
                }
                add(Triple("開始",     { r: WorkRecord? -> r?.startTime ?: "" }, ""))
                add(Triple("終了",     { r: WorkRecord? -> r?.endTime ?: "" }, ""))
                add(Triple("稼働時間", { r: WorkRecord? -> r?.workingHoursText ?: "" }, totalHours))
            }
            if (pattern.showDelivery) {
                val lbl = pattern.deliveryLabel.ifBlank { "件数" }
                val tot = records.sumOf { it.deliveryCount }.let { if (it > 0) "${it}件" else "" }
                add(Triple(lbl, { r: WorkRecord? -> if ((r?.deliveryCount ?: 0) > 0) "${r!!.deliveryCount}件" else "" }, tot))
            }
            if (pattern.showMeter) {
                add(Triple("開始メーター", { r: WorkRecord? -> if ((r?.startMeter ?: 0) > 0) "${r!!.startMeter}km" else "" }, ""))
                add(Triple("終了メーター", { r: WorkRecord? -> if ((r?.endMeter ?: 0) > 0) "${r!!.endMeter}km" else "" }, ""))
            }
            if (pattern.showIncome) {
                val tot = records.sumOf { it.income }.let { if (it > 0) "%,d円".format(it) else "" }
                add(Triple("収入", { r: WorkRecord? -> if ((r?.income ?: 0) > 0) "%,d円".format(r!!.income) else "" }, tot))
            }
            if (pattern.showPackage) {
                val lbl = pattern.packageLabel.ifBlank { "個数" }
                val tot = records.sumOf { it.packageCount }.let { if (it > 0) "${it}個" else "" }
                add(Triple(lbl, { r: WorkRecord? -> if ((r?.packageCount ?: 0) > 0) "${r!!.packageCount}個" else "" }, tot))
            }
            if (pattern.showDistance) {
                val tot = records.sumOf { it.distanceKm.toDouble() }.let { if (it > 0) "%.0fkm".format(it) else "" }
                add(Triple("走行距離", { r: WorkRecord? -> if ((r?.distanceKm ?: 0f) > 0f) "%.0fkm".format(r!!.distanceKm) else "" }, tot))
            }
            if (pattern.showFuel) {
                val fuelTot = records.sumOf { it.fuelCost }.let { if (it > 0) "%,d円".format(it) else "" }
                add(Triple("燃料費", { r: WorkRecord? -> if ((r?.fuelCost ?: 0) > 0) "%,d円".format(r!!.fuelCost) else "" }, fuelTot))
            }
            if (pattern.showArea)
                add(Triple("エリア", { r: WorkRecord? -> r?.area ?: "" }, ""))
            if (pattern.showRemarks)
                add(Triple("備考",   { r: WorkRecord? -> r?.remarks ?: "" }, ""))
        }

        val headers = cols.map { it.first }

        // データ行を先に構築
        val dataRows = (1..daysInMonth).map { day ->
            val dateStr = "%04d-%02d-%02d".format(y, m, day)
            val ld  = LocalDate.of(y, m, day)
            val wd  = ld.dayOfWeek.value % 7
            val rec = recordMap[dateStr]
            cols.mapIndexed { idx, col ->
                when (idx) {
                    0 -> "%d/%d".format(m, day)
                    1 -> WEEKDAYS[wd]
                    else -> col.second(rec)
                }
            }
        }
        val totalRow = cols.mapIndexed { idx, col -> if (idx == 0) "合計" else col.third }

        val tableW = pageW - MARGIN * 2
        val widths = autoWidths(headers, dataRows + listOf(totalRow), tableW)

        val rowsPerPage = daysInMonth + 2
        val headerH = 52f
        val tableH  = pageH - MARGIN - headerH - MARGIN
        val rowH    = tableH / rowsPerPage

        val doc  = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = doc.startPage(info)
        val canvas = page.canvas

        val tableTop = MARGIN + headerH
        drawReportHeader(canvas, y, m, companyName, driverName, clientName, pattern, assignmentName, pageW)
        drawReportTable(canvas, headers, dataRows, totalRow, widths, rowH, tableTop, daysInMonth)

        doc.finishPage(page)

        val safeName = pattern.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val safeAssign = assignmentName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val label = if (safeAssign.isNotBlank()) "${y}年${m}月_${safeAssign}_${safeName}" else "${y}年${m}月_${safeName}"
        val ts    = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val dir   = context.getExternalFilesDir(null) ?: context.filesDir
        val file  = File(dir, "${label}_${ts}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun drawReportHeader(
        canvas: Canvas, y: Int, m: Int,
        company: String, driver: String, client: String,
        pattern: ReportPattern, assignment: String,
        pageW: Int
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = pageW / 2f

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 16f
        p.textAlign = Paint.Align.CENTER
        p.color = Color.BLACK
        canvas.drawText("${y}年${m}月 ${pattern.title}", cx, MARGIN + 16f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = 10f
        p.textAlign = Paint.Align.LEFT
        val col1 = MARGIN
        val col2 = MARGIN + 180f
        val col3 = MARGIN + 380f
        val col4 = MARGIN + 560f
        if (driver.isNotBlank())      canvas.drawText("担当者: $driver",  col1, MARGIN + 32f, p)
        if (client.isNotBlank())      canvas.drawText("取引先: $client",  col2, MARGIN + 32f, p)
        if (company.isNotBlank())     canvas.drawText("事業者: $company", col3, MARGIN + 32f, p)
        canvas.drawText("締め日: ${pattern.closingDay}日", col4, MARGIN + 32f, p)
        if (assignment.isNotBlank())  canvas.drawText("案件: $assignment", col1, MARGIN + 44f, p)
    }

    private fun drawReportTable(
        canvas: Canvas,
        headers: List<String>,
        dataRows: List<List<String>>,
        totalRow: List<String>,
        widths: List<Float>,
        rowH: Float,
        tableTop: Float,
        daysInMonth: Int
    ) {
        val p    = Paint(Paint.ANTI_ALIAS_FLAG)
        val gray = Color.parseColor("#DDDDDD")

        drawRow(canvas, p, headers, widths, tableTop, rowH, isHeader = true, gray)

        val weekdayColors = listOf(Color.parseColor("#C62828"), Color.parseColor("#444444"),
            Color.parseColor("#444444"), Color.parseColor("#444444"),
            Color.parseColor("#444444"), Color.parseColor("#444444"),
            Color.parseColor("#1565C0"))

        for (day in 1..daysInMonth) {
            val rowY  = tableTop + rowH * day
            val cells = dataRows[day - 1]
            val wd    = cells.getOrNull(1)?.let { WEEKDAYS.indexOf(it).takeIf { i -> i >= 0 } } ?: 0
            val altBg = if (day % 2 == 0) Color.parseColor("#F8F8F8") else Color.WHITE
            drawRow(canvas, p, cells, widths, rowY, rowH, isHeader = false, altBg, weekdayColors[wd], colIndex = 1)
        }

        val totalY = tableTop + rowH * (daysInMonth + 1)
        drawRow(canvas, p, totalRow, widths, totalY, rowH, isHeader = true, Color.parseColor("#E3F2FD"))

        // 外枠
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f
        p.color = Color.parseColor("#888888")
        val tableW = widths.sum()
        canvas.drawRect(MARGIN, tableTop, MARGIN + tableW, totalY + rowH, p)
    }

    private fun drawRow(
        canvas: Canvas, p: Paint,
        cells: List<String>, widths: List<Float>,
        y: Float, rowH: Float,
        isHeader: Boolean, bgColor: Int,
        specialColor: Int? = null, colIndex: Int = -1
    ) {
        p.style = Paint.Style.FILL
        p.color = bgColor
        val tableW = widths.sum()
        canvas.drawRect(MARGIN, y, MARGIN + tableW, y + rowH, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.5f
        p.color = Color.parseColor("#BBBBBB")
        canvas.drawRect(MARGIN, y, MARGIN + tableW, y + rowH, p)

        var x = MARGIN
        p.style = Paint.Style.FILL
        p.textSize = if (isHeader) 8.5f else 8f
        p.typeface = if (isHeader) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        cells.forEachIndexed { i, text ->
            val w = widths.getOrElse(i) { 0f }
            p.textAlign = when {
                i == 0 || i == 1 || i == cells.size - 1 -> Paint.Align.LEFT
                else -> Paint.Align.RIGHT
            }
            p.color = if (i == colIndex && specialColor != null) specialColor else Color.BLACK

            val tx = when (p.textAlign) {
                Paint.Align.RIGHT -> x + w - 3f
                else              -> x + 3f
            }
            val ty = y + rowH / 2f + p.textSize * 0.35f

            // セル区切り線
            if (i > 0) {
                p.color = Color.parseColor("#BBBBBB")
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.5f
                canvas.drawLine(x, y, x, y + rowH, p)
                p.style = Paint.Style.FILL
            }
            p.color = if (i == colIndex && specialColor != null) specialColor else Color.BLACK

            // Paint.breakText で日本語も正確にクリップ
            val maxW = if (p.textAlign == Paint.Align.RIGHT) w - 3f else w - 6f
            val display = clipToWidth(p, text, maxW)
            canvas.drawText(display, tx, ty, p)

            x += w
        }
    }

    // ────────────────────────────────────────────
    // 点呼表PDF
    // ────────────────────────────────────────────
    fun generateTenkoPdf(
        context: Context,
        records: List<TenkoRecord>,
        yearMonth: String,
        portrait: Boolean = false
    ): File {
        val pageW = if (portrait) 595 else 842
        val pageH = if (portrait) 842 else 595
        val (y, m) = yearMonth.split("-").map { it.toInt() }
        val daysInMonth = java.time.YearMonth.of(y, m).lengthOfMonth()
        val recordMap   = records.associateBy { it.date }
        val companyName = AppSettings.getCompanyName(context)
        val driverName  = AppSettings.getDriverName(context)
        val checkerName = AppSettings.getCheckerName(context)

        val headers = listOf(
            "日付", "曜", "車番",
            "前方法", "前時刻", "健康", "疲労", "前ALC", "前確認者",
            "後方法", "後時刻", "健康", "疲労", "後ALC", "後確認者",
            "備考"
        )

        // データ行を先に構築
        var beforeCount = 0
        var afterCount  = 0
        val dataRows = (1..daysInMonth).map { day ->
            val dateStr = "%04d-%02d-%02d".format(y, m, day)
            val ld  = LocalDate.of(y, m, day)
            val wd  = ld.dayOfWeek.value % 7
            val rec = recordMap[dateStr]
            if (rec?.beforeDone == true) beforeCount++
            if (rec?.afterDone  == true) afterCount++
            listOf(
                "%d/%d".format(m, day),
                WEEKDAYS[wd],
                rec?.vehicleNumber ?: "",
                rec?.beforeMethod ?: "",
                rec?.beforeTime   ?: "",
                boolMark(rec?.beforeHealth),
                boolMark(rec?.beforeFatigue),
                rec?.beforeAlcohol?.let { "%.2f".format(it) } ?: "",
                rec?.beforeChecker ?: "",
                rec?.afterMethod ?: "",
                rec?.afterTime   ?: "",
                boolMark(rec?.afterHealth),
                boolMark(rec?.afterFatigue),
                rec?.afterAlcohol?.let { "%.2f".format(it) } ?: "",
                rec?.afterChecker ?: "",
                rec?.note ?: ""
            )
        }
        val totalRow = listOf(
            "集計", "", "",
            "乗務前 ${beforeCount}件", "", "", "", "", "",
            "乗務後 ${afterCount}件", "", "", "", "", "",
            ""
        )

        val tableW = pageW - MARGIN * 2
        val widths = autoWidths(headers, dataRows + listOf(totalRow), tableW)

        val headerH = 52f
        val tableH  = pageH - MARGIN - headerH - MARGIN
        val totalRows = daysInMonth + 2
        val rowH    = tableH / totalRows

        val doc  = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = doc.startPage(info)
        val canvas = page.canvas

        val tenkoTableTop = MARGIN + headerH
        drawTenkoHeader(canvas, y, m, companyName, driverName, checkerName, widths, pageW)
        drawTenkoTable(canvas, dataRows, totalRow, widths, rowH, tenkoTableTop, y, m, daysInMonth)

        doc.finishPage(page)

        val dir  = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "${y}年${m}月_点呼簿.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun drawTenkoHeader(
        canvas: Canvas, y: Int, m: Int,
        company: String, driver: String, checker: String,
        widths: List<Float>, pageW: Int
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = pageW / 2f

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 18f
        p.textAlign = Paint.Align.CENTER
        p.color = Color.BLACK
        canvas.drawText("${y}年${m}月 点呼記録簿", cx, MARGIN + 18f, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = 11f
        p.textAlign = Paint.Align.LEFT
        if (company.isNotBlank()) canvas.drawText("事業者: $company", MARGIN, MARGIN + 36f, p)
        if (driver.isNotBlank())  canvas.drawText("ドライバー: $driver", MARGIN + 220f, MARGIN + 36f, p)
        if (checker.isNotBlank()) canvas.drawText("確認者: $checker",    MARGIN + 440f, MARGIN + 36f, p)

        // 乗務前/後 グループラベル（自動幅に合わせた位置）
        val befStart = widths.take(3).sum()
        val befW     = widths.drop(3).take(6).sum()
        val aftStart = befStart + befW
        val aftW     = widths.drop(9).take(6).sum()
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 8.5f
        p.textAlign = Paint.Align.CENTER
        p.color = Color.parseColor("#1565C0")
        canvas.drawText("── 乗 務 前 ──", MARGIN + befStart + befW / 2f, MARGIN + 48f, p)
        p.color = Color.parseColor("#B71C1C")
        canvas.drawText("── 乗 務 後 ──", MARGIN + aftStart + aftW / 2f, MARGIN + 48f, p)
    }

    private fun drawTenkoTable(
        canvas: Canvas,
        dataRows: List<List<String>>,
        totalRow: List<String>,
        widths: List<Float>,
        rowH: Float,
        tableTop: Float,
        y: Int, m: Int, daysInMonth: Int
    ) {
        val p       = Paint(Paint.ANTI_ALIAS_FLAG)

        val headers = listOf(
            "日付", "曜", "車番",
            "前方法", "前時刻", "健康", "疲労", "前ALC", "前確認者",
            "後方法", "後時刻", "健康", "疲労", "後ALC", "後確認者",
            "備考"
        )
        drawRow(canvas, p, headers, widths, tableTop, rowH, isHeader = true, Color.parseColor("#E8EAF6"))

        val weekdayColors = listOf(Color.parseColor("#C62828"), Color.parseColor("#444444"),
            Color.parseColor("#444444"), Color.parseColor("#444444"),
            Color.parseColor("#444444"), Color.parseColor("#444444"),
            Color.parseColor("#1565C0"))

        for (day in 1..daysInMonth) {
            val ld    = LocalDate.of(y, m, day)
            val wd    = ld.dayOfWeek.value % 7
            val rowY  = tableTop + rowH * day
            val cells = dataRows[day - 1]
            val altBg = if (day % 2 == 0) Color.parseColor("#F8F8F8") else Color.WHITE
            drawRow(canvas, p, cells, widths, rowY, rowH, isHeader = false, altBg, weekdayColors[wd], colIndex = 1)
        }

        val totalY = tableTop + rowH * (daysInMonth + 1)
        drawRow(canvas, p, totalRow, widths, totalY, rowH, isHeader = true, Color.parseColor("#E8EAF6"))

        // 外枠
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f
        p.color = Color.parseColor("#888888")
        val tableW = widths.sum()
        canvas.drawRect(MARGIN, tableTop, MARGIN + tableW, totalY + rowH, p)
    }

    private fun boolMark(v: Boolean?): String = when (v) {
        true  -> "○"
        false -> "×"
        null  -> ""
    }
}
