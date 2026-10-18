package com.xu.universaltranscoder.ui;

// 引入 UIManager，用于切换 Swing 外观并覆盖部分颜色键

import javax.swing.UIManager;
// 引入 SwingUtilities，把控件更新切换到事件分发线程
import javax.swing.SwingUtilities;
// 引入 Color，定义主题颜色
import java.awt.Color;
// 引入 Font，定义统一字体
import java.awt.Font;

// 关键字 class：UiTheme 负责设置 Swing 外观与深浅两套配色
// 之所以集中处理主题：颜色与外观一旦分散在各面板，后续调整会遗漏控件，集中管理可保证视觉一致
public final class UiTheme {

    // 浅色主题的基础背景色
    private static final Color LIGHT_BACKGROUND = new Color(0xF4F5F7);
    // 浅色主题的控件背景色
    private static final Color LIGHT_CONTROL = new Color(0xFFFFFF);
    // 浅色主题的前景色
    private static final Color LIGHT_FOREGROUND = new Color(0x1F2328);
    // 深色主题的基础背景色
    private static final Color DARK_BACKGROUND = new Color(0x23272E);
    // 深色主题的控件背景色
    private static final Color DARK_CONTROL = new Color(0x2C313A);
    // 深色主题的前景色
    private static final Color DARK_FOREGROUND = new Color(0xE6E6E6);
    // 强调色，用于选中行与进度条，两套主题共用
    private static final Color ACCENT = new Color(0x2F6FEB);

    // 私有构造方法，禁止实例化这个纯静态主题工具类
    private UiTheme() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 应用主题：设置外观并在事件分发线程上刷新界面
    public static void apply(String themeName, java.awt.Window window) {
        // 判断是否要求深色主题
        boolean dark = "dark".equalsIgnoreCase(themeName);
        // 捕获外观切换异常，某些平台可能缺少指定外观
        try {
            // 优先使用跨平台的 Nimbus 外观，它的控件渲染在各系统上较为一致
            boolean nimbusFound = false;
            // 遍历全部已安装外观寻找 Nimbus
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                // 命中 Nimbus 时切换外观
                if ("Nimbus".equals(info.getName())) {
                    // 应用 Nimbus 外观
                    UIManager.setLookAndFeel(info.getClassName());
                    // 记录已找到标记
                    nimbusFound = true;
                    // 找到后立即结束遍历
                    break;
                }
            }
            // 未找到 Nimbus 时退回到系统原生外观
            if (!nimbusFound) {
                // 使用系统外观，保证与操作系统观感一致
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
        } catch (Exception ex) {
            // 外观设置失败时保持当前外观，不影响功能可用性
        }
        // 取对应主题的颜色组
        Color background = dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
        // 控件背景色
        Color control = dark ? DARK_CONTROL : LIGHT_CONTROL;
        // 前景色
        Color foreground = dark ? DARK_FOREGROUND : LIGHT_FOREGROUND;
        // 容器类颜色键统一使用基础背景色
        String[] containerKeys = {
                // 面板背景
                "Panel.background",
                // 滚动面板背景
                "ScrollPane.background",
                // 视口背景，滚动区域的实际绘制背景
                "Viewport.background"
        };
        // 逐键写入基础背景色
        for (String key : containerKeys) {
            // 写入基础背景色
            UIManager.put(key, background);
        }
        // 输入与数据类颜色键统一使用控件背景色，让输入区域与容器形成层次
        String[] controlKeys = {
                // 控件背景
                "control",
                // 文本框背景
                "TextField.background",
                // 文本域背景
                "TextArea.background",
                // 列表背景
                "List.background",
                // 表格背景
                "Table.background",
                // 下拉框背景
                "ComboBox.background"
        };
        // 逐键写入控件背景色
        for (String key : controlKeys) {
            // 写入控件背景色
            UIManager.put(key, control);
        }
        // 覆盖前景相关颜色键
        String[] foregroundKeys = {
                // 一般前景
                "Label.foreground",
                // 控件前景
                "controlText",
                // 文本框前景
                "TextField.foreground",
                // 文本域前景
                "TextArea.foreground",
                // 列表前景
                "List.foreground",
                // 表格前景
                "Table.foreground",
                // 下拉框前景
                "ComboBox.foreground",
                // 复选框前景
                "CheckBox.foreground",
                // 单选按钮前景
                "RadioButton.foreground",
                // 页签前景
                "TabbedPane.foreground"
        };
        // 逐键写入前景色
        for (String key : foregroundKeys) {
            // 写入颜色值
            UIManager.put(key, foreground);
        }
        // 覆盖选中与强调颜色
        UIManager.put("nimbusSelection", ACCENT);
        // 覆盖选中背景色键
        UIManager.put("nimbusSelectionBackground", ACCENT);
        // 覆盖表格选中背景色键
        UIManager.put("Table.selectionBackground", ACCENT);
        // 覆盖表格选中前景色键
        UIManager.put("Table.selectionForeground", dark ? Color.WHITE : Color.WHITE);
        // 覆盖列表选中背景色键
        UIManager.put("List.selectionBackground", ACCENT);
        // 覆盖页签选中背景色键
        UIManager.put("TabbedPane.selected", control);
        // 覆盖工具提示背景色
        UIManager.put("ToolTip.background", control);
        // 覆盖工具提示前景色
        UIManager.put("ToolTip.foreground", foreground);
        // 定义统一字体，避免不同控件字号不一致
        Font baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        // 覆盖常用控件字体
        String[] fontKeys = {
                // 标签字体
                "Label.font",
                // 按钮字体
                "Button.font",
                // 复选框字体
                "CheckBox.font",
                // 单选按钮字体
                "RadioButton.font",
                // 下拉框字体
                "ComboBox.font",
                // 文本框字体
                "TextField.font",
                // 密码框字体
                "PasswordField.font",
                // 文本域字体
                "TextArea.font",
                // 列表字体
                "List.font",
                // 表格字体
                "Table.font",
                // 表头字体
                "TableHeader.font",
                // 页签字体
                "TabbedPane.font",
                // 菜单字体
                "Menu.font",
                // 面板字体
                "Panel.font"
        };
        // 逐键写入字体
        for (String key : fontKeys) {
            // 写入统一字体
            UIManager.put(key, baseFont);
        }
        // 界面已构建时通知 Swing 重新计算各控件的外观
        if (window != null) {
            // 切换到事件分发线程执行界面刷新，避免线程安全问题
            SwingUtilities.invokeLater(() -> {
                // 遍历窗口内部的全部组件并刷新界面树
                SwingUtilities.updateComponentTreeUI(window);
                // 递归刷新所有子窗口（对话框）的外观
                for (java.awt.Window child : window.getOwnedWindows()) {
                    // 刷新子窗口界面树
                    SwingUtilities.updateComponentTreeUI(child);
                }
            });
        }
    }

    // 判断是否深色主题，供面板决定日志区的额外配色
    public static boolean isDark(String themeName) {
        // 忽略大小写比较主题名
        return "dark".equalsIgnoreCase(themeName);
    }
}
