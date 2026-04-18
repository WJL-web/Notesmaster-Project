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
/**
 * 文件名: GTaskASyncTask.java
 * 功能: 小米便签应用中处理Google Task(GTask)同步的异步任务类
 * 作者: The MiCode Open Source Community
 * 创建时间: 2010-2011
 * 修改记录: 包含Android 13通知权限适配和PendingIntent flags修复
 *
 * 版权声明:
 * 遵循Apache License, Version 2.0开源协议
 * 许可证详情: http://www.apache.org/licenses/LICENSE-2.0
 * 核心要点:
 * 1. 允许自由使用、修改、分发，但需保留原始版权声明
 * 2. 不提供任何明示或暗示的担保
 * 3. 不对使用本软件产生的损害承担责任
 */

package net.micode.notes.gtask.remote;

// Android系统相关导入
import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import androidx.core.app.ActivityCompat;

// 项目资源导入
import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 类名: GTaskASyncTask
 * 描述: 继承自AsyncTask的异步任务类，专门处理与Google Task的网络数据同步
 *
 * AsyncTask泛型参数说明:
 * 1. Void: 执行任务时不需要传入参数
 * 2. String: 进度更新时传递的消息类型(如"正在登录...")
 * 3. Integer: 后台任务执行完成后返回的结果码(如成功、网络错误等)
 *
 * 主要职责:
 * 1. 在后台线程执行GTask同步操作
 * 2. 通过通知栏向用户展示同步进度和结果
 * 3. 处理Android 13及以上版本的通知权限适配
 * 4. 提供同步取消机制
 * 5. 同步完成后回调通知
 *
 * 工作流程:
 * 1. 创建实例时传入上下文和完成监听器
 * 2. 调用execute()启动异步任务
 * 3. doInBackground()在后台执行实际的同步逻辑
 * 4. 通过publishProgress()更新进度，触发onProgressUpdate()显示通知
 * 5. 同步完成后，onPostExecute()根据结果码显示最终通知
 * 6. 调用完成监听器的onComplete()方法
 *
 * 设计模式: 异步任务模式 + 观察者模式(通过回调监听)
 * 线程安全: AsyncTask内部已处理线程安全问题
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {
    /** 通知ID常量: 用于标识GTask同步通知，避免与其他通知冲突 */
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 接口: OnCompleteListener
     * 功能: 同步完成回调接口，使用观察者模式实现任务完成通知
     * 设计意图: 解耦同步任务和调用者，允许异步回调
     */
    public interface OnCompleteListener {
        /**
         * 当GTask同步任务完成时被调用
         * 注意: 此方法在新线程中执行，避免阻塞主线程
         */
        void onComplete();
    }

    /** 上下文对象: 提供应用环境信息，用于访问资源、启动Activity等 */
    private Context mContext;

    /** 通知管理器: 用于创建、更新、取消系统通知 */
    private NotificationManager mNotifiManager;

    /** GTask管理器: 实际执行同步操作的业务逻辑类(单例模式) */
    private GTaskManager mTaskManager;

    /** 完成监听器: 同步完成时的回调接口实现 */
    private OnCompleteListener mOnCompleteListener;

    /**
     * 构造方法: 初始化GTask异步任务
     *
     * @param context 上下文，通常是Service或Activity
     *                注意: 如果传入Activity，需注意生命周期管理，避免内存泄漏
     *
     * @param listener 同步完成监听器，用于任务完成时回调
     *                 可以为null，表示不监听完成事件
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        // 获取系统通知管理器服务
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        // 获取GTask管理器单例实例
        mTaskManager = GTaskManager.getInstance();
    }

    /**
     * 方法名: cancelSync
     * 功能: 取消正在进行的同步操作
     * 机制: 调用GTaskManager的cancelSync()方法设置取消标志
     * 注意: 这是异步取消，实际停止需要GTaskManager内部配合检查取消标志
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 方法名: publishProgess
     * 功能: 发布同步进度(工具方法，包装publishProgress)
     * 作用: 简化进度发布调用，统一错误处理
     *
     * @param message 进度消息，显示在通知栏
     *                示例: "正在登录Google账户..."
     */
    public void publishProgess(String message) {
        publishProgress(new String[] { message });
    }

    /**
     * 方法名: showNotification
     * 功能: 显示同步状态通知
     * 特性:
     * 1. 根据同步结果(tickerId)决定点击通知后的跳转目标
     * 2. 处理Android 12+的PendingIntent flags
     * 3. 适配Android 13+的通知权限
     * 4. 使用Notification.Builder构建通知(兼容新旧版本)
     *
     * @param tickerId 状态标识资源ID，决定通知图标和点击行为
     *                 R.string.ticker_success: 同步成功
     *                 R.string.ticker_syncing: 同步中
     *                 R.string.ticker_fail: 同步失败
     *                 R.string.ticker_cancel: 同步取消
     *
     * @param content 通知内容文本，显示具体的状态信息
     */
    private void showNotification(int tickerId, String content) {
        // 创建基础Notification对象(已过时，用于设置默认值)
        Notification notification = new Notification(R.drawable.notification, mContext
                .getString(tickerId), System.currentTimeMillis());
        notification.defaults = Notification.DEFAULT_LIGHTS; // 默认LED灯提示
        notification.flags = Notification.FLAG_AUTO_CANCEL;  // 点击后自动取消

        PendingIntent pendingIntent;
        // 1. 定义符合安全规范的PendingIntent flags
        int flags = PendingIntent.FLAG_UPDATE_CURRENT; // 如果存在则更新
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // Android 6.0+ 需要添加FLAG_IMMUTABLE保证安全性
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        // 根据同步结果决定点击通知后的跳转目标
        if (tickerId != R.string.ticker_success) {
            // 同步失败/进行中/取消：跳转到设置页(检查账户配置)
            pendingIntent = PendingIntent.getActivity(mContext, 0,
                    new Intent(mContext, NotesPreferenceActivity.class), flags);
        } else {
            // 同步成功：跳转到笔记列表页
            pendingIntent = PendingIntent.getActivity(mContext, 0,
                    new Intent(mContext, NotesListActivity.class), flags);
        }

        // 使用Notification.Builder构建通知(兼容Android 3.0+)
        Notification.Builder builder = new Notification.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.app_name)) // 通知标题
                .setContentText(content) // 通知内容
                .setSmallIcon(R.mipmap.ic_launcher) // 小图标(使用应用启动图标)
                .setContentIntent(pendingIntent); // 点击意图

        notification = builder.build(); // 构建Notification对象

        // Android 13(API 33)及以上版本的通知权限检查
        if (Build.VERSION.SDK_INT >= 33) {
            // 检查是否已获得通知权限
            if (ActivityCompat.checkSelfPermission(this.mContext,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // 没有权限时的处理：记录日志，避免崩溃
                Log.w("GTaskSync", "Missing POST_NOTIFICATIONS permission");
                // 注意: 这里可以引导用户开启权限，但当前只是记录日志
                // return; // 如果需要，可以在此处返回，不发送通知
            }
        }

        // 显示通知(使用唯一ID，多次调用会更新同一通知)
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    /**
     * 方法名: doInBackground
     * 功能: 后台执行同步操作的核心方法(AsyncTask要求)
     * 执行环境: 在工作线程(非UI线程)中自动调用
     * 调用时机: execute()方法被调用后，由AsyncTask框架自动调度执行
     *
     * @param unused 可变参数，此处不使用
     * @return 同步结果码(Integer)，对应GTaskManager中定义的常量
     *         例如: STATE_SUCCESS, STATE_NETWORK_ERROR等
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        // 发布登录进度消息
        publishProgess(mContext.getString(R.string.sync_progress_login,
                NotesPreferenceActivity.getSyncAccountName(mContext)));

        // 调用GTaskManager执行实际同步，返回结果码
        return mTaskManager.sync(mContext, this);
    }

    /**
     * 方法名: onProgressUpdate
     * 功能: 更新同步进度(在主线程中执行)
     * 调用时机: 在doInBackground中调用publishProgress()后自动触发
     * 用途: 显示进度通知，并可向GTaskSyncService发送广播
     *
     * @param progress 进度消息数组，通常只包含一个字符串
     */
    @Override
    protected void onProgressUpdate(String... progress) {
        // 显示同步中的通知
        showNotification(R.string.ticker_syncing, progress[0]);

        // 如果上下文是GTaskSyncService，发送进度广播
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    /**
     * 方法名: onPostExecute
     * 功能: 同步完成后处理结果(在主线程中执行)
     * 调用时机: doInBackground执行完成后自动调用
     * 职责:
     * 1. 根据结果码显示对应的成功/失败通知
     * 2. 记录最后一次同步时间(仅成功时)
     * 3. 调用完成监听器的回调方法(在新线程中)
     *
     * @param result 同步结果码，来自doInBackground的返回值
     */
    @Override
    protected void onPostExecute(Integer result) {
        // 根据不同的结果码显示不同的通知
        if (result == GTaskManager.STATE_SUCCESS) {
            // 同步成功：显示成功通知，记录同步时间
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());

        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            // 网络错误
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));

        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            // 内部错误(如数据解析、逻辑错误)
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));

        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            // 同步被用户取消
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }

        // 调用完成监听器(如果有)
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}