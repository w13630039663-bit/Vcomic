<#
.SYNOPSIS
为 Windows 可执行文件注入 SegmentHeap manifest 配置，或从源 JDK 派生隔离的 SegmentHeap 开发启动器。
#>
param(
    [string]$ExePath = "",
    [switch]$PrepareJdkLauncher,
    [string]$SourceJdk = "",
    [string]$TargetJdk = ""
)

$ErrorActionPreference = "Stop"

$csharp = @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public static class SegmentHeapManifestTool {
    private delegate bool EnumResLangProc(IntPtr hModule, IntPtr lpszType, IntPtr lpszName, ushort wIDLanguage, IntPtr lParam);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr LoadLibraryExW(string lpFileName, IntPtr hFile, uint dwFlags);

    [DllImport("kernel32.dll")]
    private static extern bool FreeLibrary(IntPtr hModule);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr FindResourceExW(IntPtr hModule, IntPtr lpType, IntPtr lpName, ushort wLanguage);

    [DllImport("kernel32.dll")]
    private static extern IntPtr LoadResource(IntPtr hModule, IntPtr hResInfo);

    [DllImport("kernel32.dll")]
    private static extern IntPtr LockResource(IntPtr hResData);

    [DllImport("kernel32.dll")]
    private static extern uint SizeofResource(IntPtr hModule, IntPtr hResInfo);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool EnumResourceLanguagesW(IntPtr hModule, IntPtr lpType, IntPtr lpName, EnumResLangProc lpEnumFunc, IntPtr lParam);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr BeginUpdateResourceW(string pFileName, bool bDeleteExistingResources);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool UpdateResourceW(IntPtr hUpdate, IntPtr lpType, IntPtr lpName, ushort wLanguage, byte[] lpData, uint cb);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool EndUpdateResourceW(IntPtr hUpdate, bool fDiscard);

    private static string ReadManifest(string exePath, out ushort langId) {
        IntPtr hMod = LoadLibraryExW(exePath, IntPtr.Zero, 0x00000002);
        if (hMod == IntPtr.Zero) throw new Exception("LoadLibraryExW failed: " + Marshal.GetLastWin32Error());
        try {
            var langs = new List<ushort>();
            EnumResourceLanguagesW(hMod, (IntPtr)24, (IntPtr)1, (m, t, n, l, p) => {
                langs.Add(l);
                return false;
            }, IntPtr.Zero);
            langId = langs.Count > 0 ? langs[0] : (ushort)1033;
            IntPtr hRes = FindResourceExW(hMod, (IntPtr)24, (IntPtr)1, langId);
            if (hRes == IntPtr.Zero) return null;
            uint sz = SizeofResource(hMod, hRes);
            IntPtr ptr = LockResource(LoadResource(hMod, hRes));
            byte[] raw = new byte[sz];
            Marshal.Copy(ptr, raw, 0, (int)sz);
            return Encoding.UTF8.GetString(raw).TrimStart('\uFEFF');
        } finally {
            FreeLibrary(hMod);
        }
    }

    public static bool HasSegmentHeap(string exePath) {
        ushort lang;
        string xml = ReadManifest(exePath, out lang);
        return xml != null && xml.Contains("SegmentHeap");
    }

    public static string EnsureSegmentHeap(string exePath) {
        ushort lang;
        string xml = ReadManifest(exePath, out lang);
        if (xml == null) throw new Exception("Target exe has no RT_MANIFEST resource: " + exePath);
        if (xml.Contains("SegmentHeap")) return "already_present";

        const string heapTag = "<heapType xmlns=\"http://schemas.microsoft.com/SMI/2020/WindowsSettings\">SegmentHeap</heapType>";
        if (xml.Contains("</asmv3:windowsSettings>")) {
            xml = xml.Replace("</asmv3:windowsSettings>", heapTag + "</asmv3:windowsSettings>");
        } else if (xml.Contains("</windowsSettings>")) {
            xml = xml.Replace("</windowsSettings>", heapTag + "</windowsSettings>");
        } else if (xml.Contains("</assembly>")) {
            string block = "<application xmlns=\"urn:schemas-microsoft-com:asm.v3\"><windowsSettings>" + heapTag + "</windowsSettings></application>";
            xml = xml.Replace("</assembly>", block + "</assembly>");
        } else {
            throw new Exception("Unrecognized manifest XML structure in " + exePath);
        }

        byte[] outBytes = Encoding.UTF8.GetBytes(xml);
        IntPtr hUpd = BeginUpdateResourceW(exePath, false);
        if (hUpd == IntPtr.Zero) throw new Exception("BeginUpdateResourceW failed: " + Marshal.GetLastWin32Error());
        bool ok = false;
        try {
            if (!UpdateResourceW(hUpd, (IntPtr)24, (IntPtr)1, lang, outBytes, (uint)outBytes.Length))
                throw new Exception("UpdateResourceW failed: " + Marshal.GetLastWin32Error());
            if (!EndUpdateResourceW(hUpd, false))
                throw new Exception("EndUpdateResourceW failed: " + Marshal.GetLastWin32Error());
            ok = true;
        } finally {
            if (!ok) EndUpdateResourceW(hUpd, true);
        }

        if (!HasSegmentHeap(exePath)) throw new Exception("Verification failed after updating manifest: " + exePath);
        return "patched";
    }
}
"@

Add-Type -TypeDefinition $csharp

if ($PrepareJdkLauncher) {
    if (-not (Test-Path "$SourceJdk\bin\java.exe")) {
        throw "Source JDK java.exe not found: $SourceJdk"
    }
    $targetExe = Join-Path $TargetJdk "bin\java.exe"
    $stampFile = Join-Path $TargetJdk ".source-jdk-stamp"
    $needSync = $true
    if ((Test-Path $targetExe) -and (Test-Path $stampFile)) {
        $prevStamp = (Get-Content $stampFile -Raw).Trim()
        if ($prevStamp -eq $SourceJdk -and [SegmentHeapManifestTool]::HasSegmentHeap($targetExe)) {
            $needSync = $false
        }
    }
    if ($needSync) {
        New-Item -ItemType Directory -Force (Join-Path $TargetJdk "bin") | Out-Null
        Copy-Item "$SourceJdk\bin\*.dll" (Join-Path $TargetJdk "bin") -Force
        Copy-Item "$SourceJdk\bin\java.exe" $targetExe -Force
        if (Test-Path "$SourceJdk\bin\server") {
            Copy-Item "$SourceJdk\bin\server" (Join-Path $TargetJdk "bin\server") -Recurse -Force
        }
        foreach ($sub in @("lib", "conf")) {
            $srcSub = Join-Path $SourceJdk $sub
            $dstSub = Join-Path $TargetJdk $sub
            if ((Test-Path $srcSub) -and -not (Test-Path $dstSub)) {
                cmd /c mklink /J "`"$dstSub`"" "`"$srcSub`"" | Out-Null
            }
        }
        $status = [SegmentHeapManifestTool]::EnsureSegmentHeap($targetExe)
        Set-Content -Path $stampFile -Value $SourceJdk -Encoding UTF8
        Write-Output "D_SegmentHeap launcher=$status path=$targetExe"
    } else {
        Write-Output "D_SegmentHeap launcher=cached path=$targetExe"
    }
} elseif ($ExePath -ne "") {
    if (-not (Test-Path $ExePath)) {
        throw "Target exe not found: $ExePath"
    }
    $status = [SegmentHeapManifestTool]::EnsureSegmentHeap($ExePath)
    Write-Output "D_SegmentHeap exe=$status path=$ExePath"
} else {
    throw "Must specify -PrepareJdkLauncher or -ExePath"
}
