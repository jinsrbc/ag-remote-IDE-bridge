Set-Location "C:\Users\jinsc\Desktop\gemma"
$output = Get-ChildItem -Filter "gradlew*"
Write-Host "Found files:"
$output | ForEach-Object { Write-Host $_.FullName }
$batExists = Test-Path "gradlew.bat"
Write-Host "gradlew.bat exists: $batExists"
if ($batExists) {
    Write-Host "Running build..."
    & "C:\Users\jinsc\Desktop\gemma\gradlew.bat" assembleDebug
}