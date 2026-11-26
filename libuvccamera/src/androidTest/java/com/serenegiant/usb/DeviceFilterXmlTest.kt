package com.serenegiant.usb

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import com.serenegiant.uvccamera.R as LibraryR
import com.serenegiant.uvccamera.test.R as TestR

/**
 * Checks reading device filters from XML resources, as the library does to find UVC cameras.
 *
 * 检查从 XML 资源读取设备过滤器，库正是这样查找 UVC 相机的。
 */
@RunWith(AndroidJUnit4::class)
class DeviceFilterXmlTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun filters(xmlId: Int): List<DeviceFilter> = DeviceFilter.getDeviceFilters(context, xmlId)

    @Test
    fun libraryFilterSelectsVideoAndMiscellaneousClassDevices() {
        val filters = filters(LibraryR.xml.device_filter)

        assertEquals(2, filters.size)
        // Interface class 0Eh (Video) / 接口类 0Eh(视频)
        assertFilter(filters[0], clasz = 14)
        // Interface class EFh (Miscellaneous), subclass 2 / 接口类 EFh(杂项)，子类 2
        assertFilter(filters[1], clasz = 239, subclass = 2)
    }

    @Test
    fun readsHexAndDecimalNumbersAndNames() {
        val filter = filters(TestR.xml.test_device_filters)[0]

        assertFilter(filter, vendorId = 0x046d, productId = 2085, clasz = 239, subclass = 2, protocol = 1)
        assertEquals("Acme", filter.mManufacturerName)
        assertEquals("Webcam", filter.mProductName)
        assertEquals("1234", filter.mSerialNumber)
    }

    @Test
    fun readsTheOlderAttributeNames() {
        val filter = filters(TestR.xml.test_device_filters)[1]

        assertFilter(filter, vendorId = 1133, productId = 0x0825)
        assertEquals("Old Acme", filter.mManufacturerName)
        assertEquals("Old Webcam", filter.mProductName)
        assertEquals("5678", filter.mSerialNumber)
    }

    @Test
    fun readsTheExcludeFlag() {
        val filters = filters(TestR.xml.test_device_filters)

        assertFilter(filters[2], vendorId = 1, exclude = true)
        assertFilter(filters[3], clasz = 14, exclude = false)
    }

    @Test
    fun unreadableNumberMeansAny() {
        assertEquals(-1, filters(TestR.xml.test_device_filters)[3].mVendorId)
    }

    @Test
    fun readsValuesFromResources() {
        val filter = filters(TestR.xml.test_device_filters)[4]

        assertFilter(filter, vendorId = 4660, exclude = true)
        assertEquals("Referenced Cam", filter.mProductName)
    }

    @Test
    fun readsEveryEntry() {
        assertEquals(5, filters(TestR.xml.test_device_filters).size)
    }

    private fun assertFilter(
        filter: DeviceFilter,
        vendorId: Int = -1,
        productId: Int = -1,
        clasz: Int = -1,
        subclass: Int = -1,
        protocol: Int = -1,
        exclude: Boolean = false,
    ) {
        assertEquals("vendor id of $filter", vendorId, filter.mVendorId)
        assertEquals("product id of $filter", productId, filter.mProductId)
        assertEquals("class of $filter", clasz, filter.mClass)
        assertEquals("subclass of $filter", subclass, filter.mSubclass)
        assertEquals("protocol of $filter", protocol, filter.mProtocol)
        assertEquals("exclude of $filter", exclude, filter.isExclude)
    }
}
