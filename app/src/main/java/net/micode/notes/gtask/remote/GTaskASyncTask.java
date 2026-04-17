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

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * Google Tasks 异步同步任务类
 *
 * 继承自 AsyncTask，在后台线程中执行与 Google Tasks 服务的同步操作。
 * 该类负责管理同步过程中的 UI 反馈（通知栏进度显示）和最终结果通知。
 *
 * 主要功能：
 * - 后台执行同步操作（doInBackground）
 * - 实时更新通知栏进度（onProgressUpdate）
 * - 同步完成后显示结果通知（onPostExecute）
 * - 支持取消正在进行的同步任务
 *
 * 泛型参数说明：
 * - Void: 不需要传入参数
 * - String: 进度更新时传递的消息字符串
 * - Integer: 返回同步结果状态码
 *
 * @author MiCode Open Source Community
 * @see GTaskManager 实际的同步逻辑管理器
 * @see AsyncTask Android 异步任务基类
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    /** 同步通知的唯一标识 ID，用于更新/取消特定通知 */
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 同步完成监听器接口
     * 当同步任务完成（无论成功或失败）时回调
     */
    public interface OnCompleteListener {
        /**
         * 同步完成时的回调方法
         * 在异步任务结束后被调用，运行在独立线程中
         */
        void onComplete();
    }

    /** 应用上下文，用于访问系统服务和资源 */
    private Context mContext;

    /** 通知管理器，用于发送状态栏通知 */
    private NotificationManager mNotifiManager;

    /** Google Tasks 管理器单例，执行实际的同步逻辑 */
    private GTaskManager mTaskManager;

    /** 同步完成监听器 */
    private OnCompleteListener mOnCompleteListener;

    /**
     * 构造方法
     *
     * @param context  应用上下文
     * @param listener 同步完成监听器（可为 null）
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();  // 获取单例实例
    }

    /**
     * 取消正在进行的同步任务
     *
     * 委托给 GTaskManager 的 cancelSync 方法，
     * 会尝试中断当前正在执行的同步操作。
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 发布进度更新（供外部调用）
     *
     * 此方法可以在同步过程中被调用，用于更新 UI 反馈。
     * 实际会触发 onProgressUpdate 方法在 UI 线程中执行。
     *
     * @param message 进度消息文本
     */
    public void publishProgess(String message) {
        publishProgress(new String[] { message });
    }

    /**
     * 显示通知栏消息
     *
     * 根据同步状态（进行中/成功/失败/取消）显示不同的通知样式。
     * 点击通知会跳转到相应的 Activity：
     * - 同步失败：跳转到设置页（引导用户检查账号）
     * - 同步成功：跳转到便签列表页
     *
     * 兼容性处理：
     * - Android 6.0+：设置 PendingIntent.FLAG_IMMUTABLE 标志
     * - Android 13+：检查 POST_NOTIFICATIONS 权限
     *
     * @param tickerId 通知栏滚动文本的资源 ID（如 R.string.ticker_syncing）
     * @param content  通知内容文本
     */
    private void showNotification(int tickerId, String content) {
        // 创建基础通知（带滚动文本和图标）
        Notification notification = new Notification(R.drawable.notification,
                mContext.getString(tickerId), System.currentTimeMillis());
        notification.defaults = Notification.DEFAULT_LIGHTS;  // 启用默认指示灯闪烁
        notification.flags = Notification.FLAG_AUTO_CANCEL;    // 点击后自动消失
        PendingIntent pendingIntent;

        // 构建 PendingIntent 的 flags（确保兼容性）
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;  // Android 6.0+ 要求显式设置不可变性
        }

        // 根据同步状态决定点击跳转的目标页面
        if (tickerId != R.string.ticker_success) {
            // 同步失败或进行中：跳转到设置页（让用户检查账号配置）
            pendingIntent = PendingIntent.getActivity(mContext, 0,
                    new Intent(mContext, NotesPreferenceActivity.class), flags);
        } else {
            // 同步成功：跳转到便签列表主页
            pendingIntent = PendingIntent.getActivity(mContext, 0,
                    new Intent(mContext, NotesListActivity.class), flags);
        }

        // 使用 Notification.Builder 构建完整通知（支持更多特性）
        Notification.Builder builder = new Notification.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.app_name))  // 标题：应用名称
                .setContentText(content)                                 // 内容文本
                .setSmallIcon(R.mipmap.ic_launcher)                      // 小图标（启动图标）
                .setContentIntent(pendingIntent);                        // 点击意图

        notification = builder.build();

        // Android 13 (API 33) 及以上需要运行时权限检查
        if (Build.VERSION.SDK_INT >= 33) {
            if (ActivityCompat.checkSelfPermission(this.mContext, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // 没有通知权限时记录日志并跳过发送（避免崩溃）
                Log.w("GTaskSync", "Missing POST_NOTIFICATIONS permission");
                // 注意：实际生产环境中应引导用户开启权限，此处仅做防护
            }
        }

        // 发送通知
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    /**
     * 后台执行同步任务（运行在非 UI 线程）
     *
     * 首先发布登录进度消息，然后委托给 GTaskManager 执行实际的同步操作。
     *
     * @param unused 无用的参数（AsyncTask 要求）
     * @return 同步结果状态码（如 STATE_SUCCESS、STATE_NETWORK_ERROR 等）
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        // 发布登录进度（显示正在登录的账号名）
        publishProgess(mContext.getString(R.string.sync_progress_login,
                NotesPreferenceActivity.getSyncAccountName(mContext)));
        // 执行同步并返回结果
        return mTaskManager.sync(mContext, this);
    }

    /**
     * 进度更新回调（运行在 UI 线程）
     *
     * 当 publishProgress 被调用时触发，用于更新通知栏的同步进度信息。
     * 如果 mContext 是 GTaskSyncService 实例，还会通过广播发送进度消息。
     *
     * @param progress 进度消息数组（通常只使用第一个元素）
     */
    @Override
    protected void onProgressUpdate(String... progress) {
        // 显示同步进行中的通知
        showNotification(R.string.ticker_syncing, progress[0]);

        // 如果当前 Context 是同步服务，则通过广播向外发送进度信息
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    /**
     * 同步完成后的回调（运行在 UI 线程）
     *
     * 根据同步结果状态码显示相应的通知（成功/失败/取消）。
     * 同步成功时还会记录最后一次同步时间。
     * 最后调用 OnCompleteListener 回调（在独立线程中执行）。
     *
     * @param result 同步结果状态码，可能的值：
     *               - GTaskManager.STATE_SUCCESS
     *               - GTaskManager.STATE_NETWORK_ERROR
     *               - GTaskManager.STATE_INTERNAL_ERROR
     *               - GTaskManager.STATE_SYNC_CANCELLED
     */
    @Override
    protected void onPostExecute(Integer result) {
        // 根据结果状态显示对应通知
        if (result == GTaskManager.STATE_SUCCESS) {
            // 同步成功：显示成功通知，并记录最后同步时间
            showNotification(R.string.ticker_success,
                    mContext.getString(R.string.success_sync_account,
                            mTaskManager.getSyncAccount()));
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            // 网络错误
            showNotification(R.string.ticker_fail,
                    mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            // 内部错误（如 JSON 解析失败、数据库错误等）
            showNotification(R.string.ticker_fail,
                    mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            // 用户主动取消同步
            showNotification(R.string.ticker_cancel,
                    mContext.getString(R.string.error_sync_cancelled));
        }

        // 触发完成监听器（在新线程中执行，避免阻塞 UI 线程）
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}