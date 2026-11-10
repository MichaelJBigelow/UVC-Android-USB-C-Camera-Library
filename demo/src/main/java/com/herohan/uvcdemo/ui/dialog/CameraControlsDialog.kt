package com.herohan.uvcdemo.ui.dialog

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.herohan.uvcdemo.R
import com.herohan.uvcdemo.ui.theme.DeepPink
import com.herohan.uvcdemo.ui.theme.Gray400
import com.serenegiant.usb.UVCControl
import kotlin.math.roundToInt

/**
 * Translucent panel of the camera's UVC controls (brightness, focus, zoom, power line frequency…).
 *
 * Every change is sent to the camera straight away. The panel does not dim the preview behind it,
 * so the effect of each change can be seen while adjusting it.
 *
 * 相机 UVC 控制项(亮度、对焦、变焦、电源频率等)的半透明面板。
 *
 * 每次更改都会立即发送给相机。面板不会使其后方的预览变暗，
 * 因此调整时即可看到每项更改的效果。
 *
 * @param control controls of the open camera / 已打开相机的控制接口
 * @param onDismiss called when the panel should close / 面板需要关闭时回调
 */
@Composable
fun CameraControlsDialog(control: UVCControl, onDismiss: () -> Unit) {
    var sliders by remember(control) { mutableStateOf(SLIDER_CONTROLS.map { it.read(control) }) }
    var powerLine by remember(control) { mutableStateOf(readPowerLineFrequency(control)) }

    fun updateSlider(index: Int, state: SliderState) {
        sliders = sliders.toMutableList().also { it[index] = state }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        // Keep the preview behind the panel fully visible.
        // 保持面板后方的预览完全可见。
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(0f) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 60.dp)
                .background(Color(0x77000000)),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                SLIDER_CONTROLS.forEachIndexed { index, spec ->
                    val state = sliders[index]
                    SliderControlRow(
                        label = stringResource(spec.label),
                        state = state,
                        onValueChange = { value ->
                            if (value != state.value) {
                                updateSlider(index, state.copy(value = value))
                                spec.set(control, value)
                            }
                        },
                        onAutoChange = { auto ->
                            val autoSpec = spec.auto ?: return@SliderControlRow
                            var newState = state.copy(autoChecked = auto)
                            if (auto) {
                                // Before turning auto on, the manual value must be reset.
                                // 开启自动之前，必须先重置手动值。
                                autoSpec.resetValue(control)
                                newState = spec.read(control).copy(autoChecked = true)
                            }
                            updateSlider(index, newState)
                            autoSpec.set(control, auto)
                        },
                    )
                }
                PowerLineFrequencyRow(
                    state = powerLine,
                    onSelected = { value ->
                        powerLine = powerLine.copy(value = value)
                        control.powerlineFrequency = value
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.camera_controls_button_close), color = DeepPink)
                }
                TextButton(onClick = {
                    resetAllControls(control)
                    sliders = SLIDER_CONTROLS.map { it.read(control) }
                    powerLine = readPowerLineFrequency(control)
                }) {
                    Text(stringResource(R.string.camera_controls_button_reset), color = DeepPink)
                }
            }
        }
    }
}

@Composable
private fun SliderControlRow(
    label: String,
    state: SliderState,
    onValueChange: (Int) -> Unit,
    onAutoChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(100.dp)) {
            Text(label, color = Gray400, style = MaterialTheme.typography.bodyMedium)
            if (state.hasAuto) {
                Row(
                    modifier = Modifier.toggleable(
                        value = state.autoChecked,
                        enabled = state.autoEnabled,
                        role = Role.Checkbox,
                        onValueChange = onAutoChange,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.autoChecked,
                        onCheckedChange = null,
                        enabled = state.autoEnabled,
                        colors = CheckboxDefaults.colors(checkedColor = DeepPink, uncheckedColor = Gray400),
                    )
                    Text(
                        stringResource(R.string.camera_controls_text_auto),
                        color = Gray400,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Slider(
            value = state.value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            enabled = state.sliderEnabled,
            valueRange = state.min.toFloat()..maxOf(state.max, state.min + 1).toFloat(),
            steps = state.steps,
            colors = SliderDefaults.colors(thumbColor = DeepPink, activeTrackColor = DeepPink),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (state.enabled) state.value.toString() else "",
            color = DeepPink,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun PowerLineFrequencyRow(state: PowerLineState, onSelected: (Int) -> Unit) {
    val labels = listOf(
        R.string.camera_controls_text_disable,
        R.string.camera_controls_text_50hz,
        R.string.camera_controls_text_60hz,
        R.string.camera_controls_text_auto,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // The option's index is the UVC power line frequency value: 0 off, 1 50Hz, 2 60Hz, 3 auto.
        // 选项的下标即 UVC 电源频率值：0 关闭，1 50Hz，2 60Hz，3 自动。
        labels.forEachIndexed { value, label ->
            Row(
                modifier = Modifier.selectable(
                    selected = state.value == value,
                    enabled = state.enabled,
                    role = Role.RadioButton,
                    onClick = { onSelected(value) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = state.value == value,
                    onClick = null,
                    enabled = state.enabled,
                    colors = RadioButtonDefaults.colors(selectedColor = DeepPink, unselectedColor = DeepPink),
                )
                Text(stringResource(label), color = DeepPink, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * What a slider row shows, read from the camera.
 *
 * 滑块行所显示的内容，读取自相机。
 *
 * @param enabled whether the camera supports this control / 相机是否支持该控制项
 * @param hasAuto whether the control has an "Auto" checkbox / 该控制项是否带有“自动”复选框
 * @param autoEnabled whether the camera supports the auto mode / 相机是否支持自动模式
 * @param autoChecked whether the auto mode is on / 自动模式是否开启
 */
private data class SliderState(
    val enabled: Boolean,
    val min: Int,
    val max: Int,
    val value: Int,
    val hasAuto: Boolean,
    val autoEnabled: Boolean,
    val autoChecked: Boolean,
) {
    /** Manual adjustment is off while the auto mode is on. / 自动模式开启时，禁止手动调节。 */
    val sliderEnabled: Boolean get() = enabled && !(autoEnabled && autoChecked)

    /** Snap to whole values when there are few of them. / 取值较少时，吸附到整数值。 */
    val steps: Int get() = (max - min - 1).takeIf { max - min in 2..MAX_SNAPPED_VALUES } ?: 0
}

private data class PowerLineState(val enabled: Boolean, val value: Int)

/**
 * A control's "Auto" checkbox.
 *
 * 控制项的“自动”复选框。
 *
 * @param resetValue resets the manual value; required before turning the auto mode on / 重置手动值；开启自动模式前必须调用
 */
private class AutoControl(
    val isEnabled: (UVCControl) -> Boolean,
    val get: (UVCControl) -> Boolean,
    val set: (UVCControl, Boolean) -> Unit,
    val resetValue: (UVCControl) -> Unit,
)

/**
 * A control adjusted with a slider, optionally with an "Auto" checkbox.
 *
 * 通过滑块调节的控制项，可选带有“自动”复选框。
 */
private class SliderControl(
    @param:StringRes val label: Int,
    val isEnabled: (UVCControl) -> Boolean,
    val limit: (UVCControl) -> IntArray?,
    val get: (UVCControl) -> Int,
    val set: (UVCControl, Int) -> Unit,
    val auto: AutoControl? = null,
) {
    fun read(control: UVCControl): SliderState {
        // limit is [min, max, default]. / limit 为 [最小值, 最大值, 默认值]。
        val range = (if (isEnabled(control)) limit(control) else null)?.takeIf { it.size >= 2 }
        val autoEnabled = auto != null && auto.isEnabled(control)
        return SliderState(
            enabled = range != null,
            min = range?.get(0) ?: 0,
            max = range?.get(1) ?: 0,
            value = if (range != null) get(control) else 0,
            hasAuto = auto != null,
            autoEnabled = autoEnabled,
            autoChecked = auto != null && autoEnabled && auto.get(control),
        )
    }
}

/**
 * The most whole values a slider snaps to; wider ranges slide freely.
 *
 * 滑块吸附的整数值上限；范围更大时可自由滑动。
 */
private const val MAX_SNAPPED_VALUES = 50

private val SLIDER_CONTROLS = listOf(
    SliderControl(
        R.string.camera_controls_text_brightness,
        UVCControl::isBrightnessEnable, UVCControl::updateBrightnessLimit,
        UVCControl::getBrightness, UVCControl::setBrightness,
    ),
    SliderControl(
        R.string.camera_controls_text_contrast,
        UVCControl::isContrastEnable, UVCControl::updateContrastLimit,
        UVCControl::getContrast, UVCControl::setContrast,
        AutoControl(
            UVCControl::isContrastAutoEnable, UVCControl::getContrastAuto,
            UVCControl::setContrastAuto, UVCControl::resetContrast,
        ),
    ),
    SliderControl(
        R.string.camera_controls_text_hue,
        UVCControl::isHueEnable, UVCControl::updateHueLimit,
        UVCControl::getHue, UVCControl::setHue,
        AutoControl(
            UVCControl::isHueAutoEnable, UVCControl::getHueAuto,
            UVCControl::setHueAuto, UVCControl::resetHue,
        ),
    ),
    SliderControl(
        R.string.camera_controls_text_saturation,
        UVCControl::isSaturationEnable, UVCControl::updateSaturationLimit,
        UVCControl::getSaturation, UVCControl::setSaturation,
    ),
    SliderControl(
        R.string.camera_controls_text_sharpness,
        UVCControl::isSharpnessEnable, UVCControl::updateSharpnessLimit,
        UVCControl::getSharpness, UVCControl::setSharpness,
    ),
    SliderControl(
        R.string.camera_controls_text_gamma,
        UVCControl::isGammaEnable, UVCControl::updateGammaLimit,
        UVCControl::getGamma, UVCControl::setGamma,
    ),
    SliderControl(
        R.string.camera_controls_text_white_balance,
        UVCControl::isWhiteBalanceEnable, UVCControl::updateWhiteBalanceLimit,
        UVCControl::getWhiteBalance, UVCControl::setWhiteBalance,
        AutoControl(
            UVCControl::isWhiteBalanceAutoEnable, UVCControl::getWhiteBalanceAuto,
            UVCControl::setWhiteBalanceAuto, UVCControl::resetWhiteBalance,
        ),
    ),
    SliderControl(
        R.string.camera_controls_text_backlight_comp,
        UVCControl::isBacklightCompEnable, UVCControl::updateBacklightCompLimit,
        UVCControl::getBacklightComp, UVCControl::setBacklightComp,
    ),
    SliderControl(
        R.string.camera_controls_text_gain,
        UVCControl::isGainEnable, UVCControl::updateGainLimit,
        UVCControl::getGain, UVCControl::setGain,
    ),
    SliderControl(
        R.string.camera_controls_text_exposure_time,
        UVCControl::isExposureTimeAbsoluteEnable, UVCControl::updateExposureTimeAbsoluteLimit,
        UVCControl::getExposureTimeAbsolute, UVCControl::setExposureTimeAbsolute,
        AutoControl(
            UVCControl::isAutoExposureModeEnable, UVCControl::isExposureTimeAuto,
            UVCControl::setExposureTimeAuto, UVCControl::resetExposureTimeAbsolute,
        ),
    ),
    SliderControl(
        R.string.camera_controls_text_iris,
        UVCControl::isIrisAbsoluteEnable, UVCControl::updateIrisAbsoluteLimit,
        UVCControl::getIrisAbsolute, UVCControl::setIrisAbsolute,
    ),
    SliderControl(
        R.string.camera_controls_text_focus,
        UVCControl::isFocusAbsoluteEnable, UVCControl::updateFocusAbsoluteLimit,
        UVCControl::getFocusAbsolute, UVCControl::setFocusAbsolute,
        AutoControl(
            UVCControl::isFocusAutoEnable, UVCControl::getFocusAuto,
            UVCControl::setFocusAuto, UVCControl::resetFocusAbsolute,
        ),
    ),
    SliderControl(
        R.string.camera_controls_text_zoom,
        UVCControl::isZoomAbsoluteEnable, UVCControl::updateZoomAbsoluteLimit,
        UVCControl::getZoomAbsolute, UVCControl::setZoomAbsolute,
    ),
    SliderControl(
        R.string.camera_controls_text_pan,
        UVCControl::isPanAbsoluteEnable, UVCControl::updatePanAbsoluteLimit,
        UVCControl::getPanAbsolute, UVCControl::setPanAbsolute,
    ),
    SliderControl(
        R.string.camera_controls_text_tilt,
        UVCControl::isTiltAbsoluteEnable, UVCControl::updateTiltAbsoluteLimit,
        UVCControl::getTiltAbsolute, UVCControl::setTiltAbsolute,
    ),
    SliderControl(
        R.string.camera_controls_text_roll,
        UVCControl::isRollAbsoluteEnable, UVCControl::updateRollAbsoluteLimit,
        UVCControl::getRollAbsolute, UVCControl::setRollAbsolute,
    ),
)

private fun readPowerLineFrequency(control: UVCControl): PowerLineState {
    val enabled = control.isPowerlineFrequencyEnable
    return PowerLineState(enabled = enabled, value = if (enabled) control.powerlineFrequency else -1)
}

/**
 * Resets every control, including the auto modes, to the camera's defaults.
 *
 * 将所有控制项(包括自动模式)重置为相机的默认值。
 */
private fun resetAllControls(control: UVCControl) {
    control.resetBrightness()
    control.resetContrast()
    control.resetContrastAuto()
    control.resetHue()
    control.resetHueAuto()
    control.resetSaturation()
    control.resetSharpness()
    control.resetGamma()
    control.resetWhiteBalance()
    control.resetWhiteBalanceAuto()
    control.resetBacklightComp()
    control.resetGain()
    control.resetExposureTimeAbsolute()
    control.resetAutoExposureMode()
    control.resetIrisAbsolute()
    control.resetFocusAbsolute()
    control.resetFocusAuto()
    control.resetZoomAbsolute()
    control.resetPanAbsolute()
    control.resetTiltAbsolute()
    control.resetRollAbsolute()
    control.resetPowerlineFrequency()
}
