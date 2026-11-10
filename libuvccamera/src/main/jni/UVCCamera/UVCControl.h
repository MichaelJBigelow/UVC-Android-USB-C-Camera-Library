#pragma interface

#ifndef UVCCONTROL_H
#define UVCCONTROL_H

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <android/native_window.h>
#include "libUVCCamera.h"

#define    CT_SCANNING_MODE_CONTROL        0x000001    // D0:  Scanning Mode / D0：扫描模式
#define    CT_AE_MODE_CONTROL                0x000002    // D1:  Auto-Exposure Mode / D1：自动曝光模式
#define    CT_AE_PRIORITY_CONTROL    0x000004    // D2:  Auto-Exposure Priority / D2：自动曝光优先级
#define    CT_EXPOSURE_TIME_ABSOLUTE_CONTROL            0x000008    // D3:  Exposure Time (Absolute) / D3：曝光时间（绝对）
#define    CT_EXPOSURE_TIME_RELATIVE_CONTROL            0x000010    // D4:  Exposure Time (Relative) / D4：曝光时间（相对）
#define CT_FOCUS_ABSOLUTE_CONTROL        0x000020    // D5:  Focus (Absolute) / D5：对焦（绝对）
#define CT_FOCUS_RELATIVE_CONTROL        0x000040    // D6:  Focus (Relative) / D6：对焦（相对）
#define CT_IRIS_ABSOLUTE_CONTROL        0x000080    // D7:  Iris (Absolute) / D7：光圈（绝对）
#define    CT_IRIS_RELATIVE_CONTROL        0x000100    // D8:  Iris (Relative) / D8：光圈（相对）
#define    CT_ZOOM_ABSOLUTE_CONTROL        0x000200    // D9:  Zoom (Absolute) / D9：变焦（绝对）
#define CT_ZOOM_RELATIVE_CONTROL        0x000400    // D10: Zoom (Relative) / D10：变焦（相对）
#define    CT_PANTILT_ABSOLUTE_CONTROL    0x000800    // D11: PanTilt (Absolute) / D11：云台水平/俯仰（绝对）
#define CT_PANTILT_RELATIVE_CONTROL    0x001000    // D12: PanTilt (Relative) / D12：云台水平/俯仰（相对）
#define CT_ROLL_ABSOLUTE_CONTROL        0x002000    // D13: Roll (Absolute) / D13：滚转（绝对）
#define CT_ROLL_RELATIVE_CONTROL        0x004000    // D14: Roll (Relative) / D14：滚转（相对）
// D15: Reserved
// D16: Reserved
// D15：保留
// D16：保留
#define CT_FOCUS_AUTO_CONTROL        0x020000    // D17: Focus, Auto / D17：自动对焦
#define CT_PRIVACY_CONTROL        0x040000    // D18: Privacy / D18：隐私
#define CT_FOCUS_SIMPLE_CONTROL    0x080000    // D19: Focus, Simple / D19：简易对焦
#define CT_WINDOW_CONTROL            0x100000    // D20: Window / D20：窗口

#define PU_BRIGHTNESS_CONTROL        0x000001    // D0: Brightness / D0：亮度
#define PU_CONTRAST_CONTROL            0x000002    // D1: Contrast / D1：对比度
#define PU_HUE_CONTROL                0x000004    // D2: Hue / D2：色调
#define    PU_SATURATION_CONTROL        0x000008    // D3: Saturation / D3：饱和度
#define PU_SHARPNESS_CONTROL        0x000010    // D4: Sharpness / D4：锐度
#define PU_GAMMA_CONTROL            0x000020    // D5: Gamma / D5：伽马
#define    PU_WHITE_BALANCE_TEMPERATURE_CONTROL            0x000040    // D6: White Balance Temperature / D6：白平衡色温
#define    PU_WHITE_BALANCE_COMPONENT_CONTROL            0x000080    // D7: White Balance Component / D7：白平衡分量
#define    PU_BACKLIGHT_COMPENSATION_CONTROL        0x000100    // D8: Backlight Compensation / D8：背光补偿
#define PU_GAIN_CONTROL                0x000200    // D9: Gain / D9：增益
#define PU_POWER_LINE_FREQUENCY_CONTROL            0x000400    // D10: Power Line Frequency / D10：电源频率
#define PU_HUE_AUTO_CONTROL            0x000800    // D11: Hue, Auto / D11：自动色调
#define PU_WHITE_BALANCE_TEMPERATURE_AUTO_CONTROL        0x001000    // D12: White Balance Temperature, Auto / D12：自动白平衡色温
#define PU_WHITE_BALANCE_COMPONENT_AUTO_CONTROL    0x002000    // D13: White Balance Component, Auto / D13：自动白平衡分量
#define PU_DIGITAL_MULTIPLIER_CONTROL        0x004000    // D14: Digital Multiplier / D14：数字倍率
#define PU_DIGITAL_MULTIPLIER_LIMIT_CONTROL    0x008000    // D15: Digital Multiplier Limit / D15：数字倍率上限
#define PU_ANALOG_VIDEO_STANDARD_CONTROL        0x010000    // D16: Analog Video Standard / D16：模拟视频标准
#define PU_ANALOG_LOCK_STATUS_CONTROL        0x020000    // D17: Analog Video Lock Status / D17：模拟视频锁定状态
#define PU_CONTRAST_AUTO_CONTROL    0x040000    // D18: Contrast, Auto / D18：自动对比度

typedef struct control_value {
    int res;    // unused / 未使用
    int min;
    int max;
    int def;
    int current;
} control_value_t;

typedef uvc_error_t (*paramget_func_i8)(uvc_device_handle_t *devh, int8_t *value,
                                        enum uvc_req_code req_code);

typedef uvc_error_t (*paramget_func_i16)(uvc_device_handle_t *devh, int16_t *value,
                                         enum uvc_req_code req_code);

typedef uvc_error_t (*paramget_func_i32)(uvc_device_handle_t *devh, int32_t *value,
                                         enum uvc_req_code req_code);

typedef uvc_error_t (*paramget_func_u8)(uvc_device_handle_t *devh, uint8_t *value,
                                        enum uvc_req_code req_code);

typedef uvc_error_t (*paramget_func_u16)(uvc_device_handle_t *devh, uint16_t *value,
                                         enum uvc_req_code req_code);

typedef uvc_error_t (*paramget_func_u32)(uvc_device_handle_t *devh, uint32_t *value,
                                         enum uvc_req_code req_code);

typedef uvc_error_t (*paramget_func_u8u8)(uvc_device_handle_t *devh, uint8_t *value1,
                                          uint8_t *value2, enum uvc_req_code req_code);

typedef uvc_error_t (*paramget_func_i8u8)(uvc_device_handle_t *devh, int8_t *value1,
                                          uint8_t *value2, enum uvc_req_code req_code);

typedef uvc_error_t (*paramget_func_i8u8u8)(uvc_device_handle_t *devh, int8_t *value1,
                                            uint8_t *value2, uint8_t *value3,
                                            enum uvc_req_code req_code);

typedef uvc_error_t (*paramget_func_i32i32)(uvc_device_handle_t *devh, int32_t *value1,
                                            int32_t *value2, enum uvc_req_code req_code);

typedef uvc_error_t (*paramset_func_i8)(uvc_device_handle_t *devh, int8_t value);

typedef uvc_error_t (*paramset_func_i16)(uvc_device_handle_t *devh, int16_t value);

typedef uvc_error_t (*paramset_func_i32)(uvc_device_handle_t *devh, int32_t value);

typedef uvc_error_t (*paramset_func_u8)(uvc_device_handle_t *devh, uint8_t value);

typedef uvc_error_t (*paramset_func_u16)(uvc_device_handle_t *devh, uint16_t value);

typedef uvc_error_t (*paramset_func_u32)(uvc_device_handle_t *devh, uint32_t value);

typedef uvc_error_t (*paramset_func_u8u8)(uvc_device_handle_t *devh, uint8_t value1,
                                          uint8_t value2);

typedef uvc_error_t (*paramset_func_i8u8)(uvc_device_handle_t *devh, int8_t value1, uint8_t value2);

typedef uvc_error_t (*paramset_func_i8u8u8)(uvc_device_handle_t *devh, int8_t value1,
                                            uint8_t value2, uint8_t value3);

typedef uvc_error_t (*paramset_func_i32i32)(uvc_device_handle_t *devh, int32_t value1,
                                            int32_t value2);

class UVCControl {
private:
    uvc_device_handle_t *mDeviceHandle;
    // indicating the availability of certain camera controls
    // 表示某些相机控制项是否可用
    uint64_t mCTControls;
    // indicating the availability of certain processing Controls
    // 表示某些处理单元控制项是否可用
    uint64_t mPUControls;
    pthread_mutex_t mRequestMutex;
    control_value_t mScanningMode;
    control_value_t mAutoExposureMode;
    control_value_t mAutoExposurePriority;
    control_value_t mExposureTimeAbsolute;
    control_value_t mExposureTimeRelative;
    control_value_t mFocusAbsolute;
    control_value_t mFocusRelative;
    control_value_t mIrisAbsolute;
    control_value_t mIrisRelative;
    control_value_t mZoomAbsolute;
    control_value_t mZoomRelative;
    control_value_t mPanAbsolute;
    control_value_t mTiltAbsolute;
    control_value_t mPanRelative;
    control_value_t mTiltRelative;
    control_value_t mRollAbsolute;
    control_value_t mRollRelative;
    control_value_t mFocusAuto;
    control_value_t mPrivacy;
    control_value_t mFocusSimple;
    control_value_t mWhiteBalanceAuto;
    control_value_t mWhiteBalanceCompoAuto;
    control_value_t mWhiteBalance;
    control_value_t mWhiteBalanceCompo;
    control_value_t mBacklightComp;
    control_value_t mBrightness;
    control_value_t mContrast;
    control_value_t mContrastAuto;
    control_value_t mSharpness;
    control_value_t mGain;
    control_value_t mGamma;
    control_value_t mSaturation;
    control_value_t mHue;
    control_value_t mHueAuto;
    control_value_t mPowerlineFrequency;
    control_value_t mMultiplier;
    control_value_t mMultiplierLimit;
    control_value_t mAnalogVideoStandard;
    control_value_t mAnalogVideoLockState;

    void clearControlParams();

    int internalSetCtrlValue(control_value_t &values, int8_t value,
                             paramget_func_i8 get_func, paramset_func_i8 set_func);

    int internalSetCtrlValue(control_value_t &values, uint8_t value,
                             paramget_func_u8 get_func, paramset_func_u8 set_func);

    int internalSetCtrlValue(control_value_t &values, uint8_t value1, uint8_t value2,
                             paramget_func_u8u8 get_func, paramset_func_u8u8 set_func);

    int internalSetCtrlValue(control_value_t &values, int8_t value1, uint8_t value2,
                             paramget_func_i8u8 get_func, paramset_func_i8u8 set_func);

    int internalSetCtrlValue(control_value_t &values, int8_t value1, uint8_t value2, uint8_t value3,
                             paramget_func_i8u8u8 get_func, paramset_func_i8u8u8 set_func);

    int internalSetCtrlValue(control_value_t &values, int16_t value,
                             paramget_func_i16 get_func, paramset_func_i16 set_func);

    int internalSetCtrlValue(control_value_t &values, uint16_t value,
                             paramget_func_u16 get_func, paramset_func_u16 set_func);

    int internalSetCtrlValue(control_value_t &values, int32_t value,
                             paramget_func_i32 get_func, paramset_func_i32 set_func);

    int internalSetCtrlValue(control_value_t &values, uint32_t value,
                             paramget_func_u32 get_func, paramset_func_u32 set_func);

public:
    UVCControl(uvc_device_handle_t *devh);

    ~UVCControl();

    int getCameraTerminalControls(uint64_t *supports);

    int getProcessingUnitControls(uint64_t *supports);

    int obtainScanningModeLimit(int &min, int &max, int &def);

    int setScanningMode(int mode);

    int getScanningMode();

    int obtainAutoExposureModeLimit(int &min, int &max, int &def);

    int setAutoExposureMode(int mode);

    int getAutoExposureMode();

    int obtainAutoExposurePriorityLimit(int &min, int &max, int &def);

    int setAutoExposurePriority(int priority);

    int getAutoExposurePriority();

    int obtainExposureTimeAbsoluteLimit(int &min, int &max, int &def);

    int setExposureTimeAbsolute(int time);

    int getExposureTimeAbsolute();

    int obtainExposureTimeRelativeLimit(int &min, int &max, int &def);

    int setExposureTimeRelative(int ae_rel);

    int getExposureTimeRelative();

    int obtainFocusAutoLimit(int &min, int &max, int &def);

    int setFocusAuto(bool autoFocus);

    bool getFocusAuto();

    int obtainFocusAbsoluteLimit(int &min, int &max, int &def);

    int setFocusAbsolute(int focus);

    int getFocusAbsolute();

    int obtainFocusRelativeLimit(int &min, int &max, int &def);

    int setFocusRelative(int focus);

    int getFocusRelative();

/*	int obtainFocusSimpleLimit(int &min, int &max, int &def);
	int setFocusSimple(int focus);
	int getFocusSimple(); */

    int obtainIrisAbsoluteLimit(int &min, int &max, int &def);

    int setIrisAbsolute(int iris);

    int getIrisAbsolute();

    int obtainIrisRelativeLimit(int &min, int &max, int &def);

    int setIrisRelative(int iris);

    int getIrisRelative();

    int obtainPanAbsoluteLimit(int &min, int &max, int &def);

    int setPanAbsolute(int pan);

    int getPanAbsolute();

    int obtainTiltAbsoluteLimit(int &min, int &max, int &def);

    int setTiltAbsolute(int tilt);

    int getTiltAbsolute();

    int obtainRollAbsoluteLimit(int &min, int &max, int &def);

    int setRollAbsolute(int roll);

    int getRollAbsolute();

    int updatePanRelativeLimit(int &min, int &max, int &def);

    int setPanRelative(int pan_rel);

    int getPanRelative();

    int updateTiltRelativeLimit(int &min, int &max, int &def);

    int setTiltRelative(int tilt_rel);

    int getTiltRelative();

    int updateRollRelativeLimit(int &min, int &max, int &def);

    int setRollRelative(int roll_rel);

    int getRollRelative();

    int obtainPrivacyLimit(int &min, int &max, int &def);

    int setPrivacy(int privacy);

    int getPrivacy();

    int obtainWhiteBalanceAutoLimit(int &min, int &max, int &def);

    int setWhiteBalanceAuto(bool whiteBalanceAuto);

    bool getWhiteBalanceAuto();

    int obtainWhiteBalanceCompoAutoLimit(int &min, int &max, int &def);

    int setWhiteBalanceCompoAuto(bool whiteBalanceCompoAuto);

    bool getWhiteBalanceCompoAuto();

    int obtainWhiteBalanceLimit(int &min, int &max, int &def);

    int setWhiteBalance(int temp);

    int getWhiteBalance();

    int obtainWhiteBalanceCompoLimit(int &min, int &max, int &def);

    int setWhiteBalanceCompo(int white_balance_compo);

    int getWhiteBalanceCompo();

    int obtainBacklightCompLimit(int &min, int &max, int &def);

    int setBacklightComp(int backlight);

    int getBacklightComp();

    int obtainBrightnessLimit(int &min, int &max, int &def);

    int setBrightness(int brightness);

    int getBrightness();

    int obtainContrastLimit(int &min, int &max, int &def);

    int setContrast(uint16_t contrast);

    int getContrast();

    int obtainContrastAutoLimit(int &min, int &max, int &def);

    int setContrastAuto(bool autoFocus);

    bool getContrastAuto();

    int obtainSharpnessLimit(int &min, int &max, int &def);

    int setSharpness(int sharpness);

    int getSharpness();

    int obtainGainLimit(int &min, int &max, int &def);

    int setGain(int gain);

    int getGain();

    int obtainGammaLimit(int &min, int &max, int &def);

    int setGamma(int gamma);

    int getGamma();

    int obtainSaturationLimit(int &min, int &max, int &def);

    int setSaturation(int saturation);

    int getSaturation();

    int obtainHueLimit(int &min, int &max, int &def);

    int setHue(int hue);

    int getHue();

    int obtainHueAutoLimit(int &min, int &max, int &def);

    int setHueAuto(bool autoFocus);

    bool getHueAuto();

    int obtainPowerlineFrequencyLimit(int &min, int &max, int &def);

    int setPowerlineFrequency(int frequency);

    int getPowerlineFrequency();

    int obtainZoomAbsoluteLimit(int &min, int &max, int &def);

    int setZoomAbsolute(int zoom);

    int getZoomAbsolute();

    int obtainZoomRelativeLimit(int &min, int &max, int &def);

    int setZoomRelative(int zoom);

    int getZoomRelative();

    int obtainDigitalMultiplierLimit(int &min, int &max, int &def);

    int setDigitalMultiplier(int multiplier);

    int getDigitalMultiplier();

    int obtainDigitalMultiplierLimitLimit(int &min, int &max, int &def);

    int setDigitalMultiplierLimit(int multiplier_limit);

    int getDigitalMultiplierLimit();

    int obtainAnalogVideoStandardLimit(int &min, int &max, int &def);

    int setAnalogVideoStandard(int standard);

    int getAnalogVideoStandard();

    int obtainAnalogVideoLockStateLimit(int &min, int &max, int &def);

    int setAnalogVideoLockState(int status);

    int getAnalogVideoLockState();
};

#endif /* UVCCONTROL_H */
