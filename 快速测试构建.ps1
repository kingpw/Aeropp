param(
    [string]$InstanceRoot = 'C:\software\PCL2\.minecraft\versions\航空学\.minecraft\versions\Aeropp',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$modsDir = Join-Path $InstanceRoot 'mods'
$env:JAVA_HOME = 'C:\Users\KunYu\AppData\Roaming\.minecraft\runtime\java-runtime-delta'

$modules = @(
    @{ Project = 'new-mod\building-test-mod'; Output = 'build\libs\aeropp_buildtest-0.1.0.jar'; Installed = 'test-aeropp_buildtest-0.1.0.jar' },
    @{ Project = 'new-mod\mob-test-mod'; Output = 'build\libs\aeropp_mobtest-0.1.0.jar'; Installed = 'test-aeropp_mobtest-0.1.0.jar' },
    @{ Project = 'new-mod\testmod'; Output = 'build\libs\testmod-1.0.0.jar'; Installed = 'test-testmod-1.0.0.jar' }
)

if (-not (Test-Path -LiteralPath $modsDir -PathType Container)) {
    throw "PCL2 实例 mods 目录不存在：$modsDir"
}
foreach ($module in $modules) {
    $projectDir = Join-Path $repoRoot $module.Project
    $outputPath = Join-Path $projectDir $module.Output
    if (-not $SkipBuild) {
        Push-Location $projectDir
        try {
            & .\gradlew.bat build
            if ($LASTEXITCODE -ne 0) {
                throw "构建失败：$projectDir"
            }
        } finally {
            Pop-Location
        }
    }
    if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) {
        throw "构建产物不存在：$outputPath"
    }

    $installedPath = Join-Path $modsDir $module.Installed
    if (Test-Path -LiteralPath $installedPath -PathType Leaf) {
        # 原位写入，保持 NTFS 硬链接节点不被 Gradle/COPY 替换。
        $source = [IO.File]::OpenRead($outputPath)
        try {
            $dest = [IO.File]::Open($installedPath, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::Read)
            try {
                $source.CopyTo($dest)
            } finally {
                $dest.Dispose()
            }
        } finally {
            $source.Dispose()
        }
    } else {
        New-Item -ItemType HardLink -Path $installedPath -Target $outputPath | Out-Null
    }

    $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $outputPath).Hash
    $installedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $installedPath).Hash
    if ($sourceHash -ne $installedHash) {
        throw "同步校验失败：$installedPath"
    }
    Write-Host ("已同步 {0} -> {1} ({2})" -f $module.Project, $installedPath, $sourceHash)
}
