param(
    [Parameter(Mandatory = $true)][string]$ExePath,
    [Parameter(Mandatory = $true)][string]$IcoPath
)

$ErrorActionPreference = 'Stop'

$code = @"
using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.InteropServices;

public static class Win32IconPatcher {
    private const uint RT_ICON = 3;
    private const uint RT_GROUP_ICON = 14;

    private delegate bool EnumResNameProc(IntPtr hModule, IntPtr lpszType, IntPtr lpszName, IntPtr lParam);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern IntPtr LoadLibraryExW(string lpFileName, IntPtr hFile, uint dwFlags);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool FreeLibrary(IntPtr hModule);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern bool EnumResourceNamesW(IntPtr hModule, IntPtr lpszType, EnumResNameProc lpEnumFunc, IntPtr lParam);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern IntPtr BeginUpdateResourceW(string pFileName, bool bDeleteExistingResources);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern bool UpdateResourceW(IntPtr hUpdate, IntPtr lpType, IntPtr lpName, ushort wLanguage, byte[] lpData, uint cbData);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool EndUpdateResourceW(IntPtr hUpdate, bool fDiscard);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool EnumResourceLanguagesW(IntPtr hModule, IntPtr lpType, IntPtr lpName, EnumResLangProc lpEnumFunc, IntPtr lParam);

    private delegate bool EnumResLangProc(IntPtr hModule, IntPtr lpszType, IntPtr lpszName, ushort wIDLanguage, IntPtr lParam);

    private static bool IsIntResource(IntPtr ptr) {
        return ((ulong)ptr.ToInt64() >> 16) == 0;
    }

    public static void ReplaceIcon(string exePath, string icoPath) {
        byte[] icoBytes = File.ReadAllBytes(icoPath);
        if (icoBytes.Length < 6) throw new InvalidDataException("Invalid ICO file.");
        ushort count = BitConverter.ToUInt16(icoBytes, 4);
        if (count == 0) throw new InvalidDataException("Empty ICO file.");

        var groupNames = new List<IntPtr>();
        var iconNames = new List<IntPtr>();
        ushort targetLang = 0;

        IntPtr hMod = LoadLibraryExW(exePath, IntPtr.Zero, 0x00000002 /* LOAD_LIBRARY_AS_DATAFILE */);
        if (hMod != IntPtr.Zero) {
            try {
                EnumResourceNamesW(hMod, (IntPtr)RT_GROUP_ICON, (m, t, name, lp) => {
                    groupNames.Add(name);
                    if (targetLang == 0) {
                        EnumResourceLanguagesW(m, t, name, (m2, t2, n2, lang, lp2) => {
                            targetLang = lang;
                            return false;
                        }, IntPtr.Zero);
                    }
                    return true;
                }, IntPtr.Zero);
                EnumResourceNamesW(hMod, (IntPtr)RT_ICON, (m, t, name, lp) => {
                    iconNames.Add(name);
                    return true;
                }, IntPtr.Zero);
            } finally {
                FreeLibrary(hMod);
            }
        }

        IntPtr hUpdate = BeginUpdateResourceW(exePath, false);
        if (hUpdate == IntPtr.Zero) throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());

        try {
            // 清除原有的 RT_GROUP_ICON 与 RT_ICON
            foreach (var g in groupNames) {
                UpdateResourceW(hUpdate, (IntPtr)RT_GROUP_ICON, g, targetLang, null, 0);
            }
            foreach (var ic in iconNames) {
                UpdateResourceW(hUpdate, (IntPtr)RT_ICON, ic, targetLang, null, 0);
            }

            // 构造 GRPICONDIR (6 bytes header + 14 bytes * count)
            using (var ms = new MemoryStream())
            using (var bw = new BinaryWriter(ms)) {
                bw.Write((ushort)0);
                bw.Write((ushort)1);
                bw.Write(count);

                for (int i = 0; i < count; i++) {
                    int entryOffset = 6 + i * 16;
                    byte width = icoBytes[entryOffset];
                    byte height = icoBytes[entryOffset + 1];
                    byte colorCount = icoBytes[entryOffset + 2];
                    byte reserved = icoBytes[entryOffset + 3];
                    ushort planes = BitConverter.ToUInt16(icoBytes, entryOffset + 4);
                    ushort bitCount = BitConverter.ToUInt16(icoBytes, entryOffset + 6);
                    uint bytesInRes = BitConverter.ToUInt32(icoBytes, entryOffset + 8);
                    uint imageOffset = BitConverter.ToUInt32(icoBytes, entryOffset + 12);

                    ushort iconId = (ushort)(i + 1);
                    byte[] iconData = new byte[bytesInRes];
                    Buffer.BlockCopy(icoBytes, (int)imageOffset, iconData, 0, (int)bytesInRes);
                    if (!UpdateResourceW(hUpdate, (IntPtr)RT_ICON, (IntPtr)iconId, 0, iconData, bytesInRes)) {
                        throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
                    }

                    bw.Write(width);
                    bw.Write(height);
                    bw.Write(colorCount);
                    bw.Write(reserved);
                    bw.Write(planes);
                    bw.Write(bitCount);
                    bw.Write(bytesInRes);
                    bw.Write(iconId);
                }

                byte[] grpData = ms.ToArray();
                IntPtr targetGroupId = groupNames.Count > 0 ? groupNames[0] : (IntPtr)1;
                if (!UpdateResourceW(hUpdate, (IntPtr)RT_GROUP_ICON, targetGroupId, 0, grpData, (uint)grpData.Length)) {
                    throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
                }
            }
            if (!EndUpdateResourceW(hUpdate, false)) {
                throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
            }
        } catch {
            EndUpdateResourceW(hUpdate, true);
            throw;
        }
    }
}
"@

Add-Type -TypeDefinition $code -Language CSharp
[Win32IconPatcher]::ReplaceIcon((Resolve-Path $ExePath).Path, (Resolve-Path $IcoPath).Path)
