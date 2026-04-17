/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 示例单元测试类
 *
 * 该类是一个简单的单元测试示例，用于演示如何在开发环境中编写和运行单元测试。
 * 单元测试在开发机器上的 JVM 中执行，不需要 Android 设备或模拟器。
 *
 * 主要功能：
 * - 验证基本的算术运算是否正确
 * - 作为编写其他单元测试的模板参考
 *
 * 使用场景：
 * - 测试纯 Java 逻辑（不依赖 Android API 的代码）
 * - 验证工具类、算法、数据模型等
 *
 * 注意：
 * - 此类测试运行在本地 JVM 中，不是 Android 运行时环境
 * - 如果测试需要 Android API，应使用 Instrumentation 测试
 *
 * @author MiCode Open Source Community
 * @see <a href="http://d.android.com/tools/testing">Android 测试文档</a>
 */
public class ExampleUnitTest {

    /**
     * 测试加法运算的正确性
     *
     * 测试方法命名规范：通常使用 "test" 前缀 + 被测试功能描述
     *
     * 验证内容：
     * - 计算 2 + 2 的结果
     * - 期望结果为 4
     *
     * 断言说明：
     * - assertEquals(expected, actual): 比较期望值和实际值是否相等
     * - 如果相等，测试通过；否则测试失败
     */
    @Test
    public void addition_isCorrect() {
        // 断言：2 + 2 应该等于 4
        assertEquals(4, 2 + 2);
    }
}