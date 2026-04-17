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

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 下拉菜单封装类
 * 功能：将 Button 与 PopupMenu 绑定，实现点击按钮弹出下拉菜单的效果
 * 作用：简化代码，多处界面可复用（如排序、更多选项等菜单）
 */
public class DropdownMenu {
    // 触发下拉菜单的按钮
    private Button mButton;
    // 系统下拉菜单PopupMenu实例
    private PopupMenu mPopupMenu;
    // 菜单对象，用于操作菜单项
    private Menu mMenu;

    /**
     * 构造方法：初始化下拉菜单
     * @param context 上下文
     * @param button 绑定的按钮（点击后弹出菜单）
     * @param menuId 菜单布局资源ID（R.menu.xxx）
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        // 设置按钮背景为下拉箭头图标
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        // 创建PopupMenu，并绑定到按钮上
        mPopupMenu = new PopupMenu(context, mButton);
        // 获取菜单对象
        mMenu = mPopupMenu.getMenu();
        // 加载菜单布局文件到菜单中
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        // 设置按钮点击事件：点击显示下拉菜单
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 设置菜单项点击监听
     * @param listener 菜单项点击回调接口
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据ID查找菜单项
     * @param id 菜单项ID
     * @return 对应的MenuItem对象
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置按钮显示的文字
     * @param title 按钮文字
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}