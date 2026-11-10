package com.herohan.uvcdemo.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcdemo.R;
import com.serenegiant.usb.UVCControl;
import com.warkiz.widget.IndicatorSeekBar;
import com.warkiz.widget.OnSeekChangeListener;

import java.lang.ref.WeakReference;

public class CameraControlsDialogFragment extends DialogFragment {

    private WeakReference<ICameraHelper> mCameraHelperWeak;

    private IndicatorSeekBar isbBrightness;
    private IndicatorSeekBar isbContrast;
    private CheckBox cbContrastAuto;
    private IndicatorSeekBar isbHue;
    private CheckBox cbHueAuto;
    private IndicatorSeekBar isbSaturation;
    private IndicatorSeekBar isbSharpness;
    private IndicatorSeekBar isbGamma;
    private IndicatorSeekBar isbWhiteBalance;
    private CheckBox cbWhiteBalanceAuto;
    private IndicatorSeekBar isbBacklightComp;
    private IndicatorSeekBar isbGain;
    private IndicatorSeekBar isbExposureTime;
    private CheckBox cbExposureTimeAuto;
    private IndicatorSeekBar isbIris;
    private IndicatorSeekBar isbFocus;
    private CheckBox cbFocusAuto;
    private IndicatorSeekBar isbZoom;
    private IndicatorSeekBar isbPan;
    private IndicatorSeekBar isbTilt;
    private IndicatorSeekBar isbRoll;
    private RadioGroup rgPowerLineFrequency;
    private Button btnCameraControlsCancel;
    private Button btnCameraControlsReset;

    public CameraControlsDialogFragment(ICameraHelper cameraHelper) {
        mCameraHelperWeak = new WeakReference<>(cameraHelper);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //transparent background
        // 透明背景
        setStyle(STYLE_NO_TITLE, R.style.TransparentDialogFragment);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.fragment_camera_controls, container, false);
        findAllViews(view);
        setButtonListeners();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        disableDimBehind();
        showCameraControls();
    }

    private void findAllViews(View view) {
        isbBrightness = view.findViewById(R.id.isbBrightness);
        isbContrast = view.findViewById(R.id.isbContrast);
        cbContrastAuto = view.findViewById(R.id.cbContrastAuto);
        isbHue = view.findViewById(R.id.isbHue);
        cbHueAuto = view.findViewById(R.id.cbHueAuto);
        isbSaturation = view.findViewById(R.id.isbSaturation);
        isbSharpness = view.findViewById(R.id.isbSharpness);
        isbGamma = view.findViewById(R.id.isbGamma);
        isbWhiteBalance = view.findViewById(R.id.isbWhiteBalance);
        cbWhiteBalanceAuto = view.findViewById(R.id.cbWhiteBalanceAuto);
        isbBacklightComp = view.findViewById(R.id.isbBacklightComp);
        isbGain = view.findViewById(R.id.isbGain);
        isbExposureTime = view.findViewById(R.id.isbExposureTime);
        cbExposureTimeAuto = view.findViewById(R.id.cbExposureTimeAuto);
        isbIris = view.findViewById(R.id.isbIris);
        isbFocus = view.findViewById(R.id.isbFocus);
        cbFocusAuto = view.findViewById(R.id.cbFocusAuto);
        isbZoom = view.findViewById(R.id.isbZoom);
        isbPan = view.findViewById(R.id.isbPan);
        isbTilt = view.findViewById(R.id.isbTilt);
        isbRoll = view.findViewById(R.id.isbRoll);
        rgPowerLineFrequency = view.findViewById(R.id.rgPowerLineFrequency);
        btnCameraControlsCancel = view.findViewById(R.id.btnCameraControlsCancel);
        btnCameraControlsReset = view.findViewById(R.id.btnCameraControlsReset);
    }

    private void setButtonListeners() {
        btnCameraControlsCancel.setOnClickListener(v -> {
            dismiss();
        });
        btnCameraControlsReset.setOnClickListener(v -> {
            ICameraHelper cameraHelper = mCameraHelperWeak.get();
            if (cameraHelper == null || cameraHelper.getUVCControl() == null) {
                return;
            }
            UVCControl control = cameraHelper.getUVCControl();

            resetAllControlParams(control);
            setAllControlParams(control);
        });
    }

    /**
     * disable feature that everything behind this window will be dimmed.
     *
     * 禁用“此窗口后面的所有内容都会变暗”的功能。
     */
    private void disableDimBehind() {
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.0f;
        params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(params);
    }

    private void showCameraControls() {
        ICameraHelper cameraHelper = mCameraHelperWeak.get();
        if (cameraHelper == null || cameraHelper.getUVCControl() == null) {
            return;
        }
        UVCControl control = cameraHelper.getUVCControl();
        setAllControlParams(control);
        setAllControlChangeListener(control);
    }

    private void setAllControlParams(UVCControl control) {
        // Brightness
        // 亮度
        setSeekBarParams(
                isbBrightness,
                control.isBrightnessEnable(),
                control.updateBrightnessLimit(),
                control.getBrightness());

        // Contrast
        // 对比度
        setSeekBarParams(
                isbContrast,
                control.isContrastEnable(),
                control.updateContrastLimit(),
                control.getContrast());
        if (control.isContrastAutoEnable()) {
            isbContrast.setEnabled(false);
        }
        // Contrast Auto
        // 自动对比度
        setCheckBoxParams(
                cbContrastAuto,
                control.isContrastAutoEnable(),
                control.getContrastAuto());

        // Hue
        // 色调
        setSeekBarParams(
                isbHue,
                control.isHueEnable(),
                control.updateHueLimit(),
                control.getHue());
        if (control.isHueAutoEnable()) {
            isbHue.setEnabled(false);
        }
        // Hue Auto
        // 自动色调
        setCheckBoxParams(
                cbHueAuto,
                control.isHueAutoEnable(),
                control.getHueAuto());

        // Saturation
        // 饱和度
        setSeekBarParams(
                isbSaturation,
                control.isSaturationEnable(),
                control.updateSaturationLimit(),
                control.getSaturation());

        // Sharpness
        // 锐度
        setSeekBarParams(
                isbSharpness,
                control.isSharpnessEnable(),
                control.updateSharpnessLimit(),
                control.getSharpness());

        // Gamma
        // 伽马
        setSeekBarParams(
                isbGamma,
                control.isGammaEnable(),
                control.updateGammaLimit(),
                control.getGamma());

        // White Balance
        // 白平衡
        setSeekBarParams(
                isbWhiteBalance,
                control.isWhiteBalanceEnable(),
                control.updateWhiteBalanceLimit(),
                control.getWhiteBalance());
        if (control.isWhiteBalanceAutoEnable()) {
            isbWhiteBalance.setEnabled(false);
        }
        // White Balance Auto
        // 自动白平衡
        setCheckBoxParams(
                cbWhiteBalanceAuto,
                control.isWhiteBalanceAutoEnable(),
                control.getWhiteBalanceAuto());

        // Backlight Compensation
        // 背光补偿
        setSeekBarParams(
                isbBacklightComp,
                control.isBacklightCompEnable(),
                control.updateBacklightCompLimit(),
                control.getBacklightComp());

        // Gain
        // 增益
        setSeekBarParams(
                isbGain,
                control.isGainEnable(),
                control.updateGainLimit(),
                control.getGain());

        // Exposure Time
        // 曝光时间
        setSeekBarParams(
                isbExposureTime,
                control.isExposureTimeAbsoluteEnable(),
                control.updateExposureTimeAbsoluteLimit(),
                control.getExposureTimeAbsolute());
        if (control.isAutoExposureModeEnable()) {
            isbExposureTime.setEnabled(false);
        }
        // Auto-Exposure Mode
        // 自动曝光模式
        setCheckBoxParams(
                cbExposureTimeAuto,
                control.isAutoExposureModeEnable(),
                control.isExposureTimeAuto());

        // Iris
        // 光圈
        setSeekBarParams(
                isbIris,
                control.isIrisAbsoluteEnable(),
                control.updateIrisAbsoluteLimit(),
                control.getIrisAbsolute());

        // Focus
        // 对焦
        setSeekBarParams(
                isbFocus,
                control.isFocusAbsoluteEnable(),
                control.updateFocusAbsoluteLimit(),
                control.getFocusAbsolute());
        if (control.isFocusAutoEnable()) {
            isbFocus.setEnabled(false);
        }
        // Focus Auto
        // 自动对焦
        setCheckBoxParams(
                cbFocusAuto,
                control.isFocusAutoEnable(),
                control.getFocusAuto());

        // Zoom
        // 变焦
        setSeekBarParams(
                isbZoom,
                control.isZoomAbsoluteEnable(),
                control.updateZoomAbsoluteLimit(),
                control.getZoomAbsolute());

        // Pan
        // 平移
        setSeekBarParams(
                isbPan,
                control.isPanAbsoluteEnable(),
                control.updatePanAbsoluteLimit(),
                control.getPanAbsolute());

        // Tilt
        // 倾斜
        setSeekBarParams(
                isbTilt,
                control.isTiltAbsoluteEnable(),
                control.updateTiltAbsoluteLimit(),
                control.getTiltAbsolute());

        // Roll
        // 翻滚
        setSeekBarParams(
                isbRoll,
                control.isRollAbsoluteEnable(),
                control.updateRollAbsoluteLimit(),
                control.getRollAbsolute());

        // Power Line Frequency
        // 电源频率
        setPowerLineFrequencyRadioGroup(
                rgPowerLineFrequency,
                control.isPowerlineFrequencyEnable(),
                control.updatePowerlineFrequencyLimit(),
                control.getPowerlineFrequency());
    }

    private void setAllControlChangeListener(UVCControl controls) {
        // Brightness
        // 亮度
        isbBrightness.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setBrightness(seekParams.progress));

        // Contrast
        // 对比度
        isbContrast.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setContrast(seekParams.progress));
        // Contrast Auto
        // 自动对比度
        cbContrastAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Before enable Contrast Auto, must reset Contrast
                // 启用自动对比度之前，必须先重置对比度
                controls.resetContrast();
                setSeekBarParams(
                        isbContrast,
                        controls.isContrastEnable(),
                        controls.updateContrastLimit(),
                        controls.getContrast());
                isbContrast.setEnabled(false);
            } else {
                isbContrast.setEnabled(true);
            }
            controls.setContrastAuto(isChecked);
        });

        // Hue
        // 色调
        isbHue.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setHue(seekParams.progress));
        // Hue Auto
        // 自动色调
        cbHueAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Before enable Hue Auto, must reset Hue
                // 启用自动色调之前，必须先重置色调
                controls.resetHue();
                setSeekBarParams(
                        isbHue,
                        controls.isHueEnable(),
                        controls.updateHueLimit(),
                        controls.getHue());
                isbHue.setEnabled(false);
            } else {
                isbHue.setEnabled(true);
            }
            controls.setHueAuto(isChecked);
        });

        // Saturation
        // 饱和度
        isbSaturation.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setSaturation(seekParams.progress));
        // Sharpness
        // 锐度
        isbSharpness.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setSharpness(seekParams.progress));
        // Gamma
        // 伽马
        isbGamma.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setGamma(seekParams.progress));

        // White Balance
        // 白平衡
        isbWhiteBalance.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setWhiteBalance(seekParams.progress));
        // White Balance Auto
        // 自动白平衡
        cbWhiteBalanceAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Before enable White Balance Auto, must reset White Balance
                // 启用自动白平衡之前，必须先重置白平衡
                controls.resetWhiteBalance();
                setSeekBarParams(
                        isbWhiteBalance,
                        controls.isWhiteBalanceEnable(),
                        controls.updateWhiteBalanceLimit(),
                        controls.getWhiteBalance());
                isbWhiteBalance.setEnabled(false);
            } else {
                isbWhiteBalance.setEnabled(true);
            }
            controls.setWhiteBalanceAuto(isChecked);
        });

        // Backlight Compensation
        // 背光补偿
        isbBacklightComp.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setBacklightComp(seekParams.progress));

        // Gain
        // 增益
        isbGain.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setGain(seekParams.progress));

        // Exposure Time
        // 曝光时间
        isbExposureTime.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setExposureTimeAbsolute(seekParams.progress));
        // Exposure Time Auto
        // 自动曝光时间
        cbExposureTimeAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Before enable Exposure Time Auto, must reset Exposure Time
                // 启用自动曝光时间之前，必须先重置曝光时间
                controls.resetExposureTimeAbsolute();
                setSeekBarParams(
                        isbExposureTime,
                        controls.isExposureTimeAbsoluteEnable(),
                        controls.updateExposureTimeAbsoluteLimit(),
                        controls.getExposureTimeAbsolute());
                isbExposureTime.setEnabled(false);
            } else {
                isbExposureTime.setEnabled(true);
            }
            controls.setExposureTimeAuto(isChecked);
        });

        // Iris
        // 光圈
        isbIris.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setIrisAbsolute(seekParams.progress));

        // Focus
        // 对焦
        isbFocus.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setFocusAbsolute(seekParams.progress));
        // Focus Auto
        // 自动对焦
        cbFocusAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Before enable Focus Auto, must reset Focus absolute
                // 启用自动对焦之前，必须先重置绝对对焦值
                controls.resetFocusAbsolute();
                setSeekBarParams(
                        isbFocus,
                        controls.isFocusAbsoluteEnable(),
                        controls.updateFocusAbsoluteLimit(),
                        controls.getFocusAbsolute());
                isbFocus.setEnabled(false);
            } else {
                isbFocus.setEnabled(true);
            }
            controls.setFocusAuto(isChecked);
        });

        // Zoom
        // 变焦
        isbZoom.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setZoomAbsolute(seekParams.progress));

        // Pan
        // 平移
        isbPan.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setPanAbsolute(seekParams.progress));

        // Tilt
        // 倾斜
        isbTilt.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setTiltAbsolute(seekParams.progress));

        // Roll
        // 翻滚
        isbRoll.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setRollAbsolute(seekParams.progress));

        // Power Line Frequency
        // 电源频率
        rgPowerLineFrequency.setOnCheckedChangeListener((group, checkedId) -> {
            int value = 0;
            if (checkedId == R.id.rbPowerLineFrequencyDisable) {
                value = 0;
            } else if (checkedId == R.id.rbPowerLineFrequency50Hz) {
                value = 1;
            } else if (checkedId == R.id.rbPowerLineFrequency60Hz) {
                value = 2;
            } else if (checkedId == R.id.rbPowerLineFrequencyAuto) {
                value = 3;
            }
            controls.setPowerlineFrequency(value);
        });
    }

    private void resetAllControlParams(UVCControl control) {
        // Brightness
        // 亮度
        control.resetBrightness();

        // Contrast
        // 对比度
        control.resetContrast();
        // Contrast Auto
        // 自动对比度
        control.resetContrastAuto();

        // Hue
        // 色调
        control.resetHue();
        // Hue Auto
        // 自动色调
        control.resetHueAuto();

        // Saturation
        // 饱和度
        control.resetSaturation();

        // Sharpness
        // 锐度
        control.resetSharpness();

        // Gamma
        // 伽马
        control.resetGamma();

        // White Balance
        // 白平衡
        control.resetWhiteBalance();
        // White Balance Auto
        // 自动白平衡
        control.resetWhiteBalanceAuto();

        // Backlight Compensation
        // 背光补偿
        control.resetBacklightComp();

        // Gain
        // 增益
        control.resetGain();

        // Exposure Time
        // 曝光时间
        control.resetExposureTimeAbsolute();
        // Auto-Exposure Mode
        // 自动曝光模式
        control.resetAutoExposureMode();

        // Iris
        // 光圈
        control.resetIrisAbsolute();

        // Focus
        // 对焦
        control.resetFocusAbsolute();
        // Focus Auto
        // 自动对焦
        control.resetFocusAuto();

        // Zoom
        // 变焦
        control.resetZoomAbsolute();

        // Pan
        // 平移
        control.resetPanAbsolute();

        // Tilt
        // 倾斜
        control.resetTiltAbsolute();

        // Roll
        // 翻滚
        control.resetRollAbsolute();

        // Power Line Frequency
        // 电源频率
        control.resetPowerlineFrequency();
    }

    private void setSeekBarParams(IndicatorSeekBar seekBar, boolean isEnable, int[] limit, int value) {
        seekBar.setEnabled(isEnable);
        if (isEnable && limit != null) {
            seekBar.setMax(limit[1]);
            seekBar.setMin(limit[0]);
            int tickCount = limit[1] - limit[0] + 1;
            seekBar.setTickCount(Math.min(tickCount, 50));
            seekBar.setProgress(value);
        }
    }

    private void setCheckBoxParams(CheckBox checkBox, boolean isEnable, boolean isCheck) {
        checkBox.setEnabled(isEnable);
        if (isEnable) {
            checkBox.setChecked(isCheck);
        }
    }

    private void setPowerLineFrequencyRadioGroup(RadioGroup radioGroup, boolean isEnable, int[] limit, int value) {
        radioGroup.setEnabled(isEnable);
        if (isEnable) {
            switch (value) {
                case 0: // Disable / 禁用
                    radioGroup.check(R.id.rbPowerLineFrequencyDisable);
                    break;
                case 1: // 50Hz / 50Hz
                    radioGroup.check(R.id.rbPowerLineFrequency50Hz);
                    break;
                case 2: // 60Hz / 60Hz
                    radioGroup.check(R.id.rbPowerLineFrequency60Hz);
                    break;
                case 3: // Auto / 自动
                    radioGroup.check(R.id.rbPowerLineFrequencyAuto);
                    break;
            }
        }
    }

    interface MyOnSeekChangeListener extends OnSeekChangeListener {
        @Override
        default void onStartTrackingTouch(IndicatorSeekBar seekBar) {

        }

        @Override
        default void onStopTrackingTouch(IndicatorSeekBar seekBar) {

        }
    }
}
