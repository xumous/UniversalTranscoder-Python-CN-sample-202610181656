package com.xu.universaltranscoder;

// 引入自检类，用于在无人值守模式下跑内核自检与真实转码

import com.xu.universaltranscoder.core.SelfTest;
// 引入主窗口类，用于启动界面或执行离屏冒烟测试
import com.xu.universaltranscoder.ui.MainWindow;

// 引入 Swing 工具类，把界面创建放到事件分发线程上执行
import javax.swing.SwingUtilities;

// 关键字 class：Main 是应用入口，支持交互启动、无人值守自检与离屏界面冒烟三种运行方式
// 之所以把自检入口放在主类：打包成可执行文件后无法方便地调用其它入口类，主类参数是唯一的稳定入口
public final class Main {

    // 私有构造方法，禁止实例化入口类
    private Main() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 程序入口方法
    public static void main(String[] args) {
        // 判断是否要求执行无人值守自检
        boolean selfTest = hasFlag(args, "--selftest");
        // 判断是否只要求执行界面离屏冒烟
        boolean uiSmokeOnly = hasFlag(args, "--ui-smoke");
        // 判断是否请求帮助信息
        boolean help = hasFlag(args, "--help");
        // 打印帮助时输出可用参数并结束
        if (help) {
            // 输出用法说明，该文案面向开发者因此使用英文
            System.out.println("Universal Transcoder 1.0.0");
            // 输出参数说明
            System.out.println("Usage:");
            // 默认启动界面
            System.out.println("  (no arguments)        Start the graphical interface.");
            // 自检模式说明
            System.out.println("  --selftest            Run core self test, a real transcode and a UI smoke test.");
            // 界面冒烟说明
            System.out.println("  --ui-smoke            Build the main window offscreen and report the result.");
            // 帮助说明
            System.out.println("  --help                Show this message.");
            // 正常结束
            System.exit(0);
        }
        // 自检模式优先处理，因为它包含界面冒烟
        if (selfTest) {
            // 执行核心自检并取得退出码
            int coreCode = SelfTest.run();
            // 声明界面冒烟结果容器，invokeAndWait 需要可写的外部变量
            final boolean[] uiResult = new boolean[]{false};
            // 捕获界面冒烟过程中的异常，保证自检流程不因界面问题中断
            try {
                // 切换到事件分发线程构建并释放主窗口，符合 Swing 的线程约束
                SwingUtilities.invokeAndWait(() -> uiResult[0] = MainWindow.smokeTest());
            } catch (Exception ex) {
                // 界面冒烟失败时把结果置为失败
                uiResult[0] = false;
                // 打印失败原因，便于定位
                System.out.println("UI smoke test failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            // 两项都通过才算整体通过
            boolean allPassed = coreCode == 0 && uiResult[0];
            // 打印整体结论
            System.out.println("=== Self test overall: " + (allPassed ? "PASS" : "FAIL") + " ===");
            // 按整体结果结束进程，打包后的脚本可据此判断成功与否
            System.exit(allPassed ? 0 : 1);
        }
        // 仅界面冒烟模式
        if (uiSmokeOnly) {
            // 声明结果容器
            final boolean[] result = new boolean[]{false};
            // 捕获异常保证冒烟失败也能给出明确退出码
            try {
                // 切换到事件分发线程执行冒烟
                SwingUtilities.invokeAndWait(() -> result[0] = MainWindow.smokeTest());
            } catch (Exception ex) {
                // 打印失败原因
                System.out.println("UI smoke test failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            // 按结果结束进程
            System.exit(result[0] ? 0 : 1);
        }
        // 默认模式：在事件分发线程上创建并显示主窗口
        SwingUtilities.invokeLater(() -> {
            // 创建主窗口
            MainWindow window = new MainWindow();
            // 显示窗口
            window.setVisible(true);
        });
    }

    // 判断命令行是否包含指定参数，忽略大小写以提升容错
    private static boolean hasFlag(String[] args, String flag) {
        // 参数数组为空时直接返回假
        if (args == null || args.length == 0) {
            // 返回假表示未包含
            return false;
        }
        // 遍历全部参数
        for (String arg : args) {
            // 忽略大小写比较
            if (arg != null && arg.equalsIgnoreCase(flag)) {
                // 命中即返回真
                return true;
            }
        }
        // 未命中时返回假
        return false;
    }
}
