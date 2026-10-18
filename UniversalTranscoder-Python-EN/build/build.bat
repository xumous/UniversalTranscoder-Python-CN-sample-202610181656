@echo off
REM ============================================================================
REM 英文版一键构建脚本（Windows 批处理）。
REM 作用：依次完成“建虚拟环境 → 装依赖 → 生成图标 → 构建前端 → 打包 exe”。
REM 说明：脚本对每一步都做结果检查，任一步失败立即停止并给出提示，
REM       避免“上一步失败但继续往下跑”产生一个看似成功实则残缺的产物。
REM 注意：本脚本的回显信息（echo）属面向使用者的可见文案，英文版统一为英文；
REM       注释（REM）保持中文，符合“注释不翻译”的约定。
REM ============================================================================

REM 切换到项目根目录（本脚本位于 build\ 子目录，因此上溯一级）。
cd /d "%~dp0.."

echo.
echo [1/6] Checking the Python virtual environment
if not exist ".venv\Scripts\python.exe" (
    echo     .venv not found, creating it...
    python -m venv .venv
    if errorlevel 1 (
        echo     Failed to create the virtual environment. Make sure Python 3.10+ is installed.
        exit /b 1
    )
)

echo.
echo [2/6] Installing / updating dependencies
".venv\Scripts\python.exe" -m pip install --upgrade pip
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 (
    echo     Dependency installation failed. Check your network or pip mirror.
    exit /b 1
)

echo.
echo [3/6] Generating the application icon
".venv\Scripts\python.exe" tools\make_icon.py
if errorlevel 1 (
    echo     Icon generation failed.
    exit /b 1
)

echo.
echo [4/6] Building the front end (Vue 3 + JSX)
if not exist "frontend\package.json" (
    echo     frontend folder not found, skipping the front-end build.
) else (
    pushd frontend
    if not exist "node_modules" (
        echo     Installing front-end dependencies...
        call npm install
    )
    echo     Running the front-end build...
    call npm run build
    if errorlevel 1 (
        echo     Front-end build failed. The web app will still run with the built-in fallback page.
    )
    popd
)

echo.
echo [5/6] Running self-check (core + format matrix)
".venv\Scripts\python.exe" tools\smoke_core.py
if errorlevel 1 (
    echo     Core self-check failed. Fix it before packaging.
    exit /b 1
)

echo.
echo [6/6] Packaging with PyInstaller
".venv\Scripts\python.exe" -m PyInstaller build\UniversalTranscoder.spec --noconfirm --clean
if errorlevel 1 (
    echo     Packaging failed.
    exit /b 1
)

echo.
echo ============================================================================
echo  Build complete. Output folder: dist\UniversalTranscoder\
echo  Copy that folder to any offline Windows machine and run it directly.
echo ============================================================================
exit /b 0
