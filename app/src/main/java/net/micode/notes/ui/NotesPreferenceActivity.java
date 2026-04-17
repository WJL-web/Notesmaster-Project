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

package net.micode.notes.ui;

// Android账户相关类
import android.accounts.Account;                    // 账户信息
import android.accounts.AccountManager;            // 账户管理器
import android.app.ActionBar;                      // 操作栏
import android.app.AlertDialog;                    // 警告对话框
import android.content.BroadcastReceiver;          // 广播接收器
import android.content.ContentValues;              // 内容值
import android.content.Context;                    // 上下文
import android.content.DialogInterface;            // 对话框接口
import android.content.Intent;                     // 意图
import android.content.IntentFilter;               // 意图过滤器
import android.content.SharedPreferences;          // 共享偏好
import android.os.Bundle;                          // 数据包
import android.preference.Preference;              // 偏好项
import android.preference.Preference.OnPreferenceClickListener;  // 偏好点击监听器
import android.preference.PreferenceActivity;      // 偏好活动基类
import android.preference.PreferenceCategory;      // 偏好分类
import android.text.TextUtils;                     // 字符串工具
import android.text.format.DateFormat;             // 日期格式化
import android.view.LayoutInflater;                // 布局填充器
import android.view.Menu;                          // 菜单
import android.view.MenuItem;                      // 菜单项
import android.view.View;                          // 视图
import android.widget.Button;                      // 按钮
import android.widget.TextView;                    // 文本框
import android.widget.Toast;                       // 提示消息

// 项目内部类导入
import net.micode.notes.R;                         // 资源文件
import net.micode.notes.data.Notes;                // 便签数据契约
import net.micode.notes.data.Notes.NoteColumns;    // 便签表列名
import net.micode.notes.gtask.remote.GTaskSyncService;  // Google Tasks同步服务

/**
 * NotesPreferenceActivity 类 - 便签设置界面
 *
 * 作用：提供应用的所有设置选项
 * 继承自 PreferenceActivity，使用偏好设置框架
 *
 * 功能：
 * 1. 管理Google Tasks同步账户
 * 2. 手动触发同步/取消同步
 * 3. 显示最后同步时间
 * 4. 同步进度实时显示
 * 5. 账户切换时的数据清理
 *
 * 布局文件：R.xml.preferences（设置项定义）
 *            R.layout.settings_header（头部布局）
 *            R.layout.account_dialog_title（账户对话框标题）
 *            R.layout.add_account_text（添加账户文本）
 */
public class NotesPreferenceActivity extends PreferenceActivity {

    // ==================== 常量定义 ====================

    /** 共享偏好文件名 */
    public static final String PREFERENCE_NAME = "notes_preferences";

    /** 同步账户名称的偏好键 */
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";

    /** 最后同步时间的偏好键 */
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";

    /** 背景随机颜色设置的偏好键 */
    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    /** 同步账户分类的偏好键 */
    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";

    /** 账户过滤器的授权键 */
    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    // ==================== 成员变量 ====================

    /** 账户设置分类（用于显示账户相关设置） */
    private PreferenceCategory mAccountCategory;

    /** Google Tasks同步广播接收器 */
    private GTaskReceiver mReceiver;

    /** 原始账户列表（用于检测账户变化） */
    private Account[] mOriAccounts;

    /** 是否已添加新账户 */
    private boolean mHasAddedAccount;

    // ==================== Activity生命周期方法 ====================

    /**
     * Activity创建时调用
     *
     * @param icicle 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        /* 使用应用图标作为导航返回按钮 */
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // 加载偏好设置布局
        addPreferencesFromResource(R.xml.preferences);

        // 获取账户设置分类
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);

        // 注册同步服务广播接收器
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);

        mOriAccounts = null;

        // 添加自定义头部布局
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        getListView().addHeaderView(header, null, true);
    }

    /**
     * Activity恢复时调用
     *
     * 处理账户变化：如果用户添加了新账户，自动设置为同步账户
     */
    @Override
    protected void onResume() {
        super.onResume();

        // 如果用户添加了新账户，自动设置同步账户
        if (mHasAddedAccount) {
            Account[] accounts = getGoogleAccounts();  // 获取当前所有Google账户
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                // 找出新增的账户
                for (Account accountNew : accounts) {
                    boolean found = false;
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        // 将新账户设置为同步账户
                        setSyncAccount(accountNew.name);
                        break;
                    }
                }
            }
        }

        // 刷新UI显示
        refreshUI();
    }

    /**
     * Activity销毁时调用
     * 注销广播接收器，防止内存泄漏
     */
    @Override
    protected void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    // ==================== UI加载方法 ====================

    /**
     * 加载账户偏好设置项
     *
     * 创建账户设置项，点击后弹出账户选择对话框
     */
    private void loadAccountPreference() {
        // 清空现有的账户设置项
        mAccountCategory.removeAll();

        // 创建新的账户偏好项
        Preference accountPref = new Preference(this);
        final String defaultAccount = getSyncAccountName(this);
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // 首次设置账户：显示账户选择对话框
                        showSelectAccountAlertDialog();
                    } else {
                        // 已有账户：显示更改确认对话框（提示风险）
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    // 同步进行中，不能更改账户
                    Toast.makeText(NotesPreferenceActivity.this,
                                    R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        mAccountCategory.addPreference(accountPref);
    }

    /**
     * 加载同步按钮
     *
     * 根据同步状态显示不同的按钮文字和功能
     */
    private void loadSyncButton() {
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // 设置按钮状态
        if (GTaskSyncService.isSyncing()) {
            // 同步中：显示"取消同步"
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
        } else {
            // 未同步：显示"立即同步"
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
        }
        // 未设置账户时禁用同步按钮
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));

        // 设置最后同步时间显示
        if (GTaskSyncService.isSyncing()) {
            // 同步中：显示同步进度
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                // 有同步记录：显示最后同步时间
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                // 从未同步过：隐藏同步时间显示
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 刷新UI
     * 重新加载账户设置和同步按钮
     */
    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }

    // ==================== 对话框方法 ====================

    /**
     * 显示选择账户的对话框
     *
     * 功能：
     * 1. 列出所有Google账户供用户选择
     * 2. 提供"添加账户"按钮，跳转到系统添加账户界面
     */
    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 设置自定义标题
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));

        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);  // 不使用默认按钮

        // 获取Google账户列表
        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);

        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {
            // 构建账户名称列表
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;  // 标记当前选中的账户
                }
                items[index++] = account.name;
            }
            // 创建单选列表
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());  // 设置选中的账户
                            dialog.dismiss();
                            refreshUI();
                        }
                    });
        }

        // 添加"添加账户"文本视图
        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);

        final AlertDialog dialog = dialogBuilder.show();
        // 点击"添加账户"时跳转到系统账户添加界面
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHasAddedAccount = true;
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                        "gmail-ls"  // 只显示Gmail类型的账户
                });
                startActivityForResult(intent, -1);
                dialog.dismiss();
            }
        });
    }

    /**
     * 显示更改账户的确认对话框
     *
     * 提供三个选项：
     * 1. 更改账户
     * 2. 移除账户
     * 3. 取消
     */
    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 设置自定义标题（显示当前账户名称和警告信息）
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        dialogBuilder.setCustomTitle(titleView);

        // 设置菜单项
        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),  // 更改账户
                getString(R.string.preferences_menu_remove_account),  // 移除账户
                getString(R.string.preferences_menu_cancel)           // 取消
        };
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showSelectAccountAlertDialog();  // 选择新账户
                } else if (which == 1) {
                    removeSyncAccount();  // 移除同步账户
                    refreshUI();
                }
            }
        });
        dialogBuilder.show();
    }

    // ==================== 账户操作方法 ====================

    /**
     * 获取所有Google账户
     *
     * @return Google账户数组
     */
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }

    /**
     * 设置同步账户
     *
     * 功能：
     * 1. 保存账户名称到SharedPreferences
     * 2. 清空最后同步时间
     * 3. 清空本地GTask相关信息（在后台线程中执行）
     *
     * @param account 账户名称
     */
    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            editor.commit();

            // 清空最后同步时间
            setLastSyncTime(this, 0);

            // 清空本地GTask相关信息（同步ID和任务ID）
            new Thread(new Runnable() {
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");   // 清空Google Tasks ID
                    values.put(NoteColumns.SYNC_ID, 0);      // 清空同步ID
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();

            // 显示成功提示
            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 移除同步账户
     *
     * 功能：
     * 1. 从SharedPreferences中移除账户信息
     * 2. 移除最后同步时间
     * 3. 清空本地GTask相关信息
     */
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
        }
        editor.commit();

        // 清空本地GTask相关信息
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
    }

    // ==================== 静态工具方法 ====================

    /**
     * 获取同步账户名称
     *
     * @param context 上下文对象
     * @return 同步账户名称，未设置时返回空字符串
     */
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    /**
     * 设置最后同步时间
     *
     * @param context 上下文对象
     * @param time 同步时间（毫秒时间戳）
     */
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
    }

    /**
     * 获取最后同步时间
     *
     * @param context 上下文对象
     * @return 最后同步时间（毫秒时间戳），0表示从未同步
     */
    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    // ==================== 内部广播接收器 ====================

    /**
     * GTaskReceiver 类 - Google Tasks广播接收器
     *
     * 作用：接收同步服务的状态变化广播，实时更新UI
     */
    private class GTaskReceiver extends BroadcastReceiver {

        /**
         * 接收广播时的回调
         *
         * @param context 上下文
         * @param intent 广播意图，包含同步状态信息
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();  // 刷新UI

            // 如果正在同步，更新同步进度显示
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
            }
        }
    }

    // ==================== 菜单处理 ====================

    /**
     * 选项菜单项点击事件
     *
     * @param item 被点击的菜单项
     * @return true 表示已处理，false 表示未处理
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:  // 返回按钮（ActionBar上的Home按钮）
                Intent intent = new Intent(this, NotesListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);  // 清除栈顶之上的Activity
                startActivity(intent);
                return true;
            default:
                return false;
        }
    }
}