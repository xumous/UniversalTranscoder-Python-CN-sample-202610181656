// 文档转换页：图片转 PDF、PDF 转图片、文本转 PDF 三个子功能，各自独立成卡并按需切换。
import {reactive} from 'vue'                                          // 引入响应式工厂（用于子页签状态）
import {store, openBrowser, runDoc, openPath} from '../store.js'      // 引入状态与文档转换动作
import {Select, Input, Section, PathInput, Btn} from './ui.jsx'       // 引入通用控件
import {toInt, nameOf} from '../format.js'                            // 引入数值转换与文件名工具

// 当前子功能标识放在模块级，切换页面后仍保留选择。
const view = reactive({sub: 'images_to_pdf'})

// PDF 转图片的输出格式候选。
const PDF_IMG_FORMATS = [
    {value: 'png', label: 'PNG (lossless)'},   // PNG
    {value: 'jpg', label: 'JPG (smaller size)'},   // JPEG
    {value: 'webp', label: 'WebP'},           // WebP
    {value: 'bmp', label: 'BMP'},             // BMP
    {value: 'tiff', label: 'TIFF'}            // TIFF
]

// 子功能页签定义。
const SUBS = [
    {key: 'images_to_pdf', label: 'Images → PDF'},   // 多张图片合成一个 PDF
    {key: 'pdf_to_images', label: 'PDF → Images'},   // 逐页渲染为图片
    {key: 'text_to_pdf', label: 'Text → PDF'}        // 纯文本排版为 PDF
]

// 连接目录与文件名，自动匹配 Windows 反斜杠或类 Unix 斜杠。
function joinPath(dir, name) {
    // 目录为空时直接返回文件名。
    if (!dir) return name
    // 目录以反斜杠结尾或包含反斜杠时按 Windows 风格拼接。
    const sep = dir.indexOf('\\') >= 0 || /^[A-Za-z]:/.test(dir) ? '\\' : '/'
    // 去掉目录末尾已有的分隔符再拼接。
    return dir.replace(/[\\/]+$/, '') + sep + name
}

// 打开图片多选弹窗，把所选图片追加进“图片转 PDF”列表。
function addImages() {
    // 打开弹窗。
    openBrowser({
        // 弹窗标题
        title: 'Add Images (multi-select, ordered as added)',
        // 只允许图片
        mode: 'image',
        // 允许复选
        multiple: true,
        // 选择回调
        callback: function (paths) {
            // 逐条追加并去重。
            for (const path of paths) {
                // 已在列表中的跳过。
                if (store.pdfImages.indexOf(path) >= 0) continue
                // 追加到末尾。
                store.pdfImages.push(path)
            }
        }
    })
}

// 在列表中移动某个图片的位置（delta 为 -1 上移，1 下移）。
function moveImage(index, delta) {
    // 计算目标下标。
    const target = index + delta
    // 越界时不做任何操作。
    if (target < 0 || target >= store.pdfImages.length) return
    // 取出待移动项。
    const item = store.pdfImages[index]
    // 从原位置移除。
    store.pdfImages.splice(index, 1)
    // 插入到目标位置。
    store.pdfImages.splice(target, 0, item)
}

// 组件本体。
export function DocPanel() {
    // 计算图片转 PDF 的输出路径（目录 + 文件名）。
    const imagesPdfOut = joinPath(store.pdfOutDir, store.pdfOutName)
    // 计算文本转 PDF 的输出路径。
    const txtPdfOut = joinPath(store.txtOutDir, store.txtOutName)
    // 渲染面板。
    return (
        // 面板根容器
        <div>
            {/* 子功能切换卡片 */}
            <Section title="Document Conversion">
                {/* 子页签按钮组 */}
                <div className="btn-row">
                    {/* 逐个渲染子功能按钮 */}
                    {SUBS.map((s) => (
                        // 单个子页签按钮
                        <Btn
                            key={s.key}                                             /* 稳定 key */
                            kind={view.sub === s.key ? 'primary' : ''}              /* 选中态使用主按钮样式 */
                            onClick={() => {
                                view.sub = s.key
                            }}                    /* 切换子功能 */
                        >
                            {/* 子功能名称 */}
                            {s.label}
                        </Btn>
                    ))}
                </div>
                {/* 说明文字 */}
                <div className="hint mt-8">Document conversions are performed directly by the backend kernel. They do
                    not use the transcode queue, and outputs are written to the location you specify.
                </div>
            </Section>

            {/* 图片转 PDF 卡片 */}
            {view.sub === 'images_to_pdf' ? (
                // 图片转 PDF 区块
                <Section
                    title="Images → PDF"
                    toolbar={() => (
                        // 标题右侧工具区
                        <div className="btn-row">
                            {/* 添加图片 */}
                            <Btn size="sm" kind="primary" onClick={addImages}>Add Images</Btn>
                            {/* 清空列表 */}
                            <Btn size="sm" kind="danger" disabled={!store.pdfImages.length} onClick={() => {
                                store.pdfImages = []
                            }}>Clear</Btn>
                        </div>
                    )}
                >
                    {/* 已选图片排序列表 */}
                    <div className="filelist-body">
                        {/* 空态提示 */}
                        {!store.pdfImages.length ? (
                            // 空列表文字
                            <div className="empty">No images added yet. Click "Add Images" at the top right to
                                start.</div>
                        ) : (
                            // 逐条渲染，并给出页码预览
                            store.pdfImages.map((path, i) => (
                                // 单个图片行
                                <div className="order-row" key={path}>
                                    {/* 页码预览标签 */}
                                    <span className="page-tag">{`Page ${i + 1}`}</span>
                                    {/* 文件名 */}
                                    <span className="browse-name" title={path}>{nameOf(path)}</span>
                                    {/* 上移按钮 */}
                                    <Btn size="sm" kind="ghost" disabled={i === 0} onClick={() => moveImage(i, -1)}>Move
                                        Up</Btn>
                                    {/* 下移按钮 */}
                                    <Btn size="sm" kind="ghost" disabled={i === store.pdfImages.length - 1}
                                         onClick={() => moveImage(i, 1)}>Move Down</Btn>
                                    {/* 移除按钮 */}
                                    <Btn size="sm" kind="ghost" onClick={() => {
                                        store.pdfImages.splice(i, 1)
                                    }}>Remove</Btn>
                                </div>
                            ))
                        )}
                    </div>
                    {/* 页码总数提示 */}
                    <div
                        className="hint mt-8">{store.pdfImages.length ? `${store.pdfImages.length} page(s), combined into a PDF in the order above.` : ''}</div>
                    {/* 参数栅格 */}
                    <div className="grid mt-8">
                        {/* 渲染 DPI */}
                        <Input
                            label="Render DPI"                                 /* 字段标签 */
                            unit="Affects page size and sharpness"               /* 单位提示 */
                            type="number"                                      /* 数字输入 */
                            min="36"                                           /* 最小 36 */
                            max="600"                                          /* 最大 600 */
                            value={store.pdfDpi}                               /* 当前值 */
                            onChange={(v) => {
                                store.pdfDpi = toInt(v, 100)
                            }} /* 变化回写 */
                        />
                        {/* 输出目录 */}
                        <PathInput
                            label="Output folder"                              /* 字段标签 */
                            mode="dir"                                         /* 目录浏览 */
                            title="Select PDF Output Folder"                   /* 弹窗标题 */
                            value={store.pdfOutDir}                            /* 当前值 */
                            onChange={(v) => {
                                store.pdfOutDir = v
                            }}          /* 变化回写 */
                        />
                        {/* 输出文件名 */}
                        <Input
                            label="Output file name"                           /* 字段标签 */
                            unit="Include extension"                            /* 单位提示 */
                            value={store.pdfOutName}                           /* 当前值 */
                            onChange={(v) => {
                                store.pdfOutName = v
                            }}         /* 变化回写 */
                            placeholder="e.g. merged.pdf"                       /* 提示文字 */
                        />
                    </div>
                    {/* 输出路径预览 */}
                    <div
                        className="hint mt-8">{`Will output to: ${imagesPdfOut || '(output location not set yet)'}`}</div>
                    {/* 执行按钮行 */}
                    <div className="btn-row mt-8">
                        {/* 开始转换 */}
                        <Btn
                            kind="primary"                                     /* 主按钮 */
                            disabled={store.docBusy || !store.pdfImages.length || !imagesPdfOut}  /* 缺输入或输出时禁用 */
                            onClick={() => runDoc({                              /* 执行转换 */
                                // 操作标识
                                op: 'images_to_pdf',
                                // 输入图片数组
                                inputs: store.pdfImages.slice(),
                                // 输出 PDF 路径
                                out: imagesPdfOut,
                                // 渲染 DPI
                                dpi: Number(store.pdfDpi) || 100
                            })}
                        >
                            {store.docBusy ? 'Converting…' : 'Start Conversion'}
                        </Btn>
                    </div>
                </Section>
            ) : null}

            {/* PDF 转图片卡片 */}
            {view.sub === 'pdf_to_images' ? (
                // PDF 转图片区块
                <Section title="PDF → Images">
                    {/* 参数栅格 */}
                    <div className="grid">
                        {/* 输入 PDF */}
                        <PathInput
                            label="Input PDF"                                  /* 字段标签 */
                            mode="pdf"                                         /* 只允许选 PDF */
                            title="Select PDF to Split"                        /* 弹窗标题 */
                            value={store.pdfIn}                                /* 当前值 */
                            onChange={(v) => {
                                store.pdfIn = v
                            }}              /* 变化回写 */
                        />
                        {/* 输出目录 */}
                        <PathInput
                            label="Output folder"                              /* 字段标签 */
                            mode="dir"                                         /* 目录浏览 */
                            title="Select Image Output Folder"                 /* 弹窗标题 */
                            value={store.pdfOutDir2}                           /* 当前值 */
                            onChange={(v) => {
                                store.pdfOutDir2 = v
                            }}         /* 变化回写 */
                        />
                        {/* 基础文件名 */}
                        <Input
                            label="Base file name"                             /* 字段标签 */
                            unit="Empty = use the PDF's original name"         /* 单位提示 */
                            value={store.pdfBaseName}                          /* 当前值 */
                            onChange={(v) => {
                                store.pdfBaseName = v
                            }}        /* 变化回写 */
                            placeholder="e.g. page"                             /* 提示文字 */
                        />
                        {/* 渲染 DPI */}
                        <Input
                            label="Render DPI"                                 /* 字段标签 */
                            type="number"                                      /* 数字输入 */
                            min="36"                                           /* 最小 36 */
                            max="600"                                          /* 最大 600 */
                            value={store.pdfToImgDpi}                          /* 当前值 */
                            onChange={(v) => {
                                store.pdfToImgDpi = toInt(v, 150)
                            }}  /* 变化回写 */
                        />
                        {/* 输出格式 */}
                        <Select
                            label="Output format"                              /* 字段标签 */
                            value={store.pdfToImgFmt}                          /* 当前值 */
                            options={PDF_IMG_FORMATS}                          /* 选项 */
                            onChange={(v) => {
                                store.pdfToImgFmt = v
                            }}        /* 变化回写 */
                        />
                        {/* 页码范围 */}
                        <Input
                            label="Page range"                                 /* 字段标签 */
                            unit="e.g. 1-3 or 2,5,7"                            /* 单位提示 */
                            value={store.pdfPageRange}                         /* 当前值 */
                            onChange={(v) => {
                                store.pdfPageRange = v
                            }}       /* 变化回写 */
                            placeholder="Empty = all pages"                     /* 提示文字 */
                        />
                    </div>
                    {/* 执行按钮行 */}
                    <div className="btn-row mt-8">
                        {/* 开始转换 */}
                        <Btn
                            kind="primary"                                     /* 主按钮 */
                            disabled={store.docBusy || !store.pdfIn || !store.pdfOutDir2}  /* 缺输入或输出时禁用 */
                            onClick={() => runDoc({                              /* 执行转换 */
                                // 操作标识
                                op: 'pdf_to_images',
                                // 输入 PDF
                                input: store.pdfIn,
                                // 输出目录
                                out_dir: store.pdfOutDir2,
                                // 基础文件名
                                base_name: store.pdfBaseName,
                                // 渲染 DPI
                                dpi: Number(store.pdfToImgDpi) || 150,
                                // 输出格式
                                fmt: store.pdfToImgFmt,
                                // 页码范围
                                page_range: store.pdfPageRange
                            })}
                        >
                            {store.docBusy ? 'Converting…' : 'Start Conversion'}
                        </Btn>
                    </div>
                </Section>
            ) : null}

            {/* 文本转 PDF 卡片 */}
            {view.sub === 'text_to_pdf' ? (
                // 文本转 PDF 区块
                <Section title="Text → PDF">
                    {/* 参数栅格 */}
                    <div className="grid">
                        {/* 输入文本 */}
                        <PathInput
                            label="Input text"                                 /* 字段标签 */
                            mode="text"                                        /* 只允许选文本 */
                            title="Select Text File to Lay Out as PDF"         /* 弹窗标题 */
                            value={store.txtIn}                                /* 当前值 */
                            onChange={(v) => {
                                store.txtIn = v
                            }}              /* 变化回写 */
                        />
                        {/* 输出目录 */}
                        <PathInput
                            label="Output folder"                              /* 字段标签 */
                            mode="dir"                                         /* 目录浏览 */
                            title="Select PDF Output Folder"                   /* 弹窗标题 */
                            value={store.txtOutDir}                            /* 当前值 */
                            onChange={(v) => {
                                store.txtOutDir = v
                            }}          /* 变化回写 */
                        />
                        {/* 输出文件名 */}
                        <Input
                            label="Output file name"                           /* 字段标签 */
                            unit="Include extension"                            /* 单位提示 */
                            value={store.txtOutName}                           /* 当前值 */
                            onChange={(v) => {
                                store.txtOutName = v
                            }}         /* 变化回写 */
                            placeholder="e.g. document.pdf"                     /* 提示文字 */
                        />
                        {/* 字号 */}
                        <Input
                            label="Font size"                                  /* 字段标签 */
                            unit="pt"                                           /* 单位提示 */
                            type="number"                                      /* 数字输入 */
                            min="6"                                            /* 最小 6 */
                            max="48"                                           /* 最大 48 */
                            value={store.txtFontSize}                          /* 当前值 */
                            onChange={(v) => {
                                store.txtFontSize = toInt(v, 11)
                            }}  /* 变化回写 */
                        />
                        {/* 标题 */}
                        <Input
                            label="Document title"                             /* 字段标签 */
                            value={store.txtTitle}                             /* 当前值 */
                            onChange={(v) => {
                                store.txtTitle = v
                            }}           /* 变化回写 */
                            placeholder="Empty = no title"                      /* 提示文字 */
                            span                                              /* 占两列 */
                        />
                    </div>
                    {/* 输出路径预览 */}
                    <div className="hint mt-8">{`Will output to: ${txtPdfOut || '(output location not set yet)'}`}</div>
                    {/* 执行按钮行 */}
                    <div className="btn-row mt-8">
                        {/* 开始转换 */}
                        <Btn
                            kind="primary"                                     /* 主按钮 */
                            disabled={store.docBusy || !store.txtIn || !txtPdfOut}  /* 缺输入或输出时禁用 */
                            onClick={() => runDoc({                              /* 执行转换 */
                                // 操作标识
                                op: 'text_to_pdf',
                                // 输入文本
                                input: store.txtIn,
                                // 输出 PDF
                                out: txtPdfOut,
                                // 字号
                                font_size: Number(store.txtFontSize) || 11,
                                // 标题
                                title: store.txtTitle
                            })}
                        >
                            {store.docBusy ? 'Converting…' : 'Start Conversion'}
                        </Btn>
                    </div>
                </Section>
            ) : null}

            {/* 转换结果卡片：有结果或错误时显示 */}
            {store.docResult.outputs.length || store.docResult.error || store.docResult.message ? (
                // 结果区块
                <Section title="Last Conversion Result">
                    {/* 错误信息 */}
                    {store.docResult.error ? <div className="hint">Error: {store.docResult.error}</div> : null}
                    {/* 附加说明 */}
                    {store.docResult.message ? <div className="hint">Note: {store.docResult.message}</div> : null}
                    {/* 产物列表 */}
                    {store.docResult.outputs.map((p) => (
                        // 单个产物行
                        <div className="kv" key={p}>
                            {/* 文件名 */}
                            <span className="k">{nameOf(p)}</span>
                            {/* 完整路径 */}
                            <span className="v" title={p}>{p}</span>
                        </div>
                    ))}
                    {/* 打开产物目录按钮 */}
                    {store.docResult.outputs.length ? (
                        // 按钮行
                        <div className="btn-row mt-8">
                            {/* 打开第一个产物所在目录 */}
                            <Btn size="sm" kind="blue" onClick={() => openPath(store.docResult.outputs[0])}>Open Output
                                Folder</Btn>
                        </div>
                    ) : null}
                </Section>
            ) : null}
        </div>
    )
}
