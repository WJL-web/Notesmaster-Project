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
 * 文件名: GTaskSyncService.java
 * 功能: Google Task同步后台服务，在Android后台进程中管理同步任务的启动、取消和状态广播
 * 作者: The MiCode Open Source Community (www.micode.net)
 * 创建时间: 2010-2011
 * 修改记录: 实现了同步服务的完整生命周期管理
 *
 * 核心功能:
 * 1. 服务化同步任务: 将同步操作封装为后台服务，避免在Activity中直接执行
 * 2. 命令式操作: 通过Intent接收开始和取消同步的命令
 * 3. 状态广播: 向UI层广播同步状态和进度信息
 * 4. 生命周期管理: 正确处理服务的创建、启动、停止和内存不足情况
 *
 * 版权声明:
 * 本文件遵循Apache License, Version 2.0开源协议
 * 许可证详细信息请访问: http://www.apache.org/licenses/LICENSE-2.0
 * 核心条款摘要:
 *   1. 允许使用者自由使用、复制、修改、分发本软件
 *   2. 需保留原始版权声明和许可证文本
 *   3. 按"原样"提供，不提供任何明示或暗示的担保
 *   4. 不对因使用本软件而产生的任何损害承担责任
 */

// 包声明: Google Task远程同步服务
package net.micode.notes.gtask.remote;

// Android框架导入
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * 类名: GTaskSyncService
 * 描述: Google Task同步后台服务，继承自Android Service类
 *
 * 主要职责:
 * 1. 同步任务生命周期管理: 启动、执行、取消同步任务
 * 2. 命令接收与分发: 通过Intent接收外部开始/取消同步的命令
 * 3. 状态广播通知: 向注册的广播接收器发送同步进度和状态
 * 4. 单任务控制: 确保同一时间只有一个同步任务在执行
 * 5. 资源清理: 在服务销毁或内存不足时正确清理资源
 *
 * 设计模式: 服务模式 + 命令模式
 * 生命周期: 与Android Service生命周期绑定
 *
 * 工作流程:
 * 1. Activity通过静态方法startSync()启动服务
 * 2. 服务收到ACTION_START_SYNC命令
 * 3. 创建并执行GTaskASyncTask异步任务
 * 4. 通过广播发送同步进度
 * 5. 同步完成后自动停止服务
 * 6. 用户可通过静态方法cancelSync()取消同步
 *
 * 广播机制:
 * 1. 广播名称: "net.micode.notes.gtask.remote.gtask_sync_service"
 * 2. 广播内容:
 *    - 是否正在同步 (GTASK_SERVICE_BROADCAST_IS_SYNCING)
 *    - 同步进度消息 (GTASK_SERVICE_BROADCAST_PROGRESS_MSG)
 * 3. 接收方: 通常是UI层的Activity或Fragment
 *
 * 注意事项:
 * 1. 这是一个Started Service，通过startService()启动
 * 2. 没有实现onBind()，所以不支持绑定(Bound Service)
 * 3. 使用START_STICKY标志，服务被系统杀死后会尝试重新创建
 * 4. 同步状态通过静态变量维护，适合单任务场景
 */
public class GTaskSyncService extends Service {
    /**
     * 意图操作键名: 用于在Intent中标识操作类型
     * 用途: Bundle中存储操作类型的key
     */
    public final static String ACTION_STRING_NAME = "sync_action_type";

    /** 操作类型常量: 开始同步 */
    public final static int ACTION_START_SYNC = 0;

    /** 操作类型常量: 取消同步 */
    public final static int ACTION_CANCEL_SYNC = 1;

    /** 操作类型常量: 无效操作(默认值) */
    public final static int ACTION_INVALID = 2;

    /**
     * 广播名称: 同步服务广播的Action名称
     * 格式: 包名 + 模块名，避免与其他应用广播冲突
     */
    public final static String GTASK_SERVICE_BROADCAST_NAME =
            "net.micode.notes.gtask.remote.gtask_sync_service";

    /** 广播数据键名: 是否正在同步 */
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    /** 广播数据键名: 同步进度消息 */
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    /**
     * 静态同步任务实例: 确保全局只有一个同步任务在执行
     * 设计考虑: 使用静态变量确保单例，但要注意内存泄漏风险
     * 生命周期: 与Application进程相同
     */
    private static GTaskASyncTask mSyncTask = null;

    /**
     * 静态同步进度: 保存当前同步进度消息
     * 用途: 供静态方法getProgressString()获取最新进度
     */
    private static String mSyncProgress = "";

    /**
     * 方法名: startSync
     * 功能: 开始同步操作(内部方法)
     * 流程:
     * 1. 检查是否已有同步任务在执行
     * 2. 创建新的GTaskASyncTask异步任务
     * 3. 设置完成监听器，在同步完成后清理资源
     * 4. 发送开始同步的广播
     * 5. 执行异步任务
     *
     * 同步策略: 同一时间只允许一个同步任务
     * 如果mSyncTask不为null，表示已有同步在进行，不创建新任务
     */
    private void startSync() {
        // 检查是否已有同步任务
        if (mSyncTask == null) {
            // 创建新的异步任务
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                /**
                 * 同步完成回调
                 * 在GTaskASyncTask的onPostExecute()中在新线程调用
                 */
                public void onComplete() {
                    mSyncTask = null;        // 清空任务引用
                    sendBroadcast("");       // 发送空进度广播(表示同步结束)
                    stopSelf();              // 停止服务自身
                }
            });

            // 发送开始同步的广播
            sendBroadcast("");
            // 执行异步任务
            mSyncTask.execute();
        }
        // 如果mSyncTask不为null，说明已有同步在进行，忽略此次请求
    }

    /**
     * 方法名: cancelSync
     * 功能: 取消正在进行的同步操作(内部方法)
     * 机制: 调用GTaskASyncTask的cancelSync()方法设置取消标志
     * 注意: 这是异步取消，实际停止需要GTaskASyncTask内部检查取消标志
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    /**
     * 方法名: onCreate
     * 功能: 服务创建时的回调(Android Service生命周期)
     * 调用时机: 当服务第一次被创建时调用
     * 用途: 初始化服务资源
     *
     * 注意: 可能被多次调用(如果服务被销毁后重新创建)
     */
    @Override
    public void onCreate() {
        mSyncTask = null;  // 确保任务引用为null
    }

    /**
     * 方法名: onStartCommand
     * 功能: 每次通过startService()启动服务时的回调
     * 流程:
     * 1. 从Intent中提取操作类型
     * 2. 根据操作类型执行相应操作(开始/取消同步)
     * 3. 返回服务启动标志
     *
     * @param intent 启动服务的Intent，包含操作类型
     * @param flags 启动标志
     * @param startId 启动ID，用于停止服务时标识
     * @return 服务启动模式标志
     *   START_STICKY: 服务被系统杀死后会重新创建，但Intent为null
     *   START_NOT_STICKY: 服务被杀死后不会自动重新创建
     *   START_REDELIVER_INTENT: 服务被杀死后会重新创建并重新传递Intent
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();

        // 检查Intent中是否包含操作类型
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            // 根据操作类型执行相应操作
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();      // 开始同步
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();     // 取消同步
                    break;
                default:
                    // 无效操作，不执行任何操作
                    break;
            }
            return START_STICKY;  // 返回粘性启动标志
        }

        // 如果没有有效操作，调用父类实现
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 方法名: onLowMemory
     * 功能: 系统内存不足时的回调
     * 用途: 在内存紧张时释放资源，避免应用被系统杀死
     * 机制: 取消正在进行的同步任务，释放相关资源
     *
     * 注意: 这是一个保护性措施，同步会被中断
     */
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();  // 取消同步，释放内存
        }
    }

    /**
     * 方法名: onBind
     * 功能: 绑定服务时的回调(本服务不支持绑定)
     * 返回值: null 表示这是一个Started Service，不是Bound Service
     *
     * @param intent 绑定服务的Intent
     * @return 总是返回null，因为本服务不支持绑定
     */
    public IBinder onBind(Intent intent) {
        return null;  // 返回null表示不支持绑定
    }

    /**
     * 方法名: sendBroadcast
     * 功能: 发送同步状态广播
     * 广播内容:
     * 1. 是否正在同步 (mSyncTask != null)
     * 2. 同步进度消息 (传入的msg参数)
     *
     * @param msg 同步进度消息，由GTaskASyncTask通过publishProgress()提供
     *            示例: "正在登录Google账户..."
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;  // 保存进度到静态变量

        // 创建广播Intent
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);

        // 添加广播数据
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);

        // 发送有序广播
        sendBroadcast(intent);
    }

    // ==================== 静态工具方法 ====================
    // 这些方法供外部(如Activity)调用，封装了服务的启动逻辑

    /**
     * 方法名: startSync (静态)
     * 功能: 启动同步服务(供外部调用)
     * 流程:
     * 1. 设置GTaskManager的Activity上下文(用于获取认证令牌)
     * 2. 创建启动服务的Intent
     * 3. 设置操作为开始同步
     * 4. 启动服务
     *
     * 典型调用场景: 在Activity的同步按钮点击事件中调用
     *
     * @param activity 调用者的Activity，用于启动服务和提供认证上下文
     */
    public static void startSync(Activity activity) {
        // 设置GTaskManager的Activity上下文(重要!)
        GTaskManager.getInstance().setActivityContext(activity);

        // 创建启动服务的Intent
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME,
                GTaskSyncService.ACTION_START_SYNC);

        // 启动服务
        activity.startService(intent);
    }

    /**
     * 方法名: cancelSync (静态)
     * 功能: 取消同步服务(供外部调用)
     * 流程:
     * 1. 创建启动服务的Intent
     * 2. 设置操作为取消同步
     * 3. 启动服务(服务会处理取消命令)
     *
     * @param context 调用者的Context，用于启动服务
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME,
                GTaskSyncService.ACTION_CANCEL_SYNC);

        context.startService(intent);
    }

    /**
     * 方法名: isSyncing (静态)
     * 功能: 检查是否正在同步
     * 用途: UI层查询当前同步状态，决定是否显示取消按钮
     *
     * @return true表示正在同步，false表示没有同步任务
     */
    public static boolean isSyncing() {
        return mSyncTask != null;  // 通过静态任务实例判断
    }

    /**
     * 方法名: getProgressString (静态)
     * 功能: 获取当前同步进度消息
     * 用途: UI层获取最新的进度消息用于显示
     *
     * @return 当前同步进度消息
     */
    public static String getProgressString() {
        return mSyncProgress;  // 返回静态进度变量
    }
}