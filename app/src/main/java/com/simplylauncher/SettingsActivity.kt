package com.simplylauncher

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var transparencyLabel: TextView
    private lateinit var transparencySeekBar: SeekBar
    private lateinit var columnsChipGroup: ChipGroup
    private lateinit var rowsChipGroup: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        transparencyLabel = findViewById(R.id.transparencyLabel)
        transparencySeekBar = findViewById(R.id.transparencySeekBar)
        columnsChipGroup = findViewById(R.id.columnsChipGroup)
        rowsChipGroup = findViewById(R.id.rowsChipGroup)

        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            val alpha = Prefs.alphaFlow(this@SettingsActivity).first()
            val cols = Prefs.colsFlow(this@SettingsActivity).first()
            val rows = Prefs.rowsFlow(this@SettingsActivity).first()

            transparencySeekBar.progress = (alpha * 100).toInt()
            transparencyLabel.text = getString(R.string.settings_transparency, (alpha * 100).toInt())

            when (cols) {
                3 -> columnsChipGroup.check(R.id.chipCol3)
                4 -> columnsChipGroup.check(R.id.chipCol4)
            }

            when (rows) {
                5 -> rowsChipGroup.check(R.id.chipRow5)
                6 -> rowsChipGroup.check(R.id.chipRow6)
                7 -> rowsChipGroup.check(R.id.chipRow7)
            }
        }
    }

    private fun setupListeners() {
        transparencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val alpha = progress / 100f
                    transparencyLabel.text = getString(R.string.settings_transparency, progress)
                    lifecycleScope.launch {
                        Prefs.setAlpha(this@SettingsActivity, alpha)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        columnsChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val cols = when (checkedIds.firstOrNull()) {
                R.id.chipCol3 -> 3
                R.id.chipCol4 -> 4
                else -> return@setOnCheckedStateChangeListener
            }
            lifecycleScope.launch {
                Prefs.setCols(this@SettingsActivity, cols)
            }
        }

        rowsChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val rows = when (checkedIds.firstOrNull()) {
                R.id.chipRow5 -> 5
                R.id.chipRow6 -> 6
                R.id.chipRow7 -> 7
                else -> return@setOnCheckedStateChangeListener
            }
            lifecycleScope.launch {
                Prefs.setRows(this@SettingsActivity, rows)
            }
        }
    }
}
