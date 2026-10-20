package com.simplylauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WidgetPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APPWIDGET_ID = "picker_appwidget_id"
    }

    private lateinit var appWidgetManager: AppWidgetManager
    private var appWidgetId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)

        if (appWidgetId == -1) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(16))
        }

        // Titolo
        val title = TextView(this).apply {
            text = "Scegli un widget"
            textSize = 20f
            setTextColor(Color.BLACK)
            setPadding(dp(24), dp(16), dp(24), dp(16))
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), dp(16))
        }
        scroll.addView(list)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)

        val providers = appWidgetManager.installedProviders
            .filter {
                it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0
            }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        if (providers.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nessun widget disponibile"
                textSize = 16f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, 0)
            }
            list.addView(empty)
        } else {
            providers.forEach { info ->
                list.addView(createWidgetRow(info))
            }
        }
    }

    private fun createWidgetRow(info: AppWidgetProviderInfo): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            val outValue = TypedValue()
            context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, outValue, true
            )
            setBackgroundResource(outValue.resourceId)
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginEnd = dp(16)
            }
            try {
                val preview = info.loadPreviewImage(this@WidgetPickerActivity, 0)
                if (preview != null) {
                    setImageDrawable(preview)
                } else {
                    setImageDrawable(info.loadIcon(packageManager, 0))
                }
            } catch (e: Exception) {
                try {
                    setImageDrawable(info.loadIcon(packageManager, 0))
                } catch (_: Exception) {}
            }
        }
        row.addView(icon)

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val label = TextView(this).apply {
            text = info.loadLabel(packageManager)
            textSize = 16f
            setTextColor(Color.BLACK)
        }
        texts.addView(label)

        val appName = try {
            val ai = packageManager.getApplicationInfo(info.provider.packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            ""
        }
        val providerName = TextView(this).apply {
            text = appName
            textSize = 13f
            setTextColor(Color.GRAY)
        }
        texts.addView(providerName)

        row.addView(texts)

        row.setOnClickListener {
            val result = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        return row
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
