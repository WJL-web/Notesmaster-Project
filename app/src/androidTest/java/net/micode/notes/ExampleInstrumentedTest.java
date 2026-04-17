package net.micode.notes;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * 【仪器化测试】
 * 这类测试会**运行在真实Android设备或模拟器**上
 * 可以使用完整的Android框架API、Context、资源等
 *
 * 作用：验证APP运行环境是否正确、包名是否匹配
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    /**
     * 测试方法：验证应用的 Context 和包名是否正确
     */
    @Test
    public void useAppContext() {
        // 获取当前被测试应用的 Context
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // 断言：检查包名是否等于 "net.micode.notes"
        // 如果相等 → 测试通过；不相等 → 测试失败
        assertEquals("net.micode.notes", appContext.getPackageName());
    }
}