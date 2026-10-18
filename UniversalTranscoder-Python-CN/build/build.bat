@echo off
REM ============================================================================
REM 中文版一键构建脚本（Windows 批处理）。
REM 作用：依次完成“建虚拟环境 → 装依赖 → 生成图标 → 构建前端 → 打包 exe”。
REM 说明：脚本对每一步都做结果检查，任一步失败立即停止并给出提示，
REM       避免“上一步失败但继续往下跑”产生一个看似成功实则残缺的产物。
REM ============================================================================

REM 切换到项目根目录（本脚本位于 build\ 子目录，因此上溯一级）。
cd /d "%~dp0.."

echo.
echo [1/6] 检查 Python 虚拟环境
if not exist ".venv\Scripts\python.exe" (
    echo     未找到 .venv，正在创建...
    python -m venv .venv
    if errorlevel 1 (
        echo     创建虚拟环境失败，请确认已安装 Python 3.10 以上版本。
        exit /b 1
    )
)

echo.
echo [2/6] 安装/更新依赖
".venv\Scripts\python.exe" -m pip install --upgrade pip
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 (
    echo     依赖安装失败，请检查网络或 pip 源。
    exit /b 1
)

echo.
echo [3/6] 生成应用图标
".venv\Scripts\python.exe" tools\make_icon.py
if errorlevel 1 (
    echo     图标生成失败。
    exit /b 1
)

echo.
echo [4/6] 构建前端（Vue 3 + JSX）
if not exist "frontend\package.json" (
    echo     未找到 frontend 目录，跳过前端构建（Web 端将显示内置提示页）。
) else (
    pushd frontend
    if not exist "node_modules" (
        echo     安装前端依赖...
        call npm install
    )
    echo     执行前端构建...
    call npm run build
    if errorlevel 1 (
        echo     前端构建失败（Web 端仍可用，但界面将回退为内置提示页）。
    )
    popd
)

echo.
echo [5/6] 运行自检（内核 + 格式矩阵）
".venv\Scripts\python.exe" tools\smoke_core.py
if errorlevel 1 (
    echo     内核自检未通过，请先修复再打包。
    exit /b 1
)

echo.
echo [6/6] PyInstaller 打包
".venv\Scripts\python.exe" -m PyInstaller build\UniversalTranscoder.spec --noconfirm --clean
if errorlevel 1 (
    echo     打包失败。
    exit /b 1
)

echo.
echo ============================================================================
echo  构建完成！产物位于：dist\万能转码器\
echo  该目录可直接拷贝到其他离线 Windows 机器运行。
echo ============================================================================
exit /b 0
