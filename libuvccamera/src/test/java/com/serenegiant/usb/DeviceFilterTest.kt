package com.serenegiant.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Checks how filters compare with each other. Matching a [android.hardware.usb.UsbDevice] needs a
 * device, and reading filters from XML needs resources; both are covered by the instrumented tests.
 *
 * 检查过滤器之间的比较。匹配 [android.hardware.usb.UsbDevice] 需要设备，从 XML 读取过滤器需要资源；
 * 二者由仪器化测试覆盖。
 */
class DeviceFilterTest {

    /** A filter for one specific camera, without name strings. / 针对某一款具体相机、不含名称字符串的过滤器。 */
    private fun device(vendorId: Int = 0x046d, productId: Int = 0x0825, exclude: Boolean = false) =
        DeviceFilter(vendorId, productId, 239, 2, 1, null, null, null, exclude)

    /** A filter for one specific camera, with its name strings. / 针对某一款具体相机、含名称字符串的过滤器。 */
    private fun named(serial: String? = "1234", exclude: Boolean = false) =
        DeviceFilter(0x046d, 0x0825, 239, 2, 1, "Acme", "Webcam", serial, exclude)

    /** A filter that only sets the class, subclass and protocol (-1 for any). / 只设置类、子类和协议的过滤器(-1 表示任意)。 */
    private fun byClass(clasz: Int, subclass: Int = -1, protocol: Int = -1, exclude: Boolean = false) =
        DeviceFilter(-1, -1, clasz, subclass, protocol, null, null, null, exclude)

    @Test
    fun classFilterMatchesDevicesOfThatClass() {
        assertTrue(byClass(239).matches(device()))
        assertTrue(byClass(239, subclass = 2).matches(device()))
        assertTrue(byClass(239, subclass = 2, protocol = 1).matches(device()))
        assertFalse(byClass(14).matches(device()))
        assertFalse(byClass(239, subclass = 1).matches(device()))
        assertFalse(byClass(239, protocol = 2).matches(device()))
    }

    @Test
    fun vendorAndProductMustMatchWhenSet() {
        val filter = DeviceFilter(0x046d, 0x0825, -1, -1, -1, null, null, null)

        assertTrue(filter.matches(device()))
        assertFalse(filter.matches(device(vendorId = 0x1234)))
        assertFalse(filter.matches(device(productId = 0x0001)))
    }

    @Test
    fun namesMustMatchWhenBothAreSet() {
        assertTrue(named().matches(named()))
        assertFalse(named().matches(named(serial = "9999")))
    }

    @Test
    fun includeAndExcludeFiltersDontMatch() {
        assertFalse(byClass(239).matches(byClass(239, exclude = true)))
        assertTrue(byClass(239, exclude = true).matches(byClass(239, exclude = true)))
    }

    @Test
    fun identicalFiltersAreEqualWithTheSameHashCode() {
        assertEquals(named(), named())
        assertEquals(named().hashCode(), named().hashCode())
        assertEquals(device(exclude = true), device(exclude = true))
    }

    @Test
    fun filtersThatDifferAreNotEqual() {
        assertNotEquals(named(), named(exclude = true))
        assertNotEquals(named(), named(serial = "9999"))
        assertNotEquals(named(), named(serial = null))
        assertNotEquals(device(), device(productId = 0x0001))
    }

    @Test
    fun filtersWithWildcardsAreOnlyEqualToThemselves() {
        val filter = byClass(14)

        assertEquals(filter, filter)
        // Documented: two filters with an unspecified (-1) number can't be compared.
        // 已有文档说明：含未指定(-1)数值的两个过滤器无法比较。
        assertNotEquals(byClass(14), byClass(14))
    }

    @Test
    fun aListCanRemoveTheFiltersItHolds() {
        // As USBMonitor.removeDeviceFilter does with the library's wildcard filters
        // 正如 USBMonitor.removeDeviceFilter 对库中含通配符的过滤器所做的那样
        val wildcard = byClass(239, subclass = 2)
        val filters = CopyOnWriteArrayList(listOf(wildcard, named()))

        assertTrue(filters.remove(wildcard))
        assertTrue(filters.remove(named()))
        assertTrue(filters.isEmpty())
    }

    @Test
    fun toStringListsEveryField() {
        assertEquals(
            "DeviceFilter[mVendorId=1133,mProductId=2085,mClass=239,mSubclass=2,mProtocol=1," +
                "mManufacturerName=Acme,mProductName=Webcam,mSerialNumber=1234,isExclude=false]",
            named().toString(),
        )
    }
}
