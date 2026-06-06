param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = "Stop"

$archivePath = Resolve-Path -LiteralPath $Path
Add-Type -AssemblyName System.IO.Compression.FileSystem

try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($archivePath)
} catch {
    throw "ZIP поврежден или не был полностью скачан: $($_.Exception.Message)"
}

try {
    $manifest = $zip.Entries | Where-Object { $_.FullName -eq "manifest.json" } | Select-Object -First 1
    if (-not $manifest) {
        throw "В архиве нет manifest.json"
    }

    $buffer = New-Object byte[] 65536
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.EndsWith("/")) {
            continue
        }
        try {
            $stream = $entry.Open()
            try {
                while ($stream.Read($buffer, 0, $buffer.Length) -gt 0) {
                }
            } finally {
                $stream.Dispose()
            }
        } catch {
            throw "Ошибка в данных: $($entry.FullName). $($_.Exception.Message)"
        }
    }

    Write-Host "OK: архив целый. Файлов: $($zip.Entries.Count), размер: $((Get-Item -LiteralPath $archivePath).Length) байт."
} finally {
    $zip.Dispose()
}
