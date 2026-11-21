package com.gamehac.perappscaler.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.gamehac.perappscaler.R
import com.gamehac.perappscaler.data.AppItem
import com.gamehac.perappscaler.engine.GameInterventionEngine
import com.gamehac.perappscaler.engine.ResolutionCalculator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class ScalingBottomSheetDialog(
    private val item: AppItem,
    private val onConfigChanged: (AppItem) -> Unit
) : BottomSheetDialogFragment() {

    private var currentScalePercent: Int = item.scalePercent
    private var currentFps: Int = item.fps

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_tuner, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val nativeSize = ResolutionCalculator.getNativeDisplaySize(context)

        val ivIcon: ImageView = view.findViewById(R.id.dialogAppIcon)
        val tvName: TextView = view.findViewById(R.id.dialogAppName)
        val tvPkg: TextView = view.findViewById(R.id.dialogAppPkg)
        val tvScreenNative: TextView = view.findViewById(R.id.dialogScreenNative)
        val tvScaleTitle: TextView = view.findViewById(R.id.tvScaleTitle)
        val tvEstimatedRes: TextView = view.findViewById(R.id.tvEstimatedRes)
        val slider: Slider = view.findViewById(R.id.sliderResolution)
        val chipPresets: ChipGroup = view.findViewById(R.id.chipGroupPresets)
        val chipFps: ChipGroup = view.findViewById(R.id.chipGroupFps)
        val btnApply: MaterialButton = view.findViewById(R.id.btnApplyScale)
        val btnReset: MaterialButton = view.findViewById(R.id.btnResetNative)

        // Set App Header
        ivIcon.setImageDrawable(item.icon)
        tvName.text = item.label
        tvPkg.text = item.packageName
        tvScreenNative.text = "Display Native: ${nativeSize.width} × ${nativeSize.height} px"

        // Initialize state
        currentScalePercent = item.scalePercent
        currentFps = item.fps

        fun updateScaleDisplay(percent: Int) {
            currentScalePercent = percent
            tvScaleTitle.text = getString(R.string.label_resolution_scale, percent)
            tvEstimatedRes.text = ResolutionCalculator.formatEstimatedResolution(
                nativeSize.width,
                nativeSize.height,
                percent / 100.0f
            )
        }

        slider.value = currentScalePercent.toFloat().coerceIn(30f, 100f)
        updateScaleDisplay(currentScalePercent)

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                chipPresets.clearCheck()
                updateScaleDisplay(value.roundToInt())
            }
        }

        // Preset Chips
        view.findViewById<Chip>(R.id.chipPresetQuality).setOnClickListener {
            slider.value = 100f
            updateScaleDisplay(100)
        }
        view.findViewById<Chip>(R.id.chipPresetBalanced).setOnClickListener {
            slider.value = 80f
            updateScaleDisplay(80)
        }
        view.findViewById<Chip>(R.id.chipPresetPerformance).setOnClickListener {
            slider.value = 70f
            updateScaleDisplay(70)
        }
        view.findViewById<Chip>(R.id.chipPresetBattery).setOnClickListener {
            slider.value = 50f
            updateScaleDisplay(50)
        }

        // FPS Limit Chips
        when (currentFps) {
            30 -> view.findViewById<Chip>(R.id.chipFps30).isChecked = true
            45 -> view.findViewById<Chip>(R.id.chipFps45).isChecked = true
            60 -> view.findViewById<Chip>(R.id.chipFps60).isChecked = true
            90 -> view.findViewById<Chip>(R.id.chipFps90).isChecked = true
            120 -> view.findViewById<Chip>(R.id.chipFps120).isChecked = true
            else -> view.findViewById<Chip>(R.id.chipFpsDefault).isChecked = true
        }

        chipFps.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFps = when (checkedIds.firstOrNull()) {
                R.id.chipFps30 -> 30
                R.id.chipFps45 -> 45
                R.id.chipFps60 -> 60
                R.id.chipFps90 -> 90
                R.id.chipFps120 -> 120
                else -> 0
            }
        }

        // Apply Button
        btnApply.setOnClickListener {
            val scaleFactor = currentScalePercent / 100.0f
            val success = GameInterventionEngine.applyIntervention(
                context,
                item.packageName,
                scaleFactor,
                currentFps
            )

            if (success) {
                item.scalePercent = currentScalePercent
                item.fps = currentFps
                Toast.makeText(
                    context,
                    getString(R.string.toast_applied, currentScalePercent, item.label),
                    Toast.LENGTH_SHORT
                ).show()
                onConfigChanged(item)
                dismiss()
            } else {
                Toast.makeText(context, R.string.toast_failed, Toast.LENGTH_SHORT).show()
            }
        }

        // Reset Button
        btnReset.setOnClickListener {
            GameInterventionEngine.resetIntervention(context, item.packageName)
            item.scalePercent = 100
            item.fps = 0
            Toast.makeText(
                context,
                getString(R.string.toast_reset, item.label),
                Toast.LENGTH_SHORT
            ).show()
            onConfigChanged(item)
            dismiss()
        }
    }

    companion object {
        const val TAG = "ScalingBottomSheetDialog"
    }
}
