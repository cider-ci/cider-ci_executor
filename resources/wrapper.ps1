$process = Start-Process cmd `
  -Wait -NoNewWindow -PassThru `
  -ArgumentList "/c {{script-file-path}}" `
  -WorkingDirectory "{{working-dir-path}}"
Write-Output $process.StandardOutput
# Write-Error $process.StandardError
Exit $process.ExitCode
