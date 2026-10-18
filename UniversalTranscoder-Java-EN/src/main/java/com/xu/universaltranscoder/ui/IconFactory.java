package com.xu.universaltranscoder.ui;

// 引入 Image，作为窗口图标类型

import java.awt.Image;
// 引入 BufferedImage，作为程序化生成图标的画布
import java.awt.image.BufferedImage;
// 引入 Graphics2D，用于绘制图标内容
import java.awt.Graphics2D;
// 引入 Color，定义图标配色
import java.awt.Color;
// 引入 RenderingHints，开启抗锯齿提升图标质量
import java.awt.RenderingHints;
// 引入 ImageIO，加载内置 PNG 图标
import javax.imageio.ImageIO;
// 引入 IOException，处理图标读取异常
import java.io.IOException;
// 引入 InputStream，读取类路径内置资源
import java.io.InputStream;

// 关键字 class：IconFactory 提供窗口图标，优先使用内置图片，缺失时用 Java2D 程序化生成
// 之所以保留程序化生成：图标资源可能因打包裁剪而缺失，程序化生成可以保证窗口始终有可用图标
public final class IconFactory {

    // 缓存已加载的图标，避免每次创建窗口都重复读取图片
    private static volatile Image cachedIcon;

    // 私有构造方法，禁止实例化这个纯静态图标工厂
    private IconFactory() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 获取窗口图标，资源缺失时返回程序化生成的图标
    public static Image getAppIcon() {
        // 命中缓存时直接返回
        Image current = cachedIcon;
        // 缓存非空时返回
        if (current != null) {
            // 返回缓存图标
            return current;
        }
        // 尝试从类路径读取内置 PNG 图标
        try (InputStream input = IconFactory.class.getResourceAsStream("/icon/icon.png")) {
            // 资源存在时解码为图片
            if (input != null) {
                // 使用 JDK 标准解码器读取图片
                BufferedImage image = ImageIO.read(input);
                // 解码成功时写入缓存并返回
                if (image != null) {
                    // 写入缓存
                    cachedIcon = image;
                    // 返回内置图标
                    return image;
                }
            }
        } catch (IOException ex) {
            // 读取失败时回退到程序化生成，保证仍能提供图标
        }
        // 全部资源都不可用时生成一个简易图标
        Image generated = generateFallbackIcon();
        // 写入缓存
        cachedIcon = generated;
        // 返回生成的图标
        return generated;
    }

    // 用 Java2D 生成一个简易图标：深色圆角背景加白色胶片符号
    private static Image generateFallbackIcon() {
        // 图标边长设为 256 像素，满足任务栏与窗口标题栏的展示需求
        int size = 256;
        // 创建带透明通道的画布
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        // 取得绘制上下文
        Graphics2D graphics = image.createGraphics();
        // 开启抗锯齿，保证圆角与图形边缘平滑
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 设置背景色为深蓝色
        graphics.setColor(new Color(0x1F3A63));
        // 绘制圆角矩形作为图标底板
        graphics.fillRoundRect(0, 0, size, size, 48, 48);
        // 设置前景色为浅青色
        graphics.setColor(new Color(0x7FD4FF));
        // 绘制外框中空矩形，模拟胶片框
        graphics.fillRect(40, 64, 176, 128);
        // 切换回背景色
        graphics.setColor(new Color(0x1F3A63));
        // 在内框上打出一排齿孔，形成胶片观感
        for (int i = 0; i < 6; i++) {
            // 逐个小方块擦出齿孔
            graphics.fillRect(52 + i * 28, 76, 14, 12);
            // 下方同样擦出一排齿孔
            graphics.fillRect(52 + i * 28, 168, 14, 12);
        }
        // 设置前景色为亮白色
        graphics.setColor(Color.WHITE);
        // 在中部绘制两个箭头形状的三角形，象征格式转换
        graphics.fillPolygon(new int[]{96, 128, 128}, new int[]{128, 104, 152}, 3);
        // 绘制向右的三角形
        graphics.fillPolygon(new int[]{160, 128, 128}, new int[]{128, 104, 152}, 3);
        // 释放绘图上下文，避免资源泄漏
        graphics.dispose();
        // 返回生成的图标
        return image;
    }
}
