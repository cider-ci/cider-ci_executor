open System.Security
open System.Diagnostics
open System.Text
open System

// VARS #######################################################################

let workingDir = """{{working-dir-path}}"""
let interpreter = """cmd.exe"""
let interpreterArguments = """/c {{script-file-path}}"""
let pidFile = """{{pid-file-path}}"""
let execUserName =  """{{exec-user-name}}"""
let execUserPassword = """{{exec-user-password}}"""

let addEnvVars(startInfo : ProcessStartInfo) =
  {% for k,v in environment-variables %}
  if startInfo.EnvironmentVariables.ContainsKey """{{k}}"""
    then startInfo.EnvironmentVariables.Remove """{{k}}"""
  startInfo.EnvironmentVariables.Add("""{{k}}""", """{{v}}""")
  {% endfor %}
  startInfo

// FUN ########################################################################

let envVarOrFail s =
  let pw = System.Environment.GetEnvironmentVariable(s)
  if pw = null then failwith(sprintf "The EnvVar for %s was not found!" s)
    else pw

let userPassword _ =
  envVarOrFail "CIDER_CI_EXEC_USER_PASSWORD"

let toSecureString s =
    let secureString = new SecureString()
    String.iter secureString.AppendChar s
    secureString

let buildStartInfo _ =
  let si = new ProcessStartInfo()
  si.FileName <- interpreter
  si.Arguments <- interpreterArguments
  si.WorkingDirectory <- workingDir
  si.UseShellExecute <- false
  si.RedirectStandardError <- true
  si.RedirectStandardOutput <- true
  si.UserName <- execUserName
  // TODO use the one from env var
  si.Password <- toSecureString execUserPassword
  si.LoadUserProfile <- true
  si

let buildProcess(startInfo : ProcessStartInfo) =
  let proc = new Process()
  proc.StartInfo <- startInfo
  proc

let writePidFile(proc : Process) =
  let pid = proc.Id
  System.IO.File.WriteAllText(pidFile, sprintf "%d" pid)
  printf "PID %d" pid

let runit _ =
  let startInfo = buildStartInfo()
  addEnvVars startInfo |> ignore
  let proc = buildProcess startInfo
  proc.Start() |> ignore
  writePidFile proc
  proc.WaitForExit()
  let proc_stdout = proc.StandardOutput.ReadToEnd()
  printfn "%s" proc_stdout
  let proc_stderr = proc.StandardError.ReadToEnd()
  eprintfn "%s" proc_stderr
  //System.Threading.Thread.Sleep(10000);
  exit proc.ExitCode

let handleFail(ex : Exception)=
  eprintf "%s" ex.Message
  exit -1


// RUN  #######################################################################

try
  runit()
with
  | ex -> handleFail(ex)
