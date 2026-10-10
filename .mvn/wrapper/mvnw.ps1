$ErrorActionPreference = "Stop"
$wrapperDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$propsFile = Join-Path $PSScriptRoot "maven-wrapper.properties"
$distUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"

if (Test-Path $propsFile) {
    $lines = Get-Content $propsFile
    foreach ($line in $lines) {
        if ($line -match "^distributionUrl=(.+)$") {
            $distUrl = $Matches[1].Trim() -replace "\\:", ":"
        }
    }
}

$m2WrapperDir = Join-Path $env:USERPROFILE ".m2\wrapper\dists\apache-maven-3.9.9"
$mvnCmd = Join-Path $m2WrapperDir "apache-maven-3.9.9\bin\mvn.cmd"

if (-not (Test-Path $mvnCmd)) {
    New-Item -ItemType Directory -Force -Path $m2WrapperDir | Out-Null
    $zipFile = Join-Path $m2WrapperDir "maven.zip"
    Write-Host "Downloading Maven 3.9.9 from $distUrl ..."
    Invoke-WebRequest -Uri $distUrl -OutFile $zipFile
    Expand-Archive -Path $zipFile -DestinationPath $m2WrapperDir -Force
    Remove-Item $zipFile -Force
}

& $mvnCmd @args
exit $LASTEXITCODE
