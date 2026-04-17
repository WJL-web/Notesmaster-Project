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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * Google Tasks 同步服务类

 * 这是一个 Android Service（服务）组件，负责在后台执行与 Google Tasks 的同步操作。
 * 外部组件（如 Activity）通过发送 Intent 来启动或取消同步任务。

 * 主要功能：
 * - 接收外部启动/取消同步的命令（通过 Intent 携带动作类型）
 * - 管理 GTaskASyncTask 的生命周期（创建、执行、取消）
 * - 通过广播向外界发送同步进度和状态信息
 * - 在内存不足时自动取消同步以释放资源

 * 设计模式：
 * - 单例式的任务管理：通过静态变量 mSyncTask 持有当前同步任务实例
 * - 服务生命周期绑定：同步任务完成后自动停止服务（stopSelf）
 *
 * @author MiCode Open Source Community
 * @see GTaskASyncTask 实际的异步同步任务
 * @see Service Android 服务基类
 */
public class GTaskSyncService extends Service {

    // ======================= Intent 动作常量定义 =======================

    /** Intent 中携带动作类型的键名 */
    public final static String ACTION_STRING_NAME = "sync_action_type";

    /** 动作：开始同步 */
    public final static int ACTION_START_SYNC = 0;

    /** 动作：取消同步 */
    public final static int ACTION_CANCEL_SYNC = 1;

    /** 动作：无效（默认值） */
    public final static int ACTION_INVALID = 2;

    // ======================= 广播相关常量 =======================

    /** 同步服务发送广播的 Action 名称（用于接收方注册） */
    public final static String GTASK_SERVICE_BROADCAST_NAME =
            "net.micode.notes.gtask.remote.gtask_sync_service";

    /** 广播中是否正在同步的标志键名 */
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    /** 广播中同步进度消息的键名 */
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    // ======================= 静态成员变量 =======================

    /**
     * 当前正在执行的同步任务实例（静态变量，全局唯一）
     * 使用静态变量是为了让外部组件（如 Activity）可以查询同步状态
     */
    private static GTaskASyncTask mSyncTask = null;

    /** 当前同步进度的文本描述（静态变量，供外部查询） */
    private static String mSyncProgress = "";

    // ======================= 私有方法 =======================

    /**
     * 开始同步任务
     *
     * 如果当前没有正在执行的同步任务，则：
     * 1. 创建新的 GTaskASyncTask 实例
     * 2. 设置完成监听器（同步完成后清空任务引用、发送广播、停止服务）
     * 3. 发送初始广播（通知外界同步已开始）
     * 4. 执行异步任务
     */
    private void startSync() {
        if (mSyncTask == null) {
            // 创建同步任务，并设置完成监听器
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                public void onComplete() {
                    // 同步完成后清空任务引用
                    mSyncTask = null;
                    // 发送最终状态广播（isSyncing = false）
                    sendBroadcast("");
                    // 停止服务自身（因为同步已完成）
                    stopSelf();
                }
            });

            // 发送初始广播（告知外界同步已开始）
            sendBroadcast("");

            // 执行异步任务
            mSyncTask.execute();
        }
    }

    /**
     * 取消正在进行的同步任务

     * 如果存在正在执行的同步任务，则调用其 cancelSync 方法。
     * 任务的取消是协作式的，实际取消逻辑在 GTaskASyncTask 和 GTaskManager 中实现。
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    // ======================= Service 生命周期方法 =======================

    /**
     * 服务创建时回调

     * 初始化静态任务引用为 null。
     * 注意：由于 mSyncTask 是静态变量，可能在其他地方已有值，
     * 但这里重置是为了确保服务启动时的干净状态。
     */
    @Override
    public void onCreate() {
        mSyncTask = null;
    }

    /**
     * 服务启动时回调（每次 startService 都会调用）

     * 解析 Intent 中携带的动作类型，执行相应的操作：
     * - ACTION_START_SYNC：开始同步
     * - ACTION_CANCEL_SYNC：取消同步
     * - 其他：忽略
     *
     * @param intent 启动服务时传入的 Intent，包含动作类型
     * @param flags 启动标志（未使用）
     * @param startId 启动 ID，用于区分多次启动
     * @return START_STICKY 表示服务被杀死后会自动重启（但 Intent 会被丢弃）
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            // 根据动作类型执行相应操作
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            return START_STICKY;  // 服务被杀死后会重启，但 Intent 会丢失
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 系统内存不足时回调

     * 此时应主动取消同步任务以释放资源，避免被系统强制终止。
     */
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    /**
     * 绑定服务时回调（此服务不支持绑定）
     *
     * @param intent 绑定请求的 Intent
     * @return null 表示不支持绑定服务
     */
    public IBinder onBind(Intent intent) {
        return null;  // 此服务不提供绑定接口，仅支持启动
    }

    // ======================= 公共方法 =======================

    /**
     * 发送同步状态广播

     * 向所有注册了 GTASK_SERVICE_BROADCAST_NAME 的组件发送广播，
     * 告知当前同步状态（是否正在同步）和进度消息。
     *
     * @param msg 当前同步进度消息（如"正在同步第 5/10 项"）
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;  // 更新静态进度变量
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);  // 发送广播
    }

    // ======================= 静态工具方法 =======================

    /**
     * 启动同步（供 Activity 调用）

     * 这是一个静态工厂方法，外部组件通过此方法启动同步服务。
     * 会自动设置 GTaskManager 的 Activity 上下文（用于可能的登录界面）。
     *
     * @param activity 调用同步的 Activity（用于设置上下文和可能的界面跳转）
     */
    public static void startSync(Activity activity) {
        // 设置 Activity 上下文，以便在需要登录时弹出界面
        GTaskManager.getInstance().setActivityContext(activity);

        // 构建启动服务的 Intent
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);

        // 启动服务（执行同步）
        activity.startService(intent);
    }

    /**
     * 取消同步（供外部组件调用）

     * 静态方法，通过启动服务并传递取消动作来取消同步。
     * 无论服务是否正在运行，都会发送取消请求。
     *
     * @param context 应用上下文（可以是 Activity 或 Application）
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    /**
     * 查询是否正在同步

     * 静态方法，供外部组件（如 Activity）查询当前同步状态。
     *
     * @return true 表示有同步任务正在执行，false 表示空闲
     */
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    /**
     * 获取当前同步进度文本

     * 静态方法，供外部组件获取最新的进度信息。
     *
     * @return 当前进度描述字符串（如"正在同步第 3 项"）
     */
    public static String getProgressString() {
        return mSyncProgress;
    }
}