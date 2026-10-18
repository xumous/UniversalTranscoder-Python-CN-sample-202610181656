package com.xu.universaltranscoder.util;

// 引入 Context：用于访问 ContentResolver、缓存目录与应用专属外部目录。

import android.content.Context;
// 引入 ContentResolver：负责与 SAF(DocumentsProvider) 交互并打开文件流。
import android.content.ContentResolver;
// 引入 MediaMuxer：媒体封装器，需要文件路径或文件描述符来写入。
import android.media.MediaMuxer;
// 引入 DocumentsContract：提供 createDocument 等 SAF 文档操作 API。
import android.provider.DocumentsContract;
// 引入 Uri：表示 SAF 返回的 content:// 地址。
import android.net.Uri;
// 引入注解：标记返回值可为空，提示调用方做空值保护。
import androidx.annotation.Nullable;

// 引入 File：文件系统操作。
import java.io.File;
// 引入 FileOutputStream：写入应用专属目录或外部目录时使用。
import java.io.FileOutputStream;
// 引入 IOException：统一向上抛出 IO 异常，交由错误分类模块处理。
import java.io.IOException;
// 引入 OutputStream：图片与 PDF 等直接以流方式写出的目标。
import java.io.OutputStream;

/**
 * OutputTarget 抽象了“这次转换要把结果写到哪儿”。
 * <p>
 * 设计原因（为什么需要它）：
 * 1) 应用同时支持两种输出位置：应用专属外部目录（真实文件路径，MediaMuxer 可直接写入）与用户通过
 * SAF 选择的目录（content:// 形式，MediaMuxer 无法直接用路径写入）。
 * 2) 为了让上层转换器（视频/音频/图片/文档）不需要关心输出位置差异，这里统一提供
 * openStream()（用于 Bitmap/PdfDocument 等流式写出）与 openMuxer()（用于 MediaMuxer 封装）。
 * 3) 对 SAF 目录采用“先写缓存临时文件、成功后再复制到目标文档”的稳妥策略，避免部分设备
 * MediaMuxer 无法在不可 seek 的文件描述符上正确写出 moov/moov box 的问题。
 */
public final class OutputTarget {

    // 应用上下文：仅用于获取 ContentResolver 与缓存目录，不持有 Activity 引用以避免内存泄漏。
    private final Context appContext;
    // 输出位置类型，决定后续所有分支逻辑。
    private final Kind kind;
    // FILE_DIR 模式下的目标目录；SAVED_TREE 模式下为 null。
    private final File fileDir;
    // SAVED_TREE 模式下的树目录 URI（即 ACTION_OPEN_DOCUMENT_TREE 返回的地址）；FILE_DIR 模式下为 null。
    private final Uri treeUri;
    // 输出文件的基名（不含扩展名），例如 "video_20261017_120000"。
    private final String baseName;
    // 输出文件扩展名，不含点号，例如 "mp4"。
    private final String extension;
    // 输出文件的 MIME 类型，SAF 新建文档时必须提供，否则部分提供者会拒绝创建。
    private final String mimeType;
    // SAVED_TREE 模式下实际创建出来的文档 URI，延迟到真正写入时才创建。
    private Uri createdUri;
    // SAVED_TREE 模式下用于 MediaMuxer 的缓存临时文件。
    private File tempFile;
    // 标记当前这次 MediaMuxer 写入是否走了临时文件中转，决定 finalize 时是否需要复制。
    private boolean muxerViaTemp;
    // 私有构造：强制调用方使用下面的两个静态工厂方法，避免出现半初始化状态。
    private OutputTarget(Context appContext, Kind kind, @Nullable File fileDir, @Nullable Uri treeUri,
                         String baseName, String extension, String mimeType) {
        // 保存应用上下文引用。
        this.appContext = appContext;
        // 保存位置类型。
        this.kind = kind;
        // 保存真实目录（可能为 null）。
        this.fileDir = fileDir;
        // 保存树目录（可能为 null）。
        this.treeUri = treeUri;
        // 保存基名。
        this.baseName = baseName;
        // 保存扩展名。
        this.extension = extension;
        // 保存 MIME 类型。
        this.mimeType = mimeType;
    }

    /**
     * 创建一个“写入真实文件目录”的输出目标。
     * 用于默认输出位置（应用专属外部目录）以及任何已知可写路径的场景。
     */
    public static OutputTarget forFileDir(Context context, File dir, String baseName, String extension, String mimeType) {
        // 记录目录不存在时的创建结果，方便日志定位问题。
        if (dir != null && !dir.exists()) {
            // 主动创建目录：应用专属目录不需要任何权限即可创建。
            dir.mkdirs();
        }
        // 返回 FILE_DIR 类型的目标，treeUri 传 null。
        return new OutputTarget(context.getApplicationContext(), Kind.FILE_DIR, dir, null, baseName, extension, mimeType);
    }

    /**
     * 创建一个“写入用户选定的 SAF 目录”的输出目标。
     * treeUri 必须来自 ACTION_OPEN_DOCUMENT_TREE，并且在调用前已取得持久化读/写授权。
     */
    public static OutputTarget forTree(Context context, Uri treeUri, String baseName, String extension, String mimeType) {
        // 返回 SAVED_TREE 类型的目标，fileDir 传 null。
        return new OutputTarget(context.getApplicationContext(), Kind.SAVED_TREE, null, treeUri, baseName, extension, mimeType);
    }

    /**
     * 在同一输出位置派生一个新文件。
     * 主要用于“一源文件产生多个结果”的场景：PDF 逐页导出图片、视频抽取多帧。
     */
    public OutputTarget sibling(String newBaseName, String newExtension, String newMimeType) {
        // 复用当前的位置信息，仅替换文件命名与 MIME 类型。
        return new OutputTarget(appContext, kind, fileDir, treeUri, newBaseName, newExtension, newMimeType);
    }

    /**
     * 返回便于日志展示的目标路径描述。
     */
    public String displayPath() {
        // SAF 模式下展示树目录加上文件名，用户能够据此在文件管理器中定位。
        if (kind == Kind.SAVED_TREE) {
            // 拼接形如 content://…/tree/primary%3ADownload/video.mp4 的可读描述。
            return treeUri + "/" + fileName();
        }
        // FILE_DIR 模式下展示绝对路径，最直观。
        return new File(fileDir, fileName()).getAbsolutePath();
    }

    /**
     * 返回最终文件名（基名 + 点 + 扩展名）。
     */
    public String fileName() {
        // 扩展名为空时只返回基名，避免出现结尾多余的点号。
        if (extension == null || extension.isEmpty()) {
            // 直接返回基名。
            return baseName;
        }
        // 正常拼接基名与扩展名。
        return baseName + "." + extension;
    }

    /**
     * 打开一个直接的输出流，供图片压缩与 PDF 写出使用。
     * 这两类数据不需要 seek，直接写目标文档即可，无需临时文件中转。
     */
    public OutputStream openStream() throws IOException {
        // FILE_DIR 分支：直接写真实文件。
        if (kind == Kind.FILE_DIR) {
            // 确保目录存在，避免 FileNotFoundException。
            if (!fileDir.exists()) {
                // 尝试创建目录。
                fileDir.mkdirs();
            }
            // 返回文件输出流，覆盖同名文件（后续 fileName 已带秒级时间戳，重复概率极低）。
            return new FileOutputStream(new File(fileDir, fileName()));
        }
        // SAVED_TREE 分支：先在目标目录里新建文档，再打开其输出流。
        // 取出树目录对应的“目录文档 URI”，createDocument 要求传父目录文档 URI。
        Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        // 通过系统文档提供者创建文件；返回值为新文档的 URI，失败时为 null。
        Uri docUri = DocumentsContract.createDocument(appContext.getContentResolver(), parentDocUri, mimeType, fileName());
        // 创建失败时必须抛出可识别的异常，交由错误分类模块给出“权限/目标不可写”的提示。
        if (docUri == null) {
            // 抛出 IO 异常说明无法在所选目录中创建文件。
            throw new IOException("无法在所选目录中创建文档：" + displayPath());
        }
        // 记住创建出的 URI，便于失败时清理。
        createdUri = docUri;
        // 以 “wt” 模式打开输出流（截断写），保证重复运行不会残留旧内容。
        OutputStream stream = appContext.getContentResolver().openOutputStream(docUri, "wt");
        // 极端情况下 provider 不支持 “wt” 模式，这里再兜底一次普通写模式。
        if (stream == null) {
            // 使用默认模式打开输出流。
            stream = appContext.getContentResolver().openOutputStream(docUri);
        }
        // 仍然为 null 说明 provider 拒绝写，抛出异常交给分类模块。
        if (stream == null) {
            // 抛出异常，附带目标描述。
            throw new IOException("无法打开输出流：" + displayPath());
        }
        // 返回可用的输出流。
        return stream;
    }

    /**
     * 创建一个 MediaMuxer 用于封装音视频。
     * MediaMuxer 在写入结束后需要回填索引（moov/stss 等），因此要求输出可随机访问，
     * 对 SAF 目录一律先写缓存临时文件，成功后再整体复制到用户目录。
     */
    public MediaMuxer openMuxer(int format) throws IOException {
        // FILE_DIR 分支：直接以绝对路径构造 MediaMuxer，最稳。
        if (kind == Kind.FILE_DIR) {
            // 确保目录存在。
            if (!fileDir.exists()) {
                // 创建目录，避免构造失败。
                fileDir.mkdirs();
            }
            // 标记未使用临时文件。
            muxerViaTemp = false;
            // 返回基于真实路径的 muxer。
            return new MediaMuxer(new File(fileDir, fileName()).getAbsolutePath(), format);
        }
        // SAVED_TREE 分支：在缓存目录创建临时文件。
        tempFile = new File(appContext.getCacheDir(), "utc_mux_" + System.currentTimeMillis() + "_" + fileName());
        // 标记本次写入走临时文件中转。
        muxerViaTemp = true;
        // 返回基于临时文件路径的 muxer。
        return new MediaMuxer(tempFile.getAbsolutePath(), format);
    }

    /**
     * 在 muxer 已 stop() 并 release() 之后调用，把临时结果搬运到 SAF 目标文档。
     * 之所以要“先临时后复制”，是因为 ContentResolver 给出的 FileDescriptor 在不同设备/提供者上
     * 的可 seek 行为不一致，直接写容易产生损坏的 MP4。
     */
    public void finalizeOutput() throws IOException {
        // 只有在“临时文件中转”模式下才需要搬运。
        if (!muxerViaTemp) {
            // 直接写目标的情况无需任何收尾动作。
            return;
        }
        // 临时文件不存在说明写入过程中已失败并被清理，此时保持现状。
        if (tempFile == null || !tempFile.exists()) {
            // 无内容可搬运，直接返回。
            return;
        }
        // 打开 SAF 目标文档的输出流。
        OutputStream out = openStream();
        // 记录输入流以便在 finally 中关闭。
        java.io.InputStream in = new java.io.FileInputStream(tempFile);
        try {
            // 准备 64KB 缓冲区，兼顾内存占用与复制吞吐。
            byte[] buffer = new byte[64 * 1024];
            // 记录本次读取到的字节数。
            int read;
            // 循环把临时文件内容复制到目标文档。
            while ((read = in.read(buffer)) > 0) {
                // 写入读取到的这一段数据。
                out.write(buffer, 0, read);
            }
            // 强制刷出缓冲，确保数据落到存储。
            out.flush();
        } finally {
            // 关闭输入流，忽略关闭异常以避免掩盖真正的错误。
            try {
                // 关闭文件输入流。
                in.close();
            } catch (IOException ignored) {
                // 关闭失败不影响结果，这里有意忽略。
            }
            // 关闭输出流。
            try {
                // 关闭目标文档输出流。
                out.close();
            } catch (IOException ignored) {
                // 关闭失败不影响结果，这里有意忽略。
            }
        }
        // 复制完成后删除临时文件，避免占用缓存空间。
        tempFile.delete();
        // 清空临时文件引用。
        tempFile = null;
    }

    /**
     * 失败时清理已经创建出来的半成品文件。
     * 这样用户不会在输出目录里看到损坏的文件，也不会因为同名文件导致后续任务被覆盖。
     */
    public void cleanupOnFailure() {
        // FILE_DIR 模式下删除真实文件。
        if (kind == Kind.FILE_DIR) {
            // 构造目标文件对象。
            File target = new File(fileDir, fileName());
            // 存在才删除。
            if (target.exists()) {
                // 删除半成品。
                target.delete();
            }
            // FILE_DIR 分支处理结束。
            return;
        }
        // SAVED_TREE 模式下删除临时文件。
        if (tempFile != null && tempFile.exists()) {
            // 删除缓存临时文件。
            tempFile.delete();
        }
        // SAVED_TREE 模式下尽量删除已创建的目标文档，避免留下空文件。
        if (createdUri != null) {
            // 通过 DocumentsContract 删除文档；失败也不抛出，因为清理属于尽力而为。
            try {
                // 执行删除。
                DocumentsContract.deleteDocument(appContext.getContentResolver(), createdUri);
            } catch (Exception ignored) {
                // 清理失败不影响错误上报，这里有意忽略。
            }
        }
    }

    /**
     * 返回目标所在的用户可见目录描述，供界面与日志展示。
     */
    public String describeDirectory() {
        // SAF 模式展示树 URI。
        if (kind == Kind.SAVED_TREE) {
            // 返回树目录地址。
            return treeUri.toString();
        }
        // FILE_DIR 模式展示绝对目录。
        return fileDir.getAbsolutePath();
    }

    /**
     * 输出位置的两种类型：FILE_DIR 表示真实目录路径；SAVED_TREE 表示 SAF 树目录。
     */
    public enum Kind {
        // 真实文件目录：写入最可靠，MediaMuxer 直接使用绝对路径。
        FILE_DIR,
        // SAF 树目录：通过 DocumentsContract 新建文档，MediaMuxer 走临时文件中转。
        SAVED_TREE
    }
}
