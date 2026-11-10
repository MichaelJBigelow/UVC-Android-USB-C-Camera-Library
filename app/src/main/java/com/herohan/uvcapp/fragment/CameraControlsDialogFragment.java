package com.herohan.uvcapp.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.databinding.FragmentCameraControlsBinding;
import com.serenegiant.usb.UVCControl;
import com.herohan.uvcapp.R;
import com.warkiz.widget.IndicatorSeekBar;
import com.warkiz.widget.OnSeekChangeListener;

import java.lang.ref.WeakReference;

public class CameraControlsDialogFragment extends DialogFragment {

    private WeakReference<ICameraHelper> mCameraHelperWeak;

    private FragmentCameraControlsBinding mBinding;

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
        mBinding = FragmentCameraControlsBinding.inflate(getLayoutInflater(), container, false);
        setButtonListeners();
        return mBinding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        disableDimBehind();
        showCameraControls();
    }

    private void setButtonListeners() {
        mBinding.btnCameraControlsCancel.setOnClickListener(v -> {
            dismiss();
        });
        mBinding.btnCameraControlsReset.setOnClickListener(v -> {
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
     * 禁用“窗口后方所有内容变暗”的功能。
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
                mBinding.isbBrightness,
                control.isBrightnessEnable(),
                control.updateBrightnessLimit(),
                control.getBrightness());

        // Contrast
        // 对比度
        setSeekBarParams(
                mBinding.isbContrast,
                control.isContrastEnable(),
                control.updateContrastLimit(),
                control.getContrast());
        if (control.isContrastAutoEnable()) {
            mBinding.isbContrast.setEnabled(false);
        }
        // Contrast Auto
        // 对比度自动
        setCheckBoxParams(
                mBinding.cbContrastAuto,
                control.isContrastAutoEnable(),
                control.getContrastAuto());

        // Hue
        // 色调
        setSeekBarParams(
                mBinding.isbHue,
                control.isHueEnable(),
                control.updateHueLimit(),
                control.getHue());
        if (control.isHueAutoEnable()) {
            mBinding.isbHue.setEnabled(false);
        }
        // Hue Auto
        // 色调自动
        setCheckBoxParams(
                mBinding.cbHueAuto,
                control.isHueAutoEnable(),
                control.getHueAuto());

        // Saturation
        // 饱和度
        setSeekBarParams(
                mBinding.isbSaturation,
                control.isSaturationEnable(),
                control.updateSaturationLimit(),
                control.getSaturation());

        // Sharpness
        // 锐度
        setSeekBarParams(
                mBinding.isbSharpness,
                control.isSharpnessEnable(),
                control.updateSharpnessLimit(),
                control.getSharpness());

        // Gamma
        // 伽马
        setSeekBarParams(
                mBinding.isbGamma,
                control.isGammaEnable(),
                control.updateGammaLimit(),
                control.getGamma());

        // White Balance
        // 白平衡
        setSeekBarParams(
                mBinding.isbWhiteBalance,
                control.isWhiteBalanceEnable(),
                control.updateWhiteBalanceLimit(),
                control.getWhiteBalance());
        if (control.isWhiteBalanceAutoEnable()) {
            mBinding.isbWhiteBalance.setEnabled(false);
        }
        // White Balance Auto
        // 白平衡自动
        setCheckBoxParams(
                mBinding.cbWhiteBalanceAuto,
                control.isWhiteBalanceAutoEnable(),
                control.getWhiteBalanceAuto());

        // Backlight Compensation
        // 背光补偿
        setSeekBarParams(
                mBinding.isbBacklightComp,
                control.isBacklightCompEnable(),
                control.updateBacklightCompLimit(),
                control.getBacklightComp());

        // Gain
        // 增益
        setSeekBarParams(
                mBinding.isbGain,
                control.isGainEnable(),
                control.updateGainLimit(),
                control.getGain());

        // Exposure Time
        // 曝光时间
        setSeekBarParams(
                mBinding.isbExposureTime,
                control.isExposureTimeAbsoluteEnable(),
                control.updateExposureTimeAbsoluteLimit(),
                control.getExposureTimeAbsolute());
        if (control.isAutoExposureModeEnable()) {
            mBinding.isbExposureTime.setEnabled(false);
        }
        // Auto-Exposure Mode
        // 自动曝光模式
        setCheckBoxParams(
                mBinding.cbExposureTimeAuto,
                control.isAutoExposureModeEnable(),
                control.isExposureTimeAuto());

        // Iris
        // 光圈
        setSeekBarParams(
                mBinding.isbIris,
                control.isIrisAbsoluteEnable(),
                control.updateIrisAbsoluteLimit(),
                control.getIrisAbsolute());

        // Focus
        // 对焦
        setSeekBarParams(
                mBinding.isbFocus,
                control.isFocusAbsoluteEnable(),
                control.updateFocusAbsoluteLimit(),
                control.getFocusAbsolute());
        if (control.isFocusAutoEnable()) {
            mBinding.isbFocus.setEnabled(false);
        }
        // Focus Auto
        // 对焦自动
        setCheckBoxParams(
                mBinding.cbFocusAuto,
                control.isFocusAutoEnable(),
                control.getFocusAuto());

        // Zoom
        // 变焦
        setSeekBarParams(
                mBinding.isbZoom,
                control.isZoomAbsoluteEnable(),
                control.updateZoomAbsoluteLimit(),
                control.getZoomAbsolute());

        // Pan
        // 平移
        setSeekBarParams(
                mBinding.isbPan,
                control.isPanAbsoluteEnable(),
                control.updatePanAbsoluteLimit(),
                control.getPanAbsolute());

        // Tilt
        // 倾斜
        setSeekBarParams(
                mBinding.isbTilt,
                control.isTiltAbsoluteEnable(),
                control.updateTiltAbsoluteLimit(),
                control.getTiltAbsolute());

        // Roll
        // 滚转
        setSeekBarParams(
                mBinding.isbRoll,
                control.isRollAbsoluteEnable(),
                control.updateRollAbsoluteLimit(),
                control.getRollAbsolute());

        // Power Line Frequency
        // 电源频率
        setPowerLineFrequencyRadioGroup(
                mBinding.rgPowerLineFrequency,
                control.isPowerlineFrequencyEnable(),
                control.updatePowerlineFrequencyLimit(),
                control.getPowerlineFrequency());
    }

    private void setAllControlChangeListener(UVCControl controls) {
        // Brightness
        // 亮度
        mBinding.isbBrightness.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setBrightness(seekParams.progress));

        // Contrast
        // 对比度
        mBinding.isbContrast.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setContrast(seekParams.progress));
        // Contrast Auto
        // 对比度自动
        mBinding.cbContrastAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Before enable Contrast Auto, must reset Contrast
                // 启用对比度自动之前，必须先重置对比度
                controls.resetContrast();
                setSeekBarParams(
                        mBinding.isbContrast,
                        controls.isContrastEnable(),
                        controls.updateContrastLimit(),
                        controls.getContrast());
                mBinding.isbContrast.setEnabled(false);
            } else {
                mBinding.isbContrast.setEnabled(true);
            }
            controls.setContrastAuto(isChecked);
        });

        // Hue
        // 色调
        mBinding.isbHue.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setHue(seekParams.progress));
        // Hue Auto
        // 色调自动
        mBinding.cbHueAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Before enable Hue Auto, must reset Hue
                // 启用色调自动之前，必须先重置色调
                controls.resetHue();
                setSeekBarParams(
                        mBinding.isbHue,
                        controls.isHueEnable(),
                        controls.updateHueLimit(),
                        controls.getHue());
                mBinding.isbHue.setEnabled(false);
            } else {
                mBinding.isbHue.setEnabled(true);
            }
            controls.setHueAuto(isChecked);
        });

        // Saturation
        // 饱和度
        mBinding.isbSaturation.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setSaturation(seekParams.progress));
        // Sharpness
        // 锐度
        mBinding.isbSharpness.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setSharpness(seekParams.progress));
        // Gamma
        // 伽马
        mBinding.isbGamma.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setGamma(seekParams.progress));

        // White Balance
        // 白平衡
        mBinding.isbWhiteBalance.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setWhiteBalance(seekParams.progress));
        // White Balance Auto
        // 白平衡自动
        mBinding.cbWhiteBalanceAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Before enable White Balance Auto, must reset White Balance
                // 启用白平衡自动之前，必须先重置白平衡
                controls.resetWhiteBalance();
                setSeekBarParams(
                        mBinding.isbWhiteBalance,
                        controls.isWhiteBalanceEnable(),
                        controls.updateWhiteBalanceLimit(),
                        controls.getWhiteBalance());
                mBinding.isbWhiteBalance.setEnabled(false);
            } else {
                mBinding.isbWhiteBalance.setEnabled(true);
            }
            controls.setWhiteBalanceAuto(isChecked);
        });

        // Backlight Compensation
        // 背光补偿
        mBinding.isbBacklightComp.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setBacklightComp(seekParams.progress));

        // Gain
        // 增益
        mBinding.isbGain.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setGain(seekParams.progress));

        // Exposure Time
        // 曝光时间
        mBinding.isbExposureTime.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setExposureTimeAbsolute(seekParams.progress));
        // Exposure Time Auto
        // 曝光时间自动
        mBinding.cbExposureTimeAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Before enable Exposure Time Auto, must reset Exposure Time
                // 启用曝光时间自动之前，必须先重置曝光时间
                controls.resetExposureTimeAbsolute();
                setSeekBarParams(
                        mBinding.isbExposureTime,
                        controls.isExposureTimeAbsoluteEnable(),
                        controls.updateExposureTimeAbsoluteLimit(),
                        controls.getExposureTimeAbsolute());
                mBinding.isbExposureTime.setEnabled(false);
            } else {
                mBinding.isbExposureTime.setEnabled(true);
            }
            controls.setExposureTimeAuto(isChecked);
        });

        // Iris
        // 光圈
        mBinding.isbIris.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setIrisAbsolute(seekParams.progress));

        // Focus
        // 对焦
        mBinding.isbFocus.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setFocusAbsolute(seekParams.progress));
        // Focus Auto
        // 对焦自动
        mBinding.cbFocusAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Before enable Focus Auto, must reset Focus absolute
                // 启用对焦自动之前，必须先重置绝对对焦值
                controls.resetFocusAbsolute();
                setSeekBarParams(
                        mBinding.isbFocus,
                        controls.isFocusAbsoluteEnable(),
                        controls.updateFocusAbsoluteLimit(),
                        controls.getFocusAbsolute());
                mBinding.isbFocus.setEnabled(false);
            } else {
                mBinding.isbFocus.setEnabled(true);
            }
            controls.setFocusAuto(isChecked);
        });

        // Zoom
        // 变焦
        mBinding.isbZoom.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setZoomAbsolute(seekParams.progress));

        // Pan
        // 平移
        mBinding.isbPan.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setPanAbsolute(seekParams.progress));

        // Tilt
        // 倾斜
        mBinding.isbTilt.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setTiltAbsolute(seekParams.progress));

        // Roll
        // 滚转
        mBinding.isbRoll.setOnSeekChangeListener(
                (MyOnSeekChangeListener) seekParams -> controls.setRollAbsolute(seekParams.progress));

        // Power Line Frequency
        // 电源频率
        mBinding.rgPowerLineFrequency.setOnCheckedChangeListener((group, checkedId) -> {
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
        // 对比度自动
        control.resetContrastAuto();

        // Hue
        // 色调
        control.resetHue();
        // Hue Auto
        // 色调自动
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
        // 白平衡自动
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
        // 对焦自动
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
        // 滚转
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
                case 1: // 50Hz
                    radioGroup.check(R.id.rbPowerLineFrequency50Hz);
                    break;
                case 2: // 60Hz
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
