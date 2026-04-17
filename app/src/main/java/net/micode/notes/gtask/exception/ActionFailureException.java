/*
 * 版权声明 (c) 2010-2011, 米代码开源社区 (www.micode.net)
 *
 * 根据 Apache License 2.0 版本（“许可证”）授权；
 * 除非遵守许可证，否则您不得使用此文件。
 * 您可以在以下网址获取许可证副本：
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，根据许可证分发的软件
 * 是按“原样”基础分发的，不附带任何明示或暗示的担保或条件。
 * 请参阅许可证以了解特定语言下的权限和限制。
 */

/**
 * 包名：net.micode.notes.gtask.exception
 * 该包包含Google Tasks同步功能相关的自定义异常类
 */
package net.micode.notes.gtask.exception;

/**
 * ActionFailureException 类 - 操作失败异常
 *
 * 作用：表示在Google Tasks同步过程中，某个操作执行失败时抛出的运行时异常
 *
 * 继承关系：
 * - 继承自 RuntimeException（运行时异常）
 * - 属于非受检异常（Unchecked Exception），不需要在方法签名中显式声明throws
 *
 * 使用场景：
 * 1. JSON数据构建失败时（如创建/更新任务的JSON生成错误）
 * 2. 数据库操作失败时（如插入便签后无法获取生成的ID）
 * 3. 网络请求失败或响应解析错误时
 * 4. 任何导致同步操作无法继续的异常情况
 *
 * 为什么选择继承 RuntimeException：
 * - 同步过程中的错误通常是不可恢复的，不需要强制调用方处理
 * - 简化代码，避免到处写 try-catch
 * - 在合适的地方统一捕获并处理
 */
public class ActionFailureException extends RuntimeException {

    /**
     * 序列化版本UID
     *
     * 作用：用于对象的序列化和反序列化
     * 当异常对象需要在进程间传递（如通过Binder）或保存到文件时使用
     *
     * 说明：
     * - private static final 修饰，属于类常量
     * - 显式声明可以保证不同版本的JVM序列化兼容性
     * - 如果不声明，JVM会自动生成一个，但可能因编译器实现不同而导致兼容性问题
     */
    private static final long serialVersionUID = 4425249765923293627L;

    /**
     * 无参构造函数
     *
     * 创建一个没有详细消息和原因的异常实例
     *
     * 使用场景：仅需要知道发生了操作失败，不需要额外信息时
     */
    public ActionFailureException() {
        super();  // 调用父类RuntimeException的无参构造函数
    }

    /**
     * 带消息参数的构造函数
     *
     * 创建一个带有详细消息的异常实例
     *
     * @param paramString 异常详细信息，用于描述失败的具体原因
     *
     * 使用示例：
     * throw new ActionFailureException("fail to generate task-create jsonobject");
     * throw new ActionFailureException("create note failed");
     */
    public ActionFailureException(String paramString) {
        super(paramString);  // 调用父类RuntimeException的单参构造函数
    }

    /**
     * 带消息和原因的构造函数
     *
     * 创建一个带有详细消息和原始原因的异常实例
     *
     * @param paramString 异常详细信息
     * @param paramThrowable 原始异常（导致当前异常的根本原因）
     *
     * 使用场景：包装捕获到的其他异常，保留原始的异常堆栈信息
     *
     * 使用示例：
     * try {
     *     // 某些操作
     * } catch (JSONException e) {
     *     throw new ActionFailureException("JSON parse failed", e);
     * }
     */
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);  // 调用父类RuntimeException的双参构造函数
    }
}