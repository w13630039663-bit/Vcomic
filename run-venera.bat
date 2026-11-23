@echo off
setlocal EnableExtensions
rem ===========================================================================
rem  venera-desktop launcher / evidence runner   (branch codex/desktop)
rem
rem  Encoding: this file is UTF-8 WITHOUT a BOM, and line endings must stay
rem  CRLF (cmd.exe mis-parses LF-only batch files). It does carry Chinese
rem  because the menu names the on-screen labels verbatim - 阅读历史 / 离线下载 /
rem  夹:X are what you have to look for, so translating them would break the
rem  match. Measured on this machine (2026-10-22): with the chcp 65001 below
rem  those characters print correctly, and the Chinese "D_" readings the app
rem  writes are already UTF-8 (the :app task sets -Dstdout.encoding=UTF-8,
rem  which is the reason those readings exist at all - a GBK stdout on a Chinese
rem  Windows turns every one of them into U+FFFD).
rem  If a machine's console font cannot draw CJK, drop the chcp line and replace
rem  the labels with ASCII transliterations - do not just delete the chcp.
rem
rem  Line endings must stay CRLF - cmd.exe mis-parses LF-only batch files.
rem  Nothing here touches desktop/build.gradle.kts: that file is a declared
rem  input of :desktop:test, so editing it invalidates the full regression.
rem ===========================================================================

chcp 65001 >nul
cd /d "%~dp0"
title venera-desktop runner

set "PROXYARG="
if defined VENERA_PROXY set "PROXYARG=-Pproxy=%VENERA_PROXY%"

set "HASNODE="
where node >nul 2>nul && set "HASNODE=1"

set "GRADLEW=%~dp0gradlew.bat"
if not exist "%GRADLEW%" (
    echo    FATAL: gradlew.bat not found next to this bat: %~dp0
    pause
    exit /b 1
)
:menu
echo.
echo ================================================================
echo  venera-desktop   comic line: batch 140 landed
echo  last measured full run: 1600 tests / 229 classes / 0 failed
echo ================================================================
echo.
echo   [1] open the app window                 - clickable, comic domain
echo   [2] run the batch-121..123 rules        - ~6s
echo   [3] fast regression, hammer off         - ~75s   NOT the full run
echo   [4] FULL regression                     - ~2min  (once a day)
echo   [5] open the app AND save a screenshot  - auto-exits
echo   [6] run one ruler by class name
echo   [7] show the flags this launcher can pass
echo   [8] preview download queue template  - direct page
echo   [9] preview downloaded files template - direct page
echo  [10] preview local comics template     - direct page
echo  [11] preview reading history            - direct page
echo  [12] preview favorites                - direct tab
echo  [13] preview reading stats            - direct page
echo  [14] preview backup and restore       - direct page
echo  [15] preview gallery browse history   - direct page
echo  [16] preview gallery tag search       - direct page
echo  [17] preview source manage            - direct page
echo  [18] preview settings hub             - direct page
echo   [0] quit
echo.
set "choice="
set /p choice=  pick a number:
rem An empty choice means stdin is not a console (piped or redirected input).
rem Without this guard the menu would print forever without ever reading a key.
if not defined choice (
    if not "%~1"=="" set "choice=%~1"
)
if not defined choice goto bye

if "%choice%"=="1" goto app
if "%choice%"=="2" goto b123
if "%choice%"=="3" goto fast
if "%choice%"=="4" goto full
if "%choice%"=="5" goto shot
if "%choice%"=="6" goto single
if "%choice%"=="7" goto flags
if "%choice%"=="8" (set "PREVIEW=QUEUE"&goto preview)
if "%choice%"=="9" (set "PREVIEW=DOWNLOADS"&goto preview)
if "%choice%"=="10" (set "PREVIEW=LOCALCOMICS"&goto preview)
if "%choice%"=="11" (set "PREVIEW=HISTORY"&goto preview)
if "%choice%"=="12" (set "PREVIEW=FAVORITES"&goto preview)
if "%choice%"=="13" (set "PREVIEW=READINGSTATS"&goto preview)
if "%choice%"=="14" (set "PREVIEW=BACKUP"&goto preview)
if "%choice%"=="15" (set "PREVIEW=GALLERYHISTORY"&goto preview)
if "%choice%"=="16" (set "PREVIEW=GALLERYSEARCH"&goto preview)
if "%choice%"=="17" (set "PREVIEW=SOURCES"&goto preview)
if "%choice%"=="18" (set "PREVIEW=SETTINGS"&goto preview)
if "%choice%"=="0" goto bye
echo    not a known number - pick again
goto menu

:app
echo.
echo  launching :desktop:app  -PstartDomain=COMIC %PROXYARG%
echo  a cold gradle cache can make the first start take a while.
echo  close the window when done; the readings stay in this box.
call "%GRADLEW%" --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" :desktop:app -PstartDomain=COMIC %PROXYARG%
call "%GRADLEW%" --stop >nul 2>nul
echo.
echo  window closed. what to look for on that screen:
echo    explore card, bottom-right corner .... batch 118 offline badge
echo    explore top bar, "阅读历史" entry ...... batch 119 door + 4-state gate
echo    detail screen, the big pill ........... batch 120 resume reading
echo    detail screen, "离线下载" dialog ...... batch 117 bulk download
echo    detail screen, folder row ............. batch 121 favorite folders
echo    favorites screen, "夹:X" line ......... batch 121 folder display
echo    favorites top row, folder tabs ........ batch 122 filter by folder
echo    comic search, "最近搜索" row ......... batch 123 history chips + footer paging tiers
goto after

:preview
echo.
echo  launching :desktop:app  -Popen=%PREVIEW%
rem Unified preview entry: the page name comes from %PREVIEW% set by the dispatch above.
call "%GRADLEW%" --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" :desktop:app -PstartDomain=COMIC -Popen=%PREVIEW% %PROXYARG%
call "%GRADLEW%" --stop >nul 2>nul
echo.
echo  window closed; gradle daemon stopped.
goto after

:b123
echo.
call "%GRADLEW%" :desktop:test --tests "com.venera.desktop.gallery.data.DesktopFavoriteFoldersTest" --tests "com.venera.desktop.screens.DesktopFavoriteFoldersUiTest" --tests "com.venera.desktop.db.DesktopFavoriteMoveFolderTest" --tests "com.venera.desktop.search.*" --tests "com.venera.desktop.screens.DesktopComicSearchUiTest" %PROXYARG%
echo.
echo    exit code = %ERRORLEVEL%   (54 cases: folders 17 pure + 11 headless UI + 3 real sqlite,
goto after_count

:fast
echo.
echo  -PskipEngineProbe drops DesktopEngineCallConcurrencyTest, so this run is
echo  NOT the full one. The build prints a D_跑法警告 line for exactly that.
call "%GRADLEW%" -PskipEngineProbe :desktop:cleanTest :desktop:test
goto after_count

:full
echo.
call "%GRADLEW%" :desktop:cleanTest :desktop:test
goto after_count

:shot
echo.
echo  -Pshot makes the app grab its own window after a short settle, write the
echo  file, then exit. It raises the window on purpose - that is the evidence path.
if not exist screenshots mkdir screenshots
call "%GRADLEW%" --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" :desktop:app -PstartDomain=COMIC -Pshot=screenshots\menu-comic.png %PROXYARG%
call "%GRADLEW%" --stop >nul 2>nul
echo.
rem Exit code 0 does NOT prove the shot landed: selfShot prints "无效" and the
rem app still exits 0 (VeneraDesktop.kt:705-710). Judge the file, not ERRORLEVEL.
if not exist screenshots\menu-comic.png goto shotmissing
echo    OK  screenshots\menu-comic.png exists - the shot really landed.
goto shotdone
:shotmissing
echo    NO FILE written. Read the D_截图 line above: "无效" means another
echo    window was over the centre point when it fired - that check exists
echo    on purpose, a stale frame used to produce wallpaper shots while
echo    logging "截图成功". Close or move that window and run this again.
:shotdone
goto after

:single
echo.
echo  example: com.venera.desktop.screens.DesktopFavoriteFoldersUiTest
set "ruler="
set /p ruler=  class name:
rem On a piped or shortcut run stdin gives nothing, so the class name comes from arg 2.
if not defined ruler if not "%~2"=="" set "ruler=%~2"
if not defined ruler goto menu
call "%GRADLEW%" :desktop:test --tests "%ruler%"
goto after_count

:flags
echo.
echo  environment (set before the menu, or edit the lines above):
echo    set VENERA_PROXY=127.0.0.1:7890   passes -Pproxy= to the app
echo.
echo  gradle properties understood by task :app, all optional:
echo    -Pkey=^<source key^>              start source, default jm
echo    -PstartDomain=COMIC               rail domain the window opens on:
echo                                    GALLERY DISCOVER SEARCH FAVORITES ARTISTS COMIC SETTINGS
echo    -Pcomic=^<id^>[/^<title^>]         jump straight into a detail screen
echo    -Pread=^<id^>/^<ch^>[/^<name^>]     jump straight into the reader
echo    -PreadPage=^<n^>                  reader start page, 0 based
echo    -Pdownload=^<id^>/^<ch^>           write one whole episode to disk
echo    -Prepair=^<id^>/^<ch^>             queue a repair of a local episode
echo    -Pdelete=^<id^>/^<ch^>             delete a local episode
echo    -Pshot=^<path.png^>               take a screenshot and exit
echo    -Popen=QUEUE^|DOWNLOADS^|LOCALCOMICS^|HISTORY^|FAVORITES^|READINGSTATS^|BACKUP^|GALLERYHISTORY^|GALLERYSEARCH^|SOURCES^|SETTINGS  open a page directly
echo  more seeds: desktop/build.gradle.kts task "app", and ForensicFlags.kt
goto after

:after
echo.
:argpause
rem With an argument this is a one-shot run: pausing then re-reading a closed stdin
rem would fall back to the same argument and run this option forever.
if not "%~1"=="" goto bye
pause
goto menu

:after_count
echo.
rem Only the test paths land here. A targeted :desktop:test rewrites
rem desktop/build/test-results/test with just those classes, so the number
rem below is "this run" and never the 1299-case total. Say so out loud rather
rem than letting a partial count read like a regression report.
if defined HASNODE node _tools\count_desktop_tests.js
echo    ^ those XML files are what the LAST :desktop:test wrote. Options 2 and 6
echo    report a partial count by design; only 3 and 4 rewrite the whole tree,
echo    and 3 (hammer off) is not the full run either.
echo.
:argpause2
if not "%~1"=="" goto bye
pause
goto menu

:bye
endlocal
