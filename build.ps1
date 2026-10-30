param(
	[string]$JdkBin = 'C:\L2_Server\tools\zulu21fx\zulu21.52.203-ca-fx-jdk21.0.12.1-win_x64\bin'
)

$ErrorActionPreference = 'Stop'

$project = $PSScriptRoot
$jdkBin = $JdkBin
$source = Join-Path $project 'MileniaLauncher.java'
if (-not (Test-Path -LiteralPath $source -PathType Leaf))
{
	$source = Join-Path $project 'src\MileniaLauncher.java'
}
$build = Join-Path $project 'build'
$classes = Join-Path $build 'classes'
$input = Join-Path $build 'input'
$imageRoot = Join-Path $build 'image'
$image = Join-Path $imageRoot 'MileniaLauncher'
$outputDirectory = Join-Path $project 'dist'
$launcherZip = Join-Path $outputDirectory 'MileniaLauncher.zip'

foreach ($tool in @('javac.exe', 'jar.exe', 'jpackage.exe'))
{
	if (-not (Test-Path -LiteralPath (Join-Path $jdkBin $tool) -PathType Leaf))
	{
		throw "JDK tool not found: $(Join-Path $jdkBin $tool)"
	}
}
if (-not (Test-Path -LiteralPath $source -PathType Leaf))
{
	throw "Launcher source file not found: $source"
}

New-Item -ItemType Directory -Path $classes,$input,$imageRoot,$outputDirectory -Force | Out-Null
& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $classes $source
if ($LASTEXITCODE -ne 0)
{
	throw "Launcher compilation failed with exit code $LASTEXITCODE."
}

$jar = Join-Path $input 'MileniaLauncher.jar'
& (Join-Path $jdkBin 'jar.exe') --create --file $jar --main-class MileniaLauncher -C $classes .
if ($LASTEXITCODE -ne 0)
{
	throw "Launcher JAR creation failed with exit code $LASTEXITCODE."
}

if (Test-Path -LiteralPath $imageRoot)
{
	Get-ChildItem -LiteralPath $imageRoot -Force | Remove-Item -Recurse -Force
}
& (Join-Path $jdkBin 'jpackage.exe') `
	--type app-image `
	--name MileniaLauncher `
	--input $input `
	--main-jar 'MileniaLauncher.jar' `
	--main-class 'MileniaLauncher' `
	--app-version '1.0.6' `
	--dest $imageRoot `
	--vendor 'Milenia'
if ($LASTEXITCODE -ne 0)
{
	throw "Launcher packaging failed with exit code $LASTEXITCODE."
}

Compress-Archive -Path (Join-Path $image '*') -DestinationPath $launcherZip -CompressionLevel Optimal -Force
Write-Output "Built launcher package: $launcherZip"
