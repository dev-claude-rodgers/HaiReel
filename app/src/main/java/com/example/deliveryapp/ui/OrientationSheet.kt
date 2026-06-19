package com.rodgers.routist.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.rodgers.routist.util.themeColor

fun showOrientationSheet(ctx: Context, format: String = "ファイル", onSelected: (Boolean) -> Unit) {
    val sheet = BottomSheetDialog(ctx)
    val dp    = ctx.resources.displayMetrics.density
    val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

    val surfaceBg      = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
    val surfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
    val onSurface      = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVar   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val primary        = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surfaceBg)
        setPadding((24*dp).toInt(), (20*dp).toInt(), (24*dp).toInt(), (32*dp).toInt())
    }

    // ハンドルバー
    root.addView(View(ctx).apply {
        background = GradientDrawable().apply {
            setColor(onSurfaceVar); cornerRadius = 99*dp
        }
        layoutParams = LinearLayout.LayoutParams((40*dp).toInt(), (4*dp).toInt())
            .also { it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = (20*dp).toInt() }
    })

    // タイトル
    root.addView(TextView(ctx).apply {
        text = "用紙の向きを選択"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(onSurface); gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (8*dp).toInt() }
    })
    root.addView(TextView(ctx).apply {
        text = "${format}の用紙の向きを選んでください"; textSize = 13f
        setTextColor(onSurfaceVar); gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (24*dp).toInt() }
    })

    // 紙アイコンを描く関数
    fun paperView(widthDp: Int, heightDp: Int): View {
        val paperW = (widthDp * dp).toInt()
        val paperH = (heightDp * dp).toInt()

        // 影レイヤー
        val shadow = ShapeDrawable(RectShape()).apply {
            paint.color = Color.parseColor("#22000000")
        }
        // 紙本体
        val paper = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke((1*dp).toInt(), Color.parseColor("#CCCCCC"))
            cornerRadius = 3*dp
        }
        // 折り目（右上三角）
        val fold = object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: android.graphics.Canvas) {
                val foldSize = (10*dp)
                val b = bounds
                val path = android.graphics.Path().apply {
                    moveTo(b.right.toFloat() - foldSize, b.top.toFloat())
                    lineTo(b.right.toFloat(), b.top.toFloat())
                    lineTo(b.right.toFloat(), b.top.toFloat() + foldSize)
                    close()
                }
                canvas.drawPath(path, android.graphics.Paint().apply {
                    color = Color.parseColor("#DDDDDD"); isAntiAlias = true
                })
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        val layer = LayerDrawable(arrayOf(shadow, paper, fold)).apply {
            setLayerInset(0, (2*dp).toInt(), (2*dp).toInt(), 0, 0)
        }

        return View(ctx).apply {
            background = layer
            layoutParams = LinearLayout.LayoutParams(paperW, paperH)
                .also { it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = (16*dp).toInt() }
        }
    }

    // カード生成
    fun card(isPortrait: Boolean): LinearLayout {
        val label = if (isPortrait) "縦向き" else "横向き"
        val sub   = if (isPortrait) "A4 Portrait" else "A4 Landscape"
        val pw    = if (isPortrait) 48 else 68
        val ph    = if (isPortrait) 68 else 48

        val ripple = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            background  = GradientDrawable().apply {
                setColor(surfaceVariant); cornerRadius = 16*dp
            }
            foreground = ctx.getDrawable(ripple)
            setPadding((20*dp).toInt(), (24*dp).toInt(), (20*dp).toInt(), (20*dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)

            addView(paperView(pw, ph))

            addView(TextView(ctx).apply {
                text = label; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(onSurface); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            })
            addView(TextView(ctx).apply {
                text = sub; textSize = 12f
                setTextColor(onSurfaceVar); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.topMargin = (2*dp).toInt() }
            })

            setOnClickListener { onSelected(isPortrait); sheet.dismiss() }
        }
    }

    val cardRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    cardRow.addView(card(false).also {
        (it.layoutParams as LinearLayout.LayoutParams).marginEnd = (12*dp).toInt()
    })
    cardRow.addView(card(true))
    root.addView(cardRow)

    // キャンセル
    root.addView(TextView(ctx).apply {
        text = "キャンセル"; textSize = 14f
        setTextColor(primary); gravity = Gravity.CENTER
        setPadding(0, (20*dp).toInt(), 0, 0)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        setOnClickListener { sheet.dismiss() }
    })

    sheet.setContentView(root)
    sheet.show()
}
