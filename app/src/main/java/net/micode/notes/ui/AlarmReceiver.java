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

package net.micode.notes.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 闹钟提醒广播接收器
 * 作用：接收系统发出的定时闹钟广播，跳转至便签提醒弹窗界面
 * 工作流程：AlarmManager 定时触发 → 发送广播 → 本类接收 → 启动 AlarmAlertActivity
 */
public class AlarmReceiver extends BroadcastReceiver {

    /**
     * 接收广播回调方法
     * @param context 上下文
     * @param intent 携带闹钟数据的意图（包含便签ID、提醒内容等）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 设置目标Activity：将广播意图指向提醒弹窗界面
        intent.setClass(context, AlarmAlertActivity.class);
        // 添加新任务栈标记：在广播接收器中启动Activity必须添加该标记
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // 启动提醒界面
        context.startActivity(intent);
    }
}