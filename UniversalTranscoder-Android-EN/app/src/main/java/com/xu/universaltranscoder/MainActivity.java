package com.xu.universaltranscoder;

// 引入 Activity 相关类型。

import android.os.Bundle;
// 引入 Intent：打开设置与关于页。
import android.content.Intent;
// 引入 Menu/MenuItem：处理工具栏溢出菜单。
import android.view.Menu;
import android.view.MenuItem;

// 引入 AppCompatActivity：提供向后兼容的 Fragment/主题支持。
import androidx.appcompat.app.AppCompatActivity;
// 引入 Fragment：ViewPager2 的页面单元。
import androidx.fragment.app.Fragment;
// 引入 ViewPager2 与其适配器。
import androidx.viewpager2.widget.ViewPager2;
import androidx.viewpager2.adapter.FragmentStateAdapter;
// 引入注解：标记可空返回值。
import androidx.annotation.NonNull;

// 引入 Material 组件：工具栏、标签栏与二者联动器。
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

// 引入目标注册表：启动时按设备能力生成可用目标清单。
import com.xu.universaltranscoder.model.TargetRegistry;
// 引入设置：应用日志级别。
import com.xu.universaltranscoder.util.AppSettings;
// 引入日志总线：初始化日志级别。
import com.xu.universaltranscoder.util.LogBus;
// 引入 UI 包下的页面与 Activity。
import com.xu.universaltranscoder.ui.AboutActivity;
import com.xu.universaltranscoder.ui.AudioFragment;
import com.xu.universaltranscoder.ui.DocumentFragment;
import com.xu.universaltranscoder.ui.ImageFragment;
import com.xu.universaltranscoder.ui.QueueFragment;
import com.xu.universaltranscoder.ui.SettingsActivity;
import com.xu.universaltranscoder.ui.VideoFragment;

/**
 * MainActivity 是应用唯一的入口界面。
 * 它用 MaterialToolbar + TabLayout + ViewPager2 组织五个标签页：Video / Audio / Image / Document / Queue，
 * 右上角溢出菜单提供 Settings 与 About。选择“用标签页而不是抽屉”是因为五类转换是并列关系，
 * 标签页能让用户一眼看到全部能力，切换成本最低。
 */
public class MainActivity extends AppCompatActivity {

    // 标签页标题资源 id 数组：顺序与 ViewPager2 的页面顺序严格对应。
    private static final int[] TAB_TITLES = new int[]{
            com.xu.universaltranscoder.R.string.tab_video,
            com.xu.universaltranscoder.R.string.tab_audio,
            com.xu.universaltranscoder.R.string.tab_image,
            com.xu.universaltranscoder.R.string.tab_document,
            com.xu.universaltranscoder.R.string.tab_queue
    };
    // 队列页在标签中的下标，供“开始转换后自动跳到队列页”使用。
    private static final int QUEUE_TAB_INDEX = 4;

    // ViewPager2 引用，便于外部切换页面。
    private ViewPager2 viewPager;

    // 生命周期回调：创建界面。
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 调用父类实现。
        super.onCreate(savedInstanceState);
        // 绑定布局。
        setContentView(com.xu.universaltranscoder.R.layout.activity_main);
        // 把工具栏设为支持 ActionBar 的对象，从而让溢出菜单可用。
        MaterialToolbar toolbar = findViewById(com.xu.universaltranscoder.R.id.toolbar);
        // 安装工具栏。
        setSupportActionBar(toolbar);
        // 初始化日志级别（从设置读取），保证日志总线一开始就按用户配置工作。
        LogBus.setMinLevel(AppSettings.get(this).logLevel());
        // 按当前设备能力探测并生成目标格式清单；这是界面列出目标之前必须完成的前置步骤。
        TargetRegistry.init(this);
        // 记录一行启动日志，便于后续排查。
        LogBus.info("App", "Universal Transcoder started");
        // 取得 ViewPager2。
        viewPager = findViewById(com.xu.universaltranscoder.R.id.view_pager);
        // 安装适配器，提供五个页面。
        viewPager.setAdapter(new TabsAdapter(this));
        // 关闭过度滑动动画，让切换更稳重。
        viewPager.setOffscreenPageLimit(1);
        // 取得标签栏。
        TabLayout tabLayout = findViewById(com.xu.universaltranscoder.R.id.tab_layout);
        // 用 TabLayoutMediator 把标签与页面绑定：滑动页面时标签自动高亮，点击标签时页面自动滚动。
        new TabLayoutMediator(tabLayout, viewPager, new TabLayoutMediator.TabConfigurationStrategy() {
            // 配置回调：为每个位置设置标题。
            @Override
            public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                // 使用英文标题资源。
                tab.setText(getString(TAB_TITLES[position]));
            }
        }).attach();
    }

    // 创建溢出菜单。
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 从资源文件加载菜单项。
        getMenuInflater().inflate(com.xu.universaltranscoder.R.menu.main_menu, menu);
        // 返回 true 表示菜单已创建。
        return true;
    }

    // 处理菜单点击。
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // 取菜单项 id。
        int id = item.getItemId();
        // 设置项。
        if (id == com.xu.universaltranscoder.R.id.action_settings) {
            // 打开设置页。
            startActivity(new Intent(this, SettingsActivity.class));
            // 消费该事件。
            return true;
        }
        // 关于项。
        if (id == com.xu.universaltranscoder.R.id.action_about) {
            // 打开关于页。
            startActivity(new Intent(this, AboutActivity.class));
            // 消费该事件。
            return true;
        }
        // 其它项交给父类处理。
        return super.onOptionsItemSelected(item);
    }

    /**
     * 切换到队列标签页：转换页点击开始后调用，让用户立刻看到任务进展。
     */
    public void showQueueTab() {
        // ViewPager2 非空时切换页面。
        if (viewPager != null) {
            // 平滑滚动到队列页。
            viewPager.setCurrentItem(QUEUE_TAB_INDEX, true);
        }
    }

    /**
     * TabsAdapter 是 ViewPager2 的分页适配器，按位置创建对应的 Fragment。
     * 使用 FragmentStateAdapter 而不是普通 PagerAdapter，是因为它能正确保存/恢复 Fragment 状态。
     */
    private static final class TabsAdapter extends FragmentStateAdapter {

        // 构造：绑定宿主 Activity。
        TabsAdapter(@NonNull AppCompatActivity activity) {
            // 调用父类构造并传入片段管理器与生命周期。
            super(activity);
        }

        // 按位置创建 Fragment。
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            // 位置 0 为视频页。
            if (position == 0) {
                // 返回视频页。
                return new VideoFragment();
            }
            // 位置 1 为音频页。
            if (position == 1) {
                // 返回音频页。
                return new AudioFragment();
            }
            // 位置 2 为图片页。
            if (position == 2) {
                // 返回图片页。
                return new ImageFragment();
            }
            // 位置 3 为文档页。
            if (position == 3) {
                // 返回文档页。
                return new DocumentFragment();
            }
            // 其余位置为队列页。
            return new QueueFragment();
        }

        // 页面总数固定为 5。
        @Override
        public int getItemCount() {
            // 返回标签数量。
            return TAB_TITLES.length;
        }
    }
}
