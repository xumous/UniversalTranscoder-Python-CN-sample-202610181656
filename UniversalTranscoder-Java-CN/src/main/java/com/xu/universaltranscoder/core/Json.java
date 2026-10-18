package com.xu.universaltranscoder.core;

// 引入 List 接口，用于表示 JSON 数组的解析结果

import java.util.List;
// 引入 ArrayList 实现，作为数组节点的具体容器
import java.util.ArrayList;
// 引入 Map 接口，用于表示 JSON 对象的解析结果
import java.util.Map;
// 引入 LinkedHashMap 实现，保持键的插入顺序，便于日志按原始顺序输出
import java.util.LinkedHashMap;

// 关键字 class：本类实现一个不依赖任何第三方库的最小 JSON 解析器
// 之所以自己写而不引入 Gson/Jackson：任务限定唯一外部依赖为 PDFBox，且 ffprobe 的 JSON 结构简单可控
public final class Json {

    // 私有构造方法，阻止外部实例化这个纯静态工具类
    private Json() {
        // 空实现，仅为关闭默认公有构造函数
    }

    // 解析入口：把 JSON 文本解析为 Java 对象（Map / List / String / Double / Boolean / null）
    public static Object parse(String text) {
        // 文本为空时直接返回 null，避免后续索引越界，属于防御式判断
        if (text == null) {
            // 返回 null 表示没有可解析的内容
            return null;
        }
        // 创建解析游标对象，内部维护字符数组与当前位置，保证线程内状态独立
        Cursor cursor = new Cursor(text);
        // 跳过文本最前面的空白字符，容忍 ffprobe 输出可能带有的前导空行
        cursor.skipWhitespace();
        // 从游标当前位置解析一个完整的 JSON 值
        Object value = cursor.parseValue();
        // 返回解析得到的根对象，供调用方按 Map 结构逐层取值
        return value;
    }

    // 类型安全的取值助手：从对象中取出 String 值，取不到时返回默认值
    public static String getString(Map<String, Object> node, String key, String fallback) {
        // 对象本身为空说明调用方传入了不存在的层级，直接返回默认值
        if (node == null) {
            // 返回调用方提供的兜底字符串
            return fallback;
        }
        // 从 Map 中按键取值，值可能不存在或类型不是字符串
        Object raw = node.get(key);
        // 值不存在时返回默认值，避免把 null 暴露给上层
        if (raw == null) {
            // 返回兜底字符串
            return fallback;
        }
        // 统一用 String.valueOf 转成字符串，兼容 ffprobe 把数字写成字符串或纯数字的情况
        return String.valueOf(raw);
    }

    // 类型安全的取值助手：从对象中取出 double 数值，取不到或格式错误时返回默认值
    public static double getDouble(Map<String, Object> node, String key, double fallback) {
        // 对象为空时直接返回默认值，防止空指针异常
        if (node == null) {
            // 返回调用方提供的兜底数值
            return fallback;
        }
        // 按键取出原始值，可能是 Double、String 或 null
        Object raw = node.get(key);
        // 原始值为空时返回默认值
        if (raw == null) {
            // 返回兜底数值
            return fallback;
        }
        // 原始值已经是 Double 时可以直接强转返回，省去字符串解析开销
        if (raw instanceof Double) {
            // 直接返回已解析好的双精度浮点值
            return (Double) raw;
        }
        // 对其它类型（主要是 String）尝试做数值解析
        try {
            // 使用双精度解析字符串，兼容 "12.5" 与 "12" 两种写法
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            // 解析失败说明该字段不是数值语义，返回默认值而不是抛出异常
            return fallback;
        }
    }

    // 类型安全的取值助手：从对象中取出 int 数值，内部复用 double 取值再取整
    public static int getInt(Map<String, Object> node, String key, int fallback) {
        // 先按 double 取值并指定兜底值，保证语义一致
        double parsed = getDouble(node, key, fallback);
        // 四舍五入转成整型，例如 29.97 帧率取整为 30 时使用
        return (int) Math.round(parsed);
    }

    // 类型安全的取值助手：从对象中取出子对象，方便逐层下钻 ffprobe 的嵌套结构
    public static Map<String, Object> getObject(Map<String, Object> node, String key) {
        // 父节点为空时无法继续下钻，返回 null
        if (node == null) {
            // 返回空表示该层不存在
            return null;
        }
        // 取出键对应的原始值
        Object raw = node.get(key);
        // 值确实是 Map 时做安全强转后返回
        if (raw instanceof Map) {
            // 使用未检查强转，因为解析器保证对象节点一定是 Map<String,Object>
            @SuppressWarnings("unchecked")
            // 局部变量承载强转结果，便于后续 return
            Map<String, Object> typed = (Map<String, Object>) raw;
            // 返回强转后的对象节点
            return typed;
        }
        // 类型不匹配时返回 null，让上层走默认分支
        return null;
    }

    // 类型安全的取值助手：从对象中取出数组节点，用于遍历 streams 或 tags 列表
    public static List<Object> getArray(Map<String, Object> node, String key) {
        // 父节点为空时无法取值，返回 null
        if (node == null) {
            // 返回空表示没有该数组
            return null;
        }
        // 取出键对应的原始值
        Object raw = node.get(key);
        // 值确实是 List 时做安全强转后返回
        if (raw instanceof List) {
            // 使用未检查强转，解析器保证数组节点一定是 List<Object>
            @SuppressWarnings("unchecked")
            // 局部变量承载强转结果
            List<Object> typed = (List<Object>) raw;
            // 返回强转后的数组节点
            return typed;
        }
        // 类型不匹配时返回 null
        return null;
    }

    // 静态嵌套类 Cursor：封装字符数组与读取位置，实现递归下降解析
    private static final class Cursor {

        // 保存待解析的 JSON 字符数组，使用 char[] 而不是 String 是为了避免频繁的子串分配
        private final char[] data;
        // 当前读取位置（下一个待读字符的下标）
        private int pos;

        // 构造方法：接收原始文本并转为字符数组
        Cursor(String text) {
            // 把字符串转成字符数组保存，后续所有读取都基于数组下标
            this.data = text.toCharArray();
            // 读取位置从 0 开始
            this.pos = 0;
        }

        // 跳过空白字符（空格、制表符、换行、回车），JSON 允许任意位置出现这些字符
        void skipWhitespace() {
            // 循环直到读取位置越界或遇到非空白字符
            while (pos < data.length) {
                // 取出当前字符用于判断
                char ch = data[pos];
                // 四种空白字符都需要被跳过
                if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                    // 位置前移一位，继续判断下一个字符
                    pos++;
                } else {
                    // 遇到非空白字符立即结束跳过流程
                    break;
                }
            }
        }

        // 解析一个 JSON 值：根据首字符决定进入对象、数组、字符串、数字或字面量分支
        Object parseValue() {
            // 跳过值前面的空白，保证首字符判断准确
            skipWhitespace();
            // 位置越界说明文本被截断，返回 null 由上层兜底
            if (pos >= data.length) {
                // 返回空值表示没有内容可解析
                return null;
            }
            // 读取当前首字符
            char ch = data[pos];
            // 首字符为 { 时按对象解析
            switch (ch) {
                // 对象分支
                case '{':
                    // 委托给 parseObject 处理，返回值同样是 Map
                    return parseObject();
                // 数组分支
                case '[':
                    // 委托给 parseArray 处理，返回值是 List
                    return parseArray();
                // 字符串分支
                case '"':
                    // 委托给 parseString 处理，返回去引号后的内容
                    return parseString();
                // 布尔真值分支
                case 't':
                    // 直接消费 true 常量并返回布尔真
                    return parseLiteral("true", Boolean.TRUE);
                // 布尔假值分支
                case 'f':
                    // 直接消费 false 常量并返回布尔假
                    return parseLiteral("false", Boolean.FALSE);
                // 空值分支
                case 'n':
                    // 直接消费 null 常量并返回 null
                    return parseLiteral("null", null);
                // 其余字符（含负号与非零数字）按数字解析
                default:
                    // 委托给 parseNumber 处理
                    return parseNumber();
            }
        }

        // 解析字面量常量（true / false / null），校验拼写后前移游标
        private Object parseLiteral(String literal, Object value) {
            // 判断剩余文本是否以期望的字面量开头
            if (pos + literal.length() <= data.length
                    // 逐字符比较，精确匹配才能消费
                    && new String(data, pos, literal.length()).equals(literal)) {
                // 匹配成功则把游标前移整个字面量长度
                pos += literal.length();
                // 返回对应的 Java 值
                return value;
            }
            // 拼写不匹配时抛出异常，因为无法确定语义
            throw new IllegalArgumentException("Invalid JSON literal at position " + pos);
        }

        // 解析 JSON 对象，逐对读取键值并写入 LinkedHashMap
        private Map<String, Object> parseObject() {
            // 使用 LinkedHashMap 保持键顺序，调试日志更接近 ffprobe 原始输出
            Map<String, Object> map = new LinkedHashMap<>();
            // 消费开头的 { 字符
            pos++;
            // 循环读取成员直到遇到 } 或文本结束
            while (true) {
                // 跳过成员前的空白
                skipWhitespace();
                // 越界说明对象未闭合，属于非法 JSON
                if (pos >= data.length) {
                    // 抛出异常让上层尽快暴露问题
                    throw new IllegalArgumentException("Unterminated JSON object");
                }
                // 读到 } 说明对象结束
                if (data[pos] == '}') {
                    // 消费 } 并结束循环
                    pos++;
                    // 跳出循环返回结果
                    break;
                }
                // 读到逗号说明成员之间的分隔符，跳过它继续读下一个键
                if (data[pos] == ',') {
                    // 消费逗号
                    pos++;
                    // 回到循环开头继续处理下一个键值对
                    continue;
                }
                // 对象键必须是字符串，因此此处调用 parseString
                String key = parseString();
                // 跳过键后的空白
                skipWhitespace();
                // 键与值之间必须有冒号
                if (pos < data.length && data[pos] == ':') {
                    // 消费冒号
                    pos++;
                } else {
                    // 缺少冒号说明结构非法，直接抛异常
                    throw new IllegalArgumentException("Missing ':' in JSON object at " + pos);
                }
                // 解析该键对应的值
                Object value = parseValue();
                // 把键值对写入结果 Map
                map.put(key, value);
            }
            // 返回解析完成的对象
            return map;
        }

        // 解析 JSON 数组，逐个读取元素并写入 ArrayList
        private List<Object> parseArray() {
            // 使用 ArrayList 保存元素，顺序即原始顺序
            List<Object> list = new ArrayList<>();
            // 消费开头的 [ 字符
            pos++;
            // 循环读取元素直到遇到 ] 或文本结束
            while (true) {
                // 跳过元素前的空白
                skipWhitespace();
                // 越界说明数组未闭合，属于非法 JSON
                if (pos >= data.length) {
                    // 抛出异常暴露问题
                    throw new IllegalArgumentException("Unterminated JSON array");
                }
                // 读到 ] 说明数组结束
                if (data[pos] == ']') {
                    // 消费 ] 并结束循环
                    pos++;
                    // 跳出循环返回结果
                    break;
                }
                // 读到逗号说明元素分隔符，跳过它继续读下一个元素
                if (data[pos] == ',') {
                    // 消费逗号
                    pos++;
                    // 回到循环开头继续处理下一个元素
                    continue;
                }
                // 解析一个元素并加入列表
                list.add(parseValue());
            }
            // 返回解析完成的数组
            return list;
        }

        // 解析 JSON 字符串，处理常见转义序列
        private String parseString() {
            // 跳过引号前的空白，保证定位到开引号
            skipWhitespace();
            // 期望当前字符是双引号
            if (pos < data.length && data[pos] == '"') {
                // 消费开引号
                pos++;
            } else {
                // 不是引号说明调用方误用，抛出异常
                throw new IllegalArgumentException("Expected '\"' at position " + pos);
            }
            // 使用 StringBuilder 累积字符串内容，避免反复拼接
            StringBuilder builder = new StringBuilder();
            // 循环读取直到遇到收引号
            while (pos < data.length) {
                // 取出当前字符
                char ch = data[pos];
                // 遇到反斜杠说明后面是转义序列
                if (ch == '\\') {
                    // 先消费反斜杠本身
                    pos++;
                    // 越界说明转义被截断
                    if (pos >= data.length) {
                        // 抛出异常暴露问题
                        throw new IllegalArgumentException("Unterminated escape sequence");
                    }
                    // 读取转义标识字符
                    char escaped = data[pos];
                    // 消费转义标识字符
                    pos++;
                    // 按转义标识映射为真实字符
                    switch (escaped) {
                        // \n 表示换行
                        case 'n':
                            // 追加换行符
                            builder.append('\n');
                            // 结束该次转义处理
                            break;
                        // \t 表示制表符
                        case 't':
                            // 追加制表符
                            builder.append('\t');
                            // 结束该次转义处理
                            break;
                        // \r 表示回车
                        case 'r':
                            // 追加回车符
                            builder.append('\r');
                            // 结束该次转义处理
                            break;
                        // \" 表示双引号本身
                        case '"':
                            // 追加双引号
                            builder.append('"');
                            // 结束该次转义处理
                            break;
                        // \\ 表示反斜杠本身
                        case '\\':
                            // 追加反斜杠
                            builder.append('\\');
                            // 结束该次转义处理
                            break;
                        // \/ 表示斜杠本身
                        case '/':
                            // 追加斜杠
                            builder.append('/');
                            // 结束该次转义处理
                            break;
                        // 反斜杠加 u 的组合表示四位十六进制码点，这里按转义序列处理
                        case 'u':
                            // 读取四位十六进制字符构造码点
                            if (pos + 4 <= data.length) {
                                // 解析十六进制并追加对应字符
                                builder.append((char) Integer.parseInt(new String(data, pos, 4), 16));
                                // 游标前移四位
                                pos += 4;
                            }
                            // 结束该次转义处理
                            break;
                        // 其它未知转义按原样追加
                        default:
                            // 保留原字符，兼容 ffprobe 可能出现的非标准转义
                            builder.append(escaped);
                            // 结束该次转义处理
                            break;
                    }
                    // 转义处理完成后回到循环继续读取
                    continue;
                }
                // 遇到收引号说明字符串结束
                if (ch == '"') {
                    // 消费收引号
                    pos++;
                    // 跳出循环返回已累积的字符串
                    break;
                }
                // 普通字符直接追加
                builder.append(ch);
                // 游标前移一位
                pos++;
            }
            // 返回解析出的字符串内容
            return builder.toString();
        }

        // 解析 JSON 数字，按普通规则读取到分隔符为止
        private Object parseNumber() {
            // 记录数字起始位置，便于最后做一次子串解析
            int start = pos;
            // 循环读取属于数字的字符
            while (pos < data.length) {
                // 取出当前字符
                char ch = data[pos];
                // 数字允许数字、负号、正号、小数点与科学计数法标识
                boolean partOfNumber = (ch >= '0' && ch <= '9')
                        // 负号属于数字的一部分
                        || ch == '-'
                        // 正号属于数字的一部分
                        || ch == '+'
                        // 小数点在科学计数法中出现
                        || ch == '.'
                        // e/E 用于指数表示
                        || ch == 'e'
                        // 大写 E 同样用于指数表示
                        || ch == 'E';
                // 一旦遇到非数字字符就停止
                if (!partOfNumber) {
                    // 跳出循环
                    break;
                }
                // 游标前移继续读取
                pos++;
            }
            // 截取数字文本并解析为 Double，统一使用双精度以兼容小数
            return Double.valueOf(new String(data, start, pos - start));
        }
    }
}
