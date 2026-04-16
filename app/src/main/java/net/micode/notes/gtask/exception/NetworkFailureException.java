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
/*
 * 文件名: NetworkFailureException.java
 * 功能: 自定义受检异常类，用于明确表示在GTask同步过程中发生的网络相关故障
 * 作者: The MiCode Open Source Community (www.micode.net)
 * 创建时间: 2010-2011年间
 * 修改记录: 无
 *
 * 版权声明:
 * 本文件遵循 Apache License, Version 2.0 开源协议
 * 许可证详细信息请访问: http://www.apache.org/licenses/LICENSE-2.0
 * 核心条款摘要:
 *   1. 允许使用者自由使用、复制、修改、分发本软件
 *   2. 需保留原始版权声明和许可证文本
 *   3. 按"原样"提供，不提供任何明示或暗示的担保
 *   4. 不对因使用本软件而产生的任何损害承担责任
 */

// 包声明: 定义类的命名空间
// 包路径层级说明:
//   net.micode.notes      - 小米便签项目的基础包
//   gtask                 - Google Task同步功能模块
//   exception             - 异常处理专用包，集中管理所有自定义异常
package net.micode.notes.gtask.exception;

/**
 * 类名: NetworkFailureException
 * 描述: 自定义的受检异常(Checked Exception)，专门封装GTask同步过程中的网络层故障
 *
 * 核心设计差异(与之前看到的ActionFailureException对比):
 * 1. 继承自Exception而非RuntimeException → 这是"受检异常"，调用者必须处理
 * 2. 专注于网络层故障，而非通用的操作失败
 * 3. 用于必须显式处理的、可预见的网络异常场景
 *
 * 异常分类体系中的定位:
 *   NetworkFailureException → Exception → Throwable
 *   (同级)ActionFailureException → RuntimeException → Exception → Throwable
 *
 * 主要应用场景:
 * 1. 网络连接不可用(无网络、信号弱)
 * 2. 网络超时(连接超时、读取超时)
 * 3. HTTP错误状态码(404, 500等)
 * 4. SSL/TLS握手失败
 * 5. 域名解析失败(DNS错误)
 *
 * 为什么设计为受检异常(Checked Exception):
 * 1. 网络故障是相对"可预见"的异常情况
 * 2. 调用者必须显式处理，不能忽略
 * 3. 强制开发者编写健壮的错误处理逻辑
 * 4. 提高代码的可读性和可维护性
 *
 * 典型处理模式:
 *   try {
 *       gTaskSyncService.fetchDataFromServer();
 *   } catch (NetworkFailureException e) {
 *       // 必须处理: 如重试、缓存、用户提示等
 *       handleNetworkError(e);
 *   }
 *
 * 注意事项:
 * 1. 不要在方法内部吞没此异常(除非有明确的恢复策略)
 * 2. 建议在异常消息中包含具体的网络错误细节
 * 3. 通过异常链(Throwable cause)保留底层网络库的原始异常
 * 4. UI层应捕获此异常并给用户友好的网络错误提示
 */
public class NetworkFailureException extends Exception {

    /**
     * serialVersionUID: 序列化版本唯一标识符
     * 作用: 在对象序列化和反序列化过程中验证版本一致性
     * 值说明: 2107610287180234136L
     *         - 这是由开发者显式声明的64位长整型常量
     *         - 不同于ActionFailureException的值，确保两个异常类独立序列化
     * 重要性: 如果类的结构(字段、方法)发生变化，应考虑更新此值
     *         否则可能导致反序列化时出现InvalidClassException
     * 最佳实践: 使用IDE工具生成或记录下这个值，确保团队一致性
     */
    private static final long serialVersionUID = 2107610287180234136L;

    /**
     * 方法名: NetworkFailureException (无参构造方法)
     * 功能: 创建仅有异常类型标识，不包含详细信息的网络故障异常
     * 使用场景: 当网络故障原因未知或不需要详细描述时
     * 设计考量: 提供最简化的异常创建方式，但实际开发中不推荐使用
     *
     * 示例(不推荐):
     *   if (!isNetworkAvailable()) {
     *       throw new NetworkFailureException(); // 仅知道网络故障，不知具体原因
     *   }
     *
     * 更好的做法是使用带消息的构造方法，提供具体错误信息
     */
    public NetworkFailureException() {
        super(); // 调用父类Exception的无参构造方法
    }

    /**
     * 方法名: NetworkFailureException (带消息构造方法)
     * 功能: 创建包含自定义错误消息的网络故障异常
     *
     * @param paramString 错误描述信息
     *                    类型: String
     *                    描述: 人类可读的详细错误描述，应包含具体故障信息
     *                    建议内容格式:
     *                      - "连接Google服务器超时，已重试3次"
     *                      - "HTTP 502错误: 网关故障"
     *                      - "SSL证书验证失败: 证书已过期"
     *                    注意事项: 避免使用过于技术性的术语，兼顾开发调试和日志可读性
     *
     * 典型使用场景:
     *   1. 捕获底层网络异常后，转换为更有业务意义的描述
     *   2. 在多层调用中添加上下文信息
     *
     * 示例:
     *   try {
     *       HttpResponse response = httpClient.execute(request);
     *   } catch (SocketTimeoutException e) {
     *       // 将底层异常转换为业务异常，添加上下文
     *       throw new NetworkFailureException("同步任务数据时连接超时(30秒)");
     *   }
     */
    public NetworkFailureException(String paramString) {
        super(paramString); // 调用父类Exception的带消息构造方法
    }

    /**
     * 方法名: NetworkFailureException (带消息和原因链的构造方法)
     * 功能: 创建包含错误消息和根本原因的网络故障异常，支持完整的异常链追踪
     *
     * @param paramString 错误描述信息
     *                    类型: String
     *                    描述: 业务层的错误摘要，说明网络故障的影响
     *                    示例: "无法从Google Task获取待办事项列表"
     *
     * @param paramThrowable 导致此网络故障的原始异常(根本原因)
     *                       类型: Throwable
     *                       描述: 底层网络库抛出的具体技术异常
     *                       常见值:
     *                         - java.net.UnknownHostException: 域名解析失败
     *                         - java.net.SocketTimeoutException: 套接字超时
     *                         - javax.net.ssl.SSLHandshakeException: SSL握手失败
     *                         - java.io.IOException: 通用I/O错误
     *                         - 第三方HTTP库的特定异常(如OkHttp的异常)
     *                       重要性: 保留原始异常对于问题诊断至关重要
     *
     * 异常链的价值:
     *   1. 调试: 可以通过e.getCause()追溯到最底层的技术异常
     *   2. 日志: 打印堆栈时包含完整调用链
     *   3. 处理: 可以根据具体的异常类型采取不同的恢复策略
     *
     * 最佳实践示例:
     *   public void syncWithRetry() throws NetworkFailureException {
     *       try {
     *           // 尝试网络请求
     *           performNetworkRequest();
     *       } catch (ConnectException e) {
     *           // 包装并添加上下文
     *           throw new NetworkFailureException(
     *               "服务器连接被拒绝，可能服务未启动",
     *               e
     *           );
     *       } catch (SocketTimeoutException e) {
     *           // 不同的异常类型，不同的处理逻辑
     *           if (retryCount < MAX_RETRY) {
     *               retryCount++;
     *               syncWithRetry(); // 重试
     *           } else {
     *               throw new NetworkFailureException(
     *                   "服务器响应超时，已重试" + MAX_RETRY + "次",
     *                   e
     *               );
     *           }
     *       }
     *   }
     */
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable); // 调用父类Exception的带消息和原因的构造方法
    }

    // 备注: 此类继承了Exception的所有标准方法
    // 1. getMessage(): 获取构造时传入的错误消息
    // 2. getCause(): 获取导致此异常的原始Throwable对象
    // 3. printStackTrace(): 打印完整的异常堆栈跟踪
    // 4. toString(): 返回异常的字符串表示
    //
    // 如需特殊行为(如自定义日志格式)，可重写这些方法，但通常不需要
}