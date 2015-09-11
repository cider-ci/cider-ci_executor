$pinfo = New-Object System.Diagnostics.ProcessStartInfo
$pinfo.FileName = "cmd"
$pinfo.RedirectStandardError = $true
$pinfo.RedirectStandardOutput = $true
$pinfo.UseShellExecute = $false
$pinfo.Arguments = "/c {{script-file-path}}"
$pinfo.WorkingDirectory = "{{working-dir-path}}"

$p = New-Object System.Diagnostics.Process
$p.StartInfo = $pinfo
$p.Start() | Out-Null
$p.WaitForExit()
$stdout = $p.StandardOutput.ReadToEnd()
$stderr = $p.StandardError.ReadToEnd()
Write-Output $stdout
Write-Error $stderr
Write-Host "exit code: " + $p.ExitCode
Exit $p.ExitCode
