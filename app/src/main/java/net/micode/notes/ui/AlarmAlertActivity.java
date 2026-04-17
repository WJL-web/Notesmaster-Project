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
 * 请参阅许可证以了解特定语言下的权限和性质。
 */

package net.micode.notes.ui;

// Android 核心类导入
import android.app.Activity;                    // Activity基类
import android.app.AlertDialog;                 // 警告对话框
import android.content.Context;                 // 上下文对象
import android.content.DialogInterface;         // 对话框接口
import android.content.DialogInterface.OnClickListener;   // 对话框点击监听器
import android.content.DialogInterface.OnDismissListener; // 对话框关闭监听器
import android.content.Intent;                  // Intent，用于页面跳转
import android.media.AudioManager;              // 音频管理器
import android.media.MediaPlayer;               // 媒体播放器
import android.media.RingtoneManager;           // 铃声管理器
import android.net.Uri;                         // URI，用于指定铃声路径
import android.os.Bundle;                       // 数据包，用于传递状态
import android.os.PowerManager;                 // 电源管理器
import android.provider.Settings;               // 系统设置
import android.view.Window;                     // 窗口
import android.view.WindowManager;              // 窗口管理器

// 项目内部类导入
import net.micode.notes.R;                      // 资源文件
import net.micode.notes.data.Notes;             // 便签数据契约类
import net.micode.notes.tool.DataUtils;         // 数据工具类

// Java 标准库导入
import java.io.IOException;                     // IO异常

/**
 * AlarmAlertActivity 类 - 闹钟提醒活动
 *
 * 作用：当便签设置的提醒时间到达时，显示提醒对话框并播放闹钟铃声
 *
 * 功能：
 * 1. 在锁屏界面显示提醒对话框
 * 2. 播放系统闹钟铃声（循环播放）
 * 3. 显示便签内容预览（最多60字符）
 * 4. 支持点击"进入应用"跳转到便签编辑页面
 * 5. 关闭对话框时停止铃声并结束活动
 *
 * 继承关系：
 * - 继承自 Activity
 * - 实现 OnClickListener（处理按钮点击）
 * - 实现 OnDismissListener（处理对话框关闭）
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {

    // ==================== 成员变量 ====================

    /** 便签ID（从Intent中获取） */
    private long mNoteId;

    /** 便签内容摘要（用于在对话框中显示预览） */
    private String mSnippet;

    /** 摘要预览最大长度（60个字符） */
    private static final int SNIPPET_PREW_MAX_LEN = 60;

    /** 媒体播放器，用于播放闹钟铃声 */
    MediaPlayer mPlayer;

    // ==================== Activity 生命周期方法 ====================

    /**
     * Activity创建时调用
     *
     * @param savedInstanceState 保存的状态数据
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 请求无标题栏的窗口样式
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 获取窗口对象并设置标志
        final Window win = getWindow();
        // FLAG_SHOW_WHEN_LOCKED：在锁屏界面显示此Activity
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕未亮起，添加屏幕唤醒相关标志
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON          // 保持屏幕常亮
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON            // 点亮屏幕
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON // 允许屏幕亮起时锁定
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);      // 布局插入装饰
        }

        // 获取启动此Activity的Intent
        Intent intent = getIntent();

        try {
            // 从Intent的URI中解析便签ID
            // URI格式：content://micode_notes/note/{id}
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));

            // 根据便签ID获取便签内容摘要
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);

            // 截取摘要：如果长度超过最大限制，则截取前60字符并添加"..."提示
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN
                    ? mSnippet.substring(0, SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;  // 解析失败，直接返回
        }

        // 创建媒体播放器实例
        mPlayer = new MediaPlayer();

        // 检查便签是否有效（存在于数据库中且为普通便签类型）
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            // 显示提醒对话框
            showActionDialog();
            // 播放闹钟铃声
            playAlarmSound();
        } else {
            // 便签无效，直接关闭Activity
            finish();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断屏幕是否亮起
     *
     * 通过PowerManager获取屏幕状态
     *
     * @return true 屏幕亮起，false 屏幕熄灭
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 播放闹钟铃声
     *
     * 功能：
     * 1. 获取系统默认的闹钟铃声URI
     * 2. 根据系统设置判断闹钟铃声是否受静音模式影响
     * 3. 设置媒体播放器的音频流类型
     * 4. 循环播放闹钟铃声
     *
     * 注意：会捕获各种异常，避免因铃声问题导致应用崩溃
     */
    private void playAlarmSound() {
        // 获取系统默认的闹钟铃声URI
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 获取受静音模式影响的音频流类型
        // MODE_RINGER_STREAMS_AFFECTED 表示哪些音频流受静音模式影响
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 设置音频流类型
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            // 闹钟流受静音模式影响，使用静音模式设置的流类型
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            // 闹钟流不受静音模式影响，直接使用闹钟流
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }

        try {
            // 设置数据源为闹钟铃声URI
            mPlayer.setDataSource(this, url);
            // 准备播放器
            mPlayer.prepare();
            // 设置循环播放（闹钟通常需要持续响铃直到用户响应）
            mPlayer.setLooping(true);
            // 开始播放
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示提醒对话框
     *
     * 功能：
     * 1. 创建警告对话框
     * 2. 设置标题为应用名称
     * 3. 显示便签内容摘要
     * 4. 设置"确定"按钮（关闭提醒）
     * 5. 如果屏幕亮起，额外显示"进入应用"按钮
     * 6. 设置对话框关闭监听器
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name);           // 设置对话框标题
        dialog.setMessage(mSnippet);                  // 设置显示内容为便签摘要
        dialog.setPositiveButton(R.string.notealert_ok, this);  // "确定"按钮

        // 如果屏幕亮起，显示"进入应用"按钮（允许用户直接跳转到便签编辑页面）
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }
        // 显示对话框并设置关闭监听器
        dialog.show().setOnDismissListener(this);
    }

    // ==================== 接口实现方法 ====================

    /**
     * 对话框按钮点击事件处理
     *
     * 实现 OnClickListener 接口
     *
     * @param dialog 触发事件的对话框
     * @param which 被点击的按钮（BUTTON_NEGATIVE 为"进入应用"按钮）
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:  // 点击"进入应用"按钮
                // 创建Intent跳转到便签编辑页面
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_UID, mNoteId);  // 传递便签ID
                startActivity(intent);  // 启动编辑页面
                break;
            default:
                // 点击"确定"按钮时什么都不做，对话框关闭时会触发 onDismiss
                break;
        }
    }

    /**
     * 对话框关闭时调用
     *
     * 实现 OnDismissListener 接口
     *
     * @param dialog 被关闭的对话框
     */
    public void onDismiss(DialogInterface dialog) {
        // 停止闹钟铃声
        stopAlarmSound();
        // 关闭Activity
        finish();
    }

    /**
     * 停止闹钟铃声并释放资源
     *
     * 功能：
     * 1. 停止播放器播放
     * 2. 释放播放器占用的资源
     * 3. 将播放器引用设为null
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();      // 停止播放
            mPlayer.release();   // 释放资源
            mPlayer = null;      // 清空引用
        }
    }
}