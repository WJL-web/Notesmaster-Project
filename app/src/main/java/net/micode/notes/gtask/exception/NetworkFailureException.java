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

package net.micode.notes.gtask.exception;

/**
 * 网络故障异常类
 *
 * 该异常用于表示在与 Google Tasks 服务进行同步时发生的网络相关错误。
 * 当网络连接不可用、请求超时、或服务器无法访问时抛出此异常。
 *
 * 继承自标准的 Exception 类，属于受检异常（Checked Exception），
 * 调用方必须显式处理（try-catch 或 throws）。
 *
 * 典型使用场景：
 * - HTTP 请求发送失败
 * - 网络连接超时
 * - 服务器响应异常（如 5xx 错误）
 * - SSL/TLS 握手失败等网络层问题
 *
 * @author MiCode Open Source Community
 * @see ActionFailureException 动作执行失败异常（与业务逻辑相关）
 */
public class NetworkFailureException extends Exception {

    /**
     * 序列化版本 UID，用于支持对象的序列化和反序列化。
     * 确保不同版本间的类兼容性。
     */
    private static final long serialVersionUID = 2107610287180234136L;

    /**
     * 无参构造方法。
     * 创建一个没有详细错误消息和原因的网络故障异常。
     *
     * 使用示例：
     * throw new NetworkFailureException();
     */
    public NetworkFailureException() {
        super();
    }

    /**
     * 带详细错误消息的构造方法。
     *
     * @param paramString 详细错误消息，描述具体的网络故障原因
     *
     * 使用示例：
     * throw new NetworkFailureException("Network connection timeout");
     */
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带详细错误消息和根本原因的构造方法。
     *
     * @param paramString   详细错误消息
     * @param paramThrowable 导致该异常的根本原因（原始异常），用于异常链追踪
     *
     * 使用示例：
     * try {
     *     httpClient.execute(request);
     * } catch (IOException e) {
     *     throw new NetworkFailureException("HTTP request failed", e);
     * }
     */
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}