package com.serenegiant.usb;

import androidx.annotation.NonNull;

public class UVCParam implements Cloneable {

    /**
     * Preview size
     * <p>
     * 预览尺寸
     */
    private Size previewSize;
    /**
     * Enable some quirks to resolve specific issues
     * <p>
     * 启用某些兼容性处理（quirks）以解决特定问题
     */
    private int quirks;

    public UVCParam() {
    }

    public UVCParam(Size previewSize, int quirks) {
        this.previewSize = previewSize;
        this.quirks = quirks;
    }

    public Size getPreviewSize() {
        return previewSize;
    }

    public void setPreviewSize(Size previewSize) {
        this.previewSize = previewSize;
    }

    public int getQuirks() {
        return quirks;
    }

    public void setQuirks(int quirks) {
        this.quirks = quirks;
    }

    @NonNull
    @Override
    protected Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            return new UVCParam(previewSize, quirks);
        }
    }
}
