package com.patrykmis.bar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.patrykmis.bar.databinding.BottomSheetChipBinding
import com.patrykmis.bar.databinding.OutputFormatBottomSheetBinding
import com.patrykmis.bar.format.Format
import com.patrykmis.bar.format.FormatParamInfo
import com.patrykmis.bar.format.NoParamInfo
import com.patrykmis.bar.format.RangedParamInfo
import com.patrykmis.bar.format.RangedParamType
import com.patrykmis.bar.format.SampleRate

class OutputFormatBottomSheetFragment : BottomSheetDialogFragment(),
    ChipGroup.OnCheckedStateChangeListener, Slider.OnChangeListener, View.OnClickListener {
    private var _binding: OutputFormatBottomSheetBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var prefs: Preferences

    private val chipIdToFormat = HashMap<Int, Format>()
    private val formatToChipId = HashMap<Format, Int>()
    private lateinit var formatParamInfo: FormatParamInfo

    private val chipIdToSampleRate = HashMap<Int, SampleRate>()
    private val sampleRateToChipId = HashMap<SampleRate, Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = OutputFormatBottomSheetBinding.inflate(inflater, container, false)

        prefs = Preferences(requireContext())

        binding.paramSlider.setLabelFormatter {
            formatParamInfo.format(it.toUInt())
        }
        binding.paramSlider.addOnChangeListener(this)

        binding.reset.setOnClickListener(this)

        for (format in Format.all) {
            if (!format.supported) {
                continue
            }

            addFormatChip(inflater, format)
        }

        binding.nameGroup.setOnCheckedStateChangeListener(this)

        for (sampleRate in SampleRate.all) {
            addSampleRateChip(inflater, sampleRate)
        }

        binding.sampleRateGroup.setOnCheckedStateChangeListener(this)

        refreshFormat()
        refreshSampleRate()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun addFormatChip(inflater: LayoutInflater, format: Format) {
        val chipBinding = BottomSheetChipBinding.inflate(
            inflater, binding.nameGroup, false
        )
        val id = View.generateViewId()
        chipBinding.root.id = id
        chipBinding.root.text = format.name
        chipBinding.root.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        binding.nameGroup.addView(chipBinding.root)
        chipIdToFormat[id] = format
        formatToChipId[format] = id
    }

    private fun addSampleRateChip(inflater: LayoutInflater, sampleRate: SampleRate) {
        val chipBinding = BottomSheetChipBinding.inflate(
            inflater, binding.sampleRateGroup, false
        )
        val id = View.generateViewId()
        chipBinding.root.id = id
        chipBinding.root.text = sampleRate.toString()
        chipBinding.root.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        binding.sampleRateGroup.addView(chipBinding.root)
        chipIdToSampleRate[id] = sampleRate
        sampleRateToChipId[sampleRate] = id
    }

    /**
     * Update UI based on currently selected format in the preferences.
     *
     * Calls [refreshParam] via [onCheckedChanged].
     */
    private fun refreshFormat() {
        val (format, _) = Format.fromPreferences(prefs)
        binding.nameGroup.check(formatToChipId[format]!!)
    }

    private fun refreshSampleRate() {
        val sampleRate = SampleRate.fromPreferences(prefs)
        binding.sampleRateGroup.check(sampleRateToChipId[sampleRate]!!)
    }

    /**
     * Update parameter title and slider to match format parameter specifications.
     */
    private fun refreshParam() {
        val (format, param) = Format.fromPreferences(prefs)
        formatParamInfo = format.paramInfo

        when (val info = format.paramInfo) {
            is RangedParamInfo -> {
                binding.paramGroup.isVisible = true

                binding.paramTitle.setText(
                    when (info.type) {
                        RangedParamType.CompressionLevel -> R.string.output_format_bottom_sheet_compression_level
                        RangedParamType.Bitrate -> R.string.output_format_bottom_sheet_bitrate
                    }
                )

                binding.paramSlider.valueFrom = info.range.first.toFloat()
                binding.paramSlider.valueTo = info.range.last.toFloat()
                binding.paramSlider.stepSize = info.stepSize.toFloat()
                binding.paramSlider.value = (param ?: info.default).toFloat()
            }

            NoParamInfo -> {
                binding.paramGroup.isVisible = false
            }
        }
    }

    private fun checkSampleRateAndShowDialogIfNeeded(sampleRate: SampleRate?) {
        if (prefs.format?.name == "OGG/Opus" && sampleRate?.value == 44_100u) {
            prefs.sampleRate = SampleRate(48_000u)
            showUnsupportedSampleRateDialog()
        } else {
            prefs.sampleRate = sampleRate
        }
    }

    private fun showUnsupportedSampleRateDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.unsupported_sample_rate))
            .setNeutralButton(getString(android.R.string.ok)) { _, _ ->
                refreshSampleRate()
            }
            .show()
    }

    override fun onCheckedChanged(group: ChipGroup, checkedIds: MutableList<Int>) {
        when (group) {
            binding.nameGroup -> {
                prefs.format = chipIdToFormat[checkedIds.first()]!!
                refreshParam()
                checkSampleRateAndShowDialogIfNeeded(prefs.sampleRate)
            }

            binding.sampleRateGroup -> {
                prefs.sampleRate = chipIdToSampleRate[checkedIds.first()]
                checkSampleRateAndShowDialogIfNeeded(prefs.sampleRate)
            }
        }
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        when (slider) {
            binding.paramSlider -> {
                val format = chipIdToFormat[binding.nameGroup.checkedChipId]!!
                prefs.setFormatParam(format, value.toUInt())
            }
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.reset -> {
                prefs.resetAllFormats()
                prefs.sampleRate = null
                refreshFormat()
                // Need to explicitly refresh the parameter when the default format is already chosen
                refreshParam()
                refreshSampleRate()
            }
        }
    }

    companion object {
        val TAG: String = OutputFormatBottomSheetFragment::class.java.simpleName
    }
}