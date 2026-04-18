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
 * 文件名: ActionFailureException.java
 * 功能: 自定义运行时异常类，用于表示在GTask同步操作中发生的操作失败异常
 * 作者: MiCode开源社区
 * 创建时间: 2010-2011年间
 * 修改记录: 无
 *
 * 版权声明:
 * 本文件遵循Apache License 2.0开源协议
 * 详细许可证信息请参考: http://www.apache.org/licenses/LICENSE-2.0
 */

// 包声明: 定义当前类所在的包路径
// 包结构说明:
//   net.micode.notes       - 项目基础包
//   gtask                  - Google任务同步相关模块
//   exception              - 异常处理包，存放自定义异常类
package net.micode.notes.gtask.exception;

/**
 * 类名: ActionFailureException
 * 描述: 自定义运行时异常(RuntimeException)的子类，专门用于表示GTask同步操作失败
 *
 * 主要用途:
 * 1. 在Google Task同步过程中，当某个操作(如网络请求、数据处理、同步等)失败时抛出
 * 2. 封装操作失败的详细信息，便于上层调用者捕获和处理
 * 3. 支持异常链(exception chaining)，可以追溯根本原因
 *
 * 异常类型: RuntimeException(运行时异常)
 * 选择原因: 不需要在方法签名中显式声明，适用于那些程序无法提前预知的错误
 *          如: 网络中断、服务器异常、数据格式错误等
 *
 * 设计模式: 自定义异常模式
 * 继承关系: ActionFailureException → RuntimeException → Exception → Throwable
 *
 * 注意事项:
 * 1. 这是一个非受检异常(unchecked exception)，不需要try-catch强制处理
 * 2. 但建议在适当的逻辑层捕获并处理此异常
 * 3. 通常用于GTask同步模块内部，上层UI层应捕获并给用户友好提示
 */
public class ActionFailureException extends RuntimeException {

    /**
     * serialVersionUID: 序列化版本UID
     * 作用: 用于对象的序列化和反序列化版本控制
     * 值说明: 4425249765923293627L
     *         - 这是一个随机生成的64位长整型数值
     *         - 当类的结构发生变化时，修改此值可以防止不兼容的序列化
     * 注意事项: 如果修改了类的结构(如增删字段)，应考虑更新此值
     */
    private static final long serialVersionUID = 4425249765923293627L;

    /**
     * 方法名: ActionFailureException (默认构造方法)
     * 功能: 创建一个没有详细错误信息的ActionFailureException实例
     * 使用场景: 当只需要知道操作失败，不需要额外错误信息时
     *
     * 示例:
     *   throw new ActionFailureException();
     *   抛出异常后，异常信息为null，调用getMessage()返回null
     */
    public ActionFailureException() {
        super();  // 调用父类RuntimeException的无参构造方法
    }

    /**
     * 方法名: ActionFailureException (带消息参数的构造方法)
     * 功能: 创建一个包含详细错误信息的ActionFailureException实例
     *
     * @param paramString 错误描述信息
     *                    类型: String
     *                    描述: 人类可读的错误描述，说明操作失败的原因
     *                    取值范围: 任意非空字符串，建议使用具体、清晰的描述
     *                    示例: "网络连接失败，无法同步数据到Google Task"
     *
     * 异常链: 不包含原始异常(Throwable)
     * 使用场景: 当操作失败有明确原因，但不需要记录底层异常时
     *
     * 示例:
     *   throw new ActionFailureException("同步任务时发生网络超时");
     *   抛出异常后，异常信息为"同步任务时发生网络超时"
     */
    public ActionFailureException(String paramString) {
        super(paramString);  // 调用父类RuntimeException的带消息构造方法
    }

    /**
     * 方法名: ActionFailureException (带消息和原因的构造方法)
     * 功能: 创建一个包含错误信息和根本原因的ActionFailureException实例
     *
     * @param paramString 错误描述信息
     *                    类型: String
     *                    描述: 人类可读的错误描述，说明操作失败的原因
     *                    取值范围: 任意非空字符串
     *
     * @param paramThrowable 导致此异常的原始异常/原因
     *                       类型: Throwable
     *                       描述: 引发当前异常的根本原因(底层异常)
     *                       取值范围: 任何Throwable实例或其子类
     *                       常见值: IOException, JSONException, NetworkException等
     *
     * 异常链: 包含原始异常，可以通过getCause()方法获取
     * 使用场景: 当操作失败是由其他异常引起，需要保留完整的异常堆栈信息时
     *
     * 示例:
     *   try {
     *       // 执行网络请求
     *   } catch (IOException e) {
     *       throw new ActionFailureException("网络请求失败", e);
     *   }
     *
     *   这样可以在日志中看到完整的异常链，便于问题排查
     */
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);  // 调用父类RuntimeException的带消息和原因的构造方法
    }

    // 注意: 此类没有覆盖父类的其他方法，如getMessage(), getCause(), printStackTrace()等
    // 这些方法会继承自RuntimeException和Throwable类
    // 如果有特殊需求，可以重写这些方法，但通常不需要
}