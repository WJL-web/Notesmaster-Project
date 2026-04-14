
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


public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    public interface OnCompleteListener {
        void onComplete();
    }

    private Context mContext;

    private NotificationManager mNotifiManager;

    private GTaskManager mTaskManager;

    private OnCompleteListener mOnCompleteListener;

    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }

    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    public void publishProgess(String message) {
        publishProgress(new String[] {
            message
        });
    }

    private void showNotification(int tickerId, String content) {
        Notification notification = new Notification(R.drawable.notification, mContext
                .getString(tickerId), System.currentTimeMillis());
        notification.defaults = Notification.DEFAULT_LIGHTS;
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        PendingIntent pendingIntent;

        // 1. 先定义符合安全规范的 flags
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        if (tickerId != R.string.ticker_success) {
            // 同步失败：跳转到设置页（检查账号）
            pendingIntent = PendingIntent.getActivity(mContext, 0,
                    new Intent(mContext, NotesPreferenceActivity.class), flags);
        } else {
            // 同步成功：跳转到列表页
            pendingIntent = PendingIntent.getActivity(mContext, 0,
                    new Intent(mContext, NotesListActivity.class), flags);
        }

        Notification.Builder builder = new Notification.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher) // 修改这一行，用系统默认的启动图标
                .setContentIntent(pendingIntent);

        notification = builder.build(); // 注意：如果 getNotification() 报错，就改成 build()

        if (Build.VERSION.SDK_INT >= 33) {
            // 检查是否已经获得了通知权限
            if (ActivityCompat.checkSelfPermission(this.mContext, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // 如果没有权限，在汇报时你可以说：此处应引导用户开启权限。
                // 为了防止崩溃，如果没有权限可以 return 或者 log 记录
                Log.w("GTaskSync", "Missing POST_NOTIFICATIONS permission");
                // return; // 如果不希望在没权限时尝试发送，可以加上 return
            }
        }

        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    @Override
    protected Integer doInBackground(Void... unused) {
        publishProgess(mContext.getString(R.string.sync_progress_login, NotesPreferenceActivity
                .getSyncAccountName(mContext)));
        return mTaskManager.sync(mContext, this);
    }

    @Override
    protected void onProgressUpdate(String... progress) {
        showNotification(R.string.ticker_syncing, progress[0]);
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    @Override
    protected void onPostExecute(Integer result) {
        if (result == GTaskManager.STATE_SUCCESS) {
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {

                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}
