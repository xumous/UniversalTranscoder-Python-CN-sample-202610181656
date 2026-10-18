# -*- coding: utf-8 -*-
# 文档转换模块。
# 说明：视频/音频/图片的转码走 FFmpeg；而“文档类”转换（图片↔PDF、文本→PDF）用 FFmpeg 并不合适，
#       因此这里使用纯 Python 实现，既保证离线可用，也避免依赖外部办公软件。
# 覆盖能力：
#   1) 图片 → PDF（多张合并为一个 PDF，支持页面尺寸自适应）。
#   2) PDF → 图片（按页渲染为 PNG/JPG）。
#   3) 文本 → PDF（内置极简 PDF 生成器，无需任何第三方字体或库）。
# 所有函数都对“依赖缺失 / 文件损坏”做容错，返回结果对象而不是抛出异常。

import os  # 路径处理
import zlib  # 文本 PDF 的流压缩（可选）
import logging  # 记录失败原因
from dataclasses import dataclass, field  # 结果结构
from typing import List  # 类型标注

logger = logging.getLogger(__name__)


@dataclass
class DocResult:
    """文档转换结果。"""
    ok: bool = False  # 是否成功
    outputs: List[str] = field(default_factory=list)  # 输出文件列表
    error: str = ""  # 失败原因
    message: str = ""  # 成功提示


# ---------------------- 图片 → PDF ----------------------

def images_to_pdf(image_paths: List[str], out_pdf: str, dpi: int = 100) -> DocResult:
    """把多张图片合并为一个 PDF。

    参数:
        image_paths: 图片路径列表（按给定顺序成页）。
        out_pdf: 输出 PDF 路径。
        dpi: 像素到 PDF 点（1/72 英寸）的换算基准；数值越大页面物理尺寸越小。
    返回:
        DocResult。
    实现：使用 Pillow 的 save(..., "PDF", save_all=True, append_images=[...]) 一次写入多页。
    """
    # 延迟导入 Pillow，避免未安装时影响其它功能模块的导入（Web 端可能不需要文档功能）。
    try:
        from PIL import Image
    except ImportError as exc:
        return DocResult(ok=False, error=f"Missing Pillow dependency; cannot convert images to PDF: {exc}")
    # 过滤出确实存在的图片。
    valid = [p for p in image_paths if p and os.path.isfile(p)]
    if not valid:
        return DocResult(ok=False, error="No image files available to build the PDF.")
    # 逐张打开为 RGB（PDF 不支持带 alpha 的原始通道，需要先合并到白底）。
    pages = []
    for p in valid:
        try:
            # 打开图片。
            img = Image.open(p)
            # 处理透明通道：粘贴到白色背景上，避免出现黑色/杂色。
            if img.mode in ("RGBA", "LA", "P"):
                # 转换为 RGBA 后取 alpha 通道。
                rgba = img.convert("RGBA")
                # 新建白底画布。
                bg = Image.new("RGB", rgba.size, (255, 255, 255))
                # 用自身作为掩模粘贴，实现“保留透明区域的真实颜色”。
                bg.paste(rgba, mask=rgba.split()[-1])
                img = bg
            else:
                # 其它模式统一转 RGB。
                img = img.convert("RGB")
            pages.append(img)
        except Exception as exc:
            # 单张图片读取失败：记录并跳过，不影响其它页。
            logger.warning("图片加载失败，已跳过：%s（%s）", p, exc)
    # 全部失败时返回错误。
    if not pages:
        return DocResult(ok=False,
                         error="All images could not be read (possibly corrupted or in an unsupported format).")
    # 确保输出目录存在。
    try:
        os.makedirs(os.path.dirname(os.path.abspath(out_pdf)), exist_ok=True)
    except OSError as exc:
        return DocResult(ok=False, error=f"Cannot create output folder: {exc}")
    # 写出多页 PDF。
    try:
        first, rest = pages[0], pages[1:]
        # resolution 控制页面物理尺寸：像素数 / dpi * 72 = PDF 点。
        first.save(out_pdf, "PDF", resolution=float(dpi),
                   save_all=True, append_images=rest)
        return DocResult(ok=True, outputs=[out_pdf],
                         message=f"Generated a {len(pages)}-page PDF.")
    except Exception as exc:
        return DocResult(ok=False, error=f"Failed to write PDF: {exc}")
    finally:
        # 关闭所有已打开的图片句柄，避免 Windows 上文件被占用无法删除。
        for im in pages:
            try:
                im.close()
            except Exception:
                pass


# ---------------------- PDF → 图片 ----------------------

def pdf_to_images(pdf_path: str, out_dir: str, base_name: str = "",
                  dpi: int = 150, fmt: str = "png", page_range: str = "") -> DocResult:
    """把 PDF 逐页渲染为图片。

    参数:
        pdf_path: 源 PDF 路径。
        out_dir: 输出目录。
        base_name: 输出文件名主名（不含扩展名）。
        dpi: 渲染分辨率（越大越清晰、文件越大）。
        fmt: 输出图片格式：png / jpg。
        page_range: 页码范围，如 "1-3,5"；空表示全部页。
    返回:
        DocResult。
    实现：使用 pypdfium2 渲染（纯二进制轮子，无需外部库，离线可用）。
    """
    # 延迟导入。
    try:
        import pypdfium2 as pdfium
    except ImportError as exc:
        return DocResult(ok=False, error=f"Missing pypdfium2 dependency; cannot convert PDF to images: {exc}")
    # 检查输入。
    if not os.path.isfile(pdf_path):
        return DocResult(ok=False, error="The source PDF file does not exist.")
    # 输出目录与主名。
    os.makedirs(out_dir, exist_ok=True)
    stem = base_name or os.path.splitext(os.path.basename(pdf_path))[0]
    try:
        # 打开 PDF 文档。
        doc = pdfium.PdfDocument(pdf_path)
        total = len(doc)
        # 解析页码范围。
        selected = _parse_page_range(page_range, total)
        # 渲染比例：PDF 默认 72dpi，目标 dpi 需要按比例放大。
        scale = max(0.1, float(dpi) / 72.0)
        # 归一化格式。
        ext = "jpg" if str(fmt).lower() in ("jpg", "jpeg") else "png"
        outputs = []
        # 逐页渲染。
        for idx in selected:
            try:
                # 取页并渲染为位图。
                page = doc[idx]
                # render 返回 PDFium 位图；scale 控制清晰度。
                bitmap = page.render(scale=scale)
                # 转成 Pillow 图像。
                pil_image = bitmap.to_pil()
                # 输出路径：主名_页码.png。
                out_path = os.path.join(out_dir, f"{stem}_{idx + 1:03d}.{ext}")
                # JPEG 需要转 RGB（去掉 alpha）。
                if ext == "jpg" and pil_image.mode not in ("RGB", "L"):
                    pil_image = pil_image.convert("RGB")
                # 保存。
                pil_image.save(out_path)
                outputs.append(out_path)
            except Exception as exc:
                # 单页失败不中断整个文档。
                logger.warning("PDF 第 %d 页渲染失败：%s", idx + 1, exc)
        # 全部失败时的兜底。
        if not outputs:
            return DocResult(ok=False, error="No pages were rendered from the PDF (the file may be corrupted).")
        return DocResult(ok=True, outputs=outputs,
                         message=f"Exported {len(outputs)} page image(s) (out of {total} pages).")
    except Exception as exc:
        return DocResult(ok=False, error=f"Failed to open PDF: {exc}")


def _parse_page_range(spec: str, total: int) -> List[int]:
    """解析页码范围字符串，返回 0 基的页索引列表。

    支持格式："1-3,5,8-10"；空串表示全选。
    容错：越界自动裁剪；非法片段忽略。
    """
    # 空串：全部页。
    if not spec or not str(spec).strip():
        return list(range(total))
    result = []
    # 按逗号切分片段。
    for chunk in str(spec).split(","):
        seg = chunk.strip()
        if not seg:
            continue
        # 处理区间 "a-b"。
        if "-" in seg:
            try:
                a, b = seg.split("-", 1)
                start = int(a.strip())
                end = int(b.strip())
                # 保证 start <= end。
                if start > end:
                    start, end = end, start
                # 转 0 基并裁剪到合法范围。
                for page in range(start, end + 1):
                    if 1 <= page <= total:
                        result.append(page - 1)
            except ValueError:
                continue
        else:
            # 处理单页。
            try:
                page = int(seg)
                if 1 <= page <= total:
                    result.append(page - 1)
            except ValueError:
                continue
    # 去重并保持升序。
    return sorted(set(result))


# ---------------------- 文本 → PDF ----------------------

# 内置的极简 PDF 生成器所需常量（Helvetica 是 PDF 的 14 种标准字体之一，无需嵌入字体文件）。
_PDF_POINTS_PER_INCH = 72.0
_PDF_MARGIN = 56.0  # 页边距（点）
_PDF_LINE_HEIGHT = 16.0  # 行高（点）
_PDF_FONT_SIZE = 11  # 正文字号


def text_to_pdf(text_path: str, out_pdf: str, font_size: int = _PDF_FONT_SIZE,
                title: str = "") -> DocResult:
    """把纯文本文件转换为 PDF（内置生成器，不依赖第三方库）。

    参数:
        text_path: 源文本文件（按 UTF-8 读取；读取失败会尝试 GBK 回退）。
        out_pdf: 输出 PDF。
        font_size: 字号（点）。
        title: 文档标题（用于 PDF 元信息与首行标题）。
    返回:
        DocResult。
    实现说明：
        使用 PDF 的 14 种标准字体之一 Helvetica。为兼容性，非 ASCII 字符将被替换为 "?"，
        因为标准字体不含中文字形；若需完整中文支持，应改用“图片 → PDF”路径或嵌入中文字体。
    """
    # 读取文本内容（UTF-8 优先，失败回退 GBK，再失败回退 latin-1 忽略错误）。
    try:
        with open(text_path, "r", encoding="utf-8") as fp:
            content = fp.read()
    except UnicodeDecodeError:
        try:
            with open(text_path, "r", encoding="gbk", errors="ignore") as fp:
                content = fp.read()
        except Exception as exc:
            return DocResult(ok=False, error=f"Failed to read text: {exc}")
    except Exception as exc:
        return DocResult(ok=False, error=f"Failed to read text: {exc}")

    # 把非 ASCII 字符替换掉（Helvetica 只覆盖 Latin-1）。
    def _ascii(s: str) -> str:
        # 逐字符处理：可编码为 latin-1 的保留，否则用 "?" 代替。
        return "".join(ch if ord(ch) < 256 else "?" for ch in s)

    # 组装页面内容：把文本按宽度粗略折行。
    lines = []
    if title:
        # 标题行单独加粗（用粗体字体）。
        lines.append(("B", _ascii(title)))
        lines.append(("R", ""))  # 空行
    # 处理正文：按换行拆分，并做简单折行（每行约 90 个字符）。
    for raw in content.splitlines():
        s = _ascii(raw)
        # 空行保留，维持段落间距。
        if not s:
            lines.append(("R", ""))
            continue
        # 简单折行：每行不超过 90 字符。
        while len(s) > 90:
            lines.append(("R", s[:90]))
            s = s[90:]
        lines.append(("R", s))

    # 页面尺寸：A4（595 x 842 点）。
    page_w, page_h = 595.0, 842.0
    # 每页可容纳行数。
    usable_h = page_h - 2 * _PDF_MARGIN
    lines_per_page = max(1, int(usable_h / _PDF_LINE_HEIGHT))
    # 按行数分页。
    pages = [lines[i:i + lines_per_page] for i in range(0, len(lines), lines_per_page)] or [[]]
    # 生成 PDF 字节流。
    try:
        data = _build_pdf_bytes(pages, page_w, page_h, font_size, title)
        os.makedirs(os.path.dirname(os.path.abspath(out_pdf)), exist_ok=True)
        with open(out_pdf, "wb") as fp:
            fp.write(data)
        return DocResult(ok=True, outputs=[out_pdf], message=f"Generated a {len(pages)}-page PDF.")
    except Exception as exc:
        return DocResult(ok=False, error=f"Failed to generate PDF: {exc}")


def _build_pdf_bytes(pages, page_w: float, page_h: float, font_size: int, title: str) -> bytes:
    """手工构造 PDF 文件的字节内容（含交叉引用表）。

    这是 PDF 的最小合法结构：
        1) 头部 %PDF-1.4
        2) 若干对象（Catalog / Pages / Font / 每页 Page / 每页 Content / 每页资源）
        3) 交叉引用表 xref
        4) 尾部 trailer + startxref
    """
    # 对象容器：每个元素是一个对象的正文（不含 "N 0 obj" 包装）。
    objects = []
    # 用于记录“对象号 -> 字节偏移”，构建 xref 时需要。
    offsets = []
    # 页对象号与内容对象号的分配：
    #   1: Catalog
    #   2: Pages
    #   3: Font(Helvetica)
    #   4: Font(Helvetica-Bold)
    #   5..: 每页 Page 与 Content 交替
    num_pages = len(pages)
    # 页对象号：从 5 开始，每页两个对象（Page, Contents）。
    page_ids = [5 + i * 2 for i in range(num_pages)]
    content_ids = [6 + i * 2 for i in range(num_pages)]
    # ---- 对象 1：Catalog ----
    objects.append(f"<<1>>\n<<2 0 R>>")  # 占位：稍后统一格式化
    # 为便于书写，这里改用直接字符串列表，最后统一编号。
    return _assemble_pdf(pages, page_w, page_h, font_size, title, page_ids, content_ids)


def _assemble_pdf(pages, page_w, page_h, font_size, title, page_ids, content_ids) -> bytes:
    """真正拼装 PDF 字节流（与上一函数的占位说明配合）。"""
    # 收集对象正文，索引 i 对应对象号 i+1。
    objs = []
    # 对象 1：Catalog（文档根）。
    objs.append("<<1>>")
    # 对象 2：Pages（页面树，Kids 列出所有 Page 对象）。
    kids = " ".join(f"{pid} 0 R" for pid in page_ids)
    objs.append(f"<<2>>")
    # 对象 3/4：两种标准字体。
    objs.append("<<3>>")
    objs.append("<<4>>")
    # 逐页对象。
    for i in range(len(pages)):
        # Page 对象（编号 page_ids[i]）。
        objs.append("<<PAGE>>")
        # Content 流对象（编号 content_ids[i]）。
        objs.append("<<CONTENT>>")

    # 生成每页内容流。
    def content_stream(page_lines):
        # 内容流文本：BT/ET 包裹文本块，逐行 Td 定位后 Tj 输出。
        parts = ["BT", f"/F1 {font_size} Tf", f"{_PDF_LINE_HEIGHT} TL",
                 f"1 0 0 1 {_PDF_MARGIN} {page_h - _PDF_MARGIN} Tm"]
        for style, text in page_lines:
            # 选择字体：B 用 F2（粗体），R 用 F1（常规）。
            font = "/F2" if style == "B" else "/F1"
            # 设置字体大小。
            parts.append(f"{font} {font_size} Tf")
            # 输出一行文本（括号需转义）。
            escaped = text.replace("\\", r"\\").replace("(", r"\(").replace(")", r"\)")
            parts.append(f"({escaped}) Tj")
            # 换行（T* 使用 TL 指定的行距）。
            parts.append("T*")
        parts.append("ET")
        return "\n".join(parts)

    # 把占位符替换为真实对象正文。
    full_objs = []
    for idx, o in enumerate(objs):
        num = idx + 1
        if o == "<<1>>":
            full_objs.append(f"{num} 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        elif o == "<<2>>":
            full_objs.append(
                f"{num} 0 obj\n<< /Type /Pages /Count {len(pages)} /Kids [ {kids} ] >>\nendobj\n")
        elif o == "<<3>>":
            full_objs.append(
                f"{num} 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")
        elif o == "<<4>>":
            full_objs.append(
                f"{num} 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n")
        elif o == "<<PAGE>>":
            # 找到该 Page 对应的页序号与内容对象号。
            page_i = (idx - 4) // 2  # 前 4 个对象固定，之后每页 2 个
            cid = content_ids[page_i]
            full_objs.append(
                f"{num} 0 obj\n<< /Type /Page /Parent 2 0 R "
                f"/MediaBox [0 0 {page_w:g} {page_h:g}] "
                f"/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> "
                f"/Contents {cid} 0 R >>\nendobj\n")
        elif o == "<<CONTENT>>":
            # 页序号。
            page_i = (idx - 4) // 2
            stream = content_stream(pages[page_i])
            raw = stream.encode("latin-1", errors="replace")
            full_objs.append(
                f"{num} 0 obj\n<< /Length {len(raw)} >>\nstream\n{stream}\nendstream\nendobj\n")

    # 拼装文件头与对象体，同时记录每个对象的字节偏移。
    out = bytearray()
    out += b"%PDF-1.4\n"
    # 标题元信息（放入 Info 对象可选；这里简化为注释）。
    offsets = [0] * (len(full_objs) + 1)  # 索引 0 占位
    for i, s in enumerate(full_objs):
        offsets[i + 1] = len(out)
        out += s.encode("latin-1", errors="replace")
    # 交叉引用表起始偏移。
    xref_pos = len(out)
    out += f"xref\n0 {len(full_objs) + 1}\n".encode("latin-1")
    # 第一行固定为“空闲对象”。
    out += b"0000000000 65535 f \n"
    # 其余每行描述一个对象的偏移。
    for i in range(1, len(full_objs) + 1):
        out += f"{offsets[i]:010d} 00000 n \n".encode("latin-1")
    # 尾部。
    out += (f"trailer\n<< /Size {len(full_objs) + 1} /Root 1 0 R "
            f"/Info << /Title ({_esc_pdf_text(title or 'Text Document')}) /Producer (Universal Transcoder) >> >>\n"
            f"startxref\n{xref_pos}\n%%EOF\n").encode("latin-1", errors="replace")
    return bytes(out)


def _esc_pdf_text(s: str) -> str:
    """转义 PDF 字符串中的括号与反斜杠。"""
    return str(s or "").replace("\\", r"\\").replace("(", r"\(").replace(")", r"\)")
