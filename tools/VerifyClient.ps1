param(
    [ValidateSet('standalone', 'integrations')][string]$Mode = 'standalone',
    [string]$LauncherDirectory = $env:TROPIMON_HOME,
    [string]$InstanceDirectory,
    [string]$CobblemonJar,
    [ValidateRange(960, 3840)][int]$Width = 1400,
    [ValidateRange(600, 2160)][int]$Height = 900,
    [ValidateSet('fr_fr', 'en_us')][string]$Language = 'fr_fr',
    [switch]$HabitatAudit,
    [ValidateRange(1, 4)][int]$GuiScale = 2
)
# Import the engine's built-in modules explicitly, including when launched by a build daemon.
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
Import-Module (Join-Path $PSHOME 'Modules/CimCmdlets/CimCmdlets.psd1') -ErrorAction Stop
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (!$LauncherDirectory) { $LauncherDirectory = Join-Path $env:APPDATA '.tropimon' }
$launcher = $LauncherDirectory
if (!$InstanceDirectory) {
    $profiles = Join-Path $launcher 'profiles'
    if (Test-Path -LiteralPath $profiles -PathType Container) {
        $instances = @(Get-ChildItem -LiteralPath $profiles -Directory | ForEach-Object {
            $candidate = Join-Path $_.FullName 'instance'
            if (Test-Path -LiteralPath (Join-Path $candidate 'mods') -PathType Container) { $candidate }
        })
        if ($instances.Count -ne 1) { throw 'Profil ambigu : fournir -InstanceDirectory.' }
        $InstanceDirectory = $instances[0]
    } else { $InstanceDirectory = $launcher }
}
$run = Join-Path $project "build/verify-$Mode-gui$GuiScale-$Width-$Height"
if ($HabitatAudit) { $run = Join-Path $project 'build/verify-habitat-audit' }
if (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains($run) }) {
    throw 'Close this isolated test instance before replacing its test JAR.'
}
$mods = Join-Path $run 'mods'
New-Item -ItemType Directory -Force $mods | Out-Null
$modVersion = ((Get-Content (Join-Path $project 'gradle.properties') | Select-String '^mod_version=').Line -split '=', 2)[1]
$artifact = Join-Path $project "build/libs/tropimon-farm-$modVersion.jar"
if (!(Test-Path -LiteralPath $artifact)) { throw 'Build the release JAR first.' }
Get-ChildItem -LiteralPath $mods -Filter 'tropimon-farm-*.jar' -File |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName }
Copy-Item -LiteralPath $artifact -Destination $mods
Copy-Item -LiteralPath (Join-Path $project "build/smoke-helper/tropimon-farm-$modVersion-smoke.jar") -Destination $mods
Add-Type -AssemblyName System.IO.Compression.FileSystem
$dependencies = @{}
foreach ($jar in (Get-ChildItem -LiteralPath (Join-Path $InstanceDirectory 'mods') -Filter '*.jar' -File)) {
    $archive = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        $entry = $archive.GetEntry('fabric.mod.json')
        if (!$entry) { continue }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($metadata.id -in @('cobblemon', 'fabric-api', 'fabric-language-kotlin')) {
            if ($dependencies.ContainsKey($metadata.id)) { throw 'Dépendance active en double.' }
            $dependencies[$metadata.id] = $jar.FullName
        }
    } finally { $archive.Dispose() }
}
if ($CobblemonJar) { $dependencies['cobblemon'] = (Get-Item -LiteralPath $CobblemonJar).FullName }
foreach ($id in @('cobblemon', 'fabric-api', 'fabric-language-kotlin')) {
    if (!$dependencies.ContainsKey($id)) { throw "Dépendance active absente : $id" }
    Copy-Item -LiteralPath $dependencies[$id] -Destination (Join-Path $mods ($id + '-active.jar'))
}
$patterns = @()
if ($Mode -eq 'integrations') { $patterns += @('TropimodClient-*.jar', 'TropimonBuild-*.jar', '*xaero*.jar') }
foreach ($pattern in $patterns) {
    Get-ChildItem (Join-Path $InstanceDirectory 'mods') -Filter $pattern |
        Where-Object { $_.Name -notlike '*BetterPC*' } |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $mods }
}
$version = Get-Content (Join-Path $launcher '1.21.1.json') -Raw | ConvertFrom-Json
$loader = Get-Content (Join-Path $launcher 'fabric-loader-0.17.3-1.21.1.json') -Raw | ConvertFrom-Json
$classpath = [Collections.Generic.List[string]]::new()
foreach ($library in @($loader.libraries) + @($version.libraries)) {
    $allowed = !$library.rules
    foreach ($rule in $library.rules) {
        if (!$rule.os -or (!$rule.os.name -or $rule.os.name -eq 'windows')) { $allowed = $rule.action -eq 'allow' }
    }
    if (!$allowed) { continue }
    $relative = $library.downloads.artifact.path
    if (!$relative) {
        $parts = $library.name.Split(':')
        $relative = $parts[0].Replace('.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' + $parts[1] + '-' + $parts[2] + '.jar'
    }
    $path = Join-Path (Join-Path $launcher 'libraries') $relative
    if (!(Test-Path -LiteralPath $path)) { throw "Missing library: $relative" }
    $classpath.Add($path)
}
$classpath.Add((Join-Path $launcher 'client.jar'))
$java = Join-Path $launcher 'runtime/x64/jdk-21.0.6+7/bin/java.exe'
$optionsPath = Join-Path $run 'options.txt'
$optionsLines = if (Test-Path -LiteralPath $optionsPath) { @(Get-Content -LiteralPath $optionsPath | Where-Object { $_ -notmatch '^lang:' }) } else { @() }
[IO.File]::WriteAllLines($optionsPath, [string[]]($optionsLines + "lang:$Language"), [Text.UTF8Encoding]::new($false))
$arguments = @('-Xmx3G', '-Dtropimon.smoke=true', "-Dtropimon.smoke.guiScale=$GuiScale", '-Dfabric.debug.disableErrorGui=true', "-Dtropimon.smoke.language=$Language", '-Dfabric.log.disableAnsi=true',
    "-Djava.library.path=$(Join-Path $launcher 'natives')",
    '-cp', ($classpath -join ';'), $loader.mainClass,
    '--username', 'InstrumentTest', '--uuid', '00000000000000000000000000000001',
    '--accessToken', '0', '--version', '1.21.1', '--userType', 'legacy',
    '--gameDir', $run, '--assetsDir', (Join-Path $launcher 'assets'),
    '--assetIndex', $version.assetIndex.id, '--width', $Width.ToString(), '--height', $Height.ToString())
if ($HabitatAudit) { $arguments = @('-Dtropimon.habitatAudit=true') + $arguments }
Push-Location $run
try { & $java @arguments } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (-not (Select-String -LiteralPath (Join-Path $run "logs/latest.log") -SimpleMatch "TROPIMON_SMOKE_OK" -Quiet)) { throw "Verification incompletement validee : consulter le journal local." }
if ($HabitatAudit -and -not (Select-String -LiteralPath (Join-Path $run 'logs/latest.log') -SimpleMatch 'HABITAT_AUDIT COMPLETE' -Quiet)) {
    throw 'Habitat audit incomplete: inspect the isolated log.'
}
