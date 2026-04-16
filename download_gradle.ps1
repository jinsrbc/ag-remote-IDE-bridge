$temp = "$env:TEMP\gradlew_downloaded.bat"
Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/master/gradlew.bat' -OutFile $temp
Write-Host "Downloaded to: $temp"
Write-Host "Content length: $((Get-Item $temp).Length)"