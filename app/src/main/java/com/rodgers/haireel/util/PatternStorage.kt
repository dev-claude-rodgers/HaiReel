package com.rodgers.haireel.util

import android.content.Context
import com.rodgers.haireel.model.ExcelColumn
import com.rodgers.haireel.model.ReportPattern
import com.rodgers.haireel.model.decodeExcelColumns
import com.rodgers.haireel.model.encodeToJson

object PatternStorage {
    private const val PREFS = "report_patterns"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getIds(ctx: Context): List<Int> {
        val raw = p(ctx).getString("ids", "") ?: ""
        return if (raw.isBlank()) emptyList()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun putIds(ctx: Context, ids: List<Int>) =
        p(ctx).edit().putString("ids", ids.joinToString(",")).apply()

    fun get(ctx: Context, id: Int): ReportPattern? {
        if (!getIds(ctx).contains(id)) return null
        val sp = p(ctx)
        val columnsJson = sp.getString("${id}_columns", "") ?: ""
        val columns = if (columnsJson.isNotBlank()) {
            decodeExcelColumns(columnsJson)
        } else {
            // 旧フォーマットからの移行: デフォルト列を使用
            ReportPattern.defaultExcelColumns()
        }
        return ReportPattern(
            id          = id,
            title       = sp.getString("${id}_title",   "稼働報告書") ?: "稼働報告書",
            clientName  = sp.getString("${id}_client",  "") ?: "",
            driverName  = sp.getString("${id}_driver",  "") ?: "",
            closingDay  = sp.getInt(   "${id}_closing", 25),
            excelColumns = columns,
            paymentType = sp.getInt(   "${id}_pay_type",   3),
            unitPrice   = sp.getInt(   "${id}_unit_price", 0)
        )
    }

    fun getAll(ctx: Context): List<ReportPattern> = getIds(ctx).mapNotNull { get(ctx, it) }

    fun save(ctx: Context, pattern: ReportPattern) {
        val ids = getIds(ctx).toMutableList()
        if (!ids.contains(pattern.id)) { ids.add(pattern.id); putIds(ctx, ids) }
        p(ctx).edit().apply {
            putString("${pattern.id}_title",      pattern.title)
            putString("${pattern.id}_client",     pattern.clientName)
            putString("${pattern.id}_driver",     pattern.driverName)
            putInt   ("${pattern.id}_closing",    pattern.closingDay)
            putString("${pattern.id}_columns",    pattern.excelColumns.encodeToJson())
            putInt   ("${pattern.id}_pay_type",   pattern.paymentType)
            putInt   ("${pattern.id}_unit_price", pattern.unitPrice)
        }.apply()
    }

    fun delete(ctx: Context, id: Int) {
        val ids = getIds(ctx).toMutableList().also { it.remove(id) }
        putIds(ctx, ids)
        p(ctx).edit().apply {
            listOf("title", "client", "driver", "closing", "columns", "pay_type", "unit_price")
                .forEach { remove("${id}_$it") }
        }.apply()
        if (getActiveId(ctx) == id) setActiveId(ctx, ids.firstOrNull() ?: -1)
    }

    fun nextId(ctx: Context): Int {
        val sp = p(ctx); val n = sp.getInt("next_id", 0)
        sp.edit().putInt("next_id", n + 1).apply()
        return n
    }

    fun getActiveId(ctx: Context): Int = p(ctx).getInt("active_id", -1)
    fun setActiveId(ctx: Context, id: Int) = p(ctx).edit().putInt("active_id", id).apply()

    fun setNextId(ctx: Context, id: Int) = p(ctx).edit().putInt("next_id", id).apply()

    fun getActive(ctx: Context): ReportPattern = ensureDefault(ctx)

    fun ensureDefault(ctx: Context): ReportPattern {
        val ids = getIds(ctx)
        if (ids.isEmpty()) {
            val def = ReportPattern.default(nextId(ctx))
            save(ctx, def); setActiveId(ctx, def.id); return def
        }
        val aid = getActiveId(ctx)
        val validId = if (ids.contains(aid)) aid else ids.first()
        if (validId != aid) setActiveId(ctx, validId)
        return get(ctx, validId) ?: ReportPattern.default(validId)
    }
}
