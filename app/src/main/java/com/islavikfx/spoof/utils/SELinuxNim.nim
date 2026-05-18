import os, osproc, strutils, times, json


type
  BypassResult = object
    success: bool
    message: string
    methodName: string
    timestamp: string


proc runShell(cmd: string): (int, string) =
  let p = execCmdEx(cmd)
  return (p.exitCode, p.output)

proc disableSelinuxViaMapping(): bool =
  discard runShell("echo '2' > /sys/fs/selinux/allow_unknown 2>/dev/null")
  discard runShell("echo 1 > /sys/fs/selinux/reload_policy 2>/dev/null")
  return true


proc disableSelinuxWithMagisk(): bool =
  let commands = @[
    "magiskpolicy --live 'allow shell app_data_file dir write' 2>/dev/null",
    "magiskpolicy --live 'allow shell app_data_file file write' 2>/dev/null",
    "magiskpolicy --live 'allow shell app_data_file dir read' 2>/dev/null",
    "magiskpolicy --live 'allow shell app_data_file file read' 2>/dev/null",
    "magiskpolicy --live 'allow untrusted_app app_data_file dir write' 2>/dev/null",
    "magiskpolicy --live 'allow untrusted_app app_data_file file write' 2>/dev/null"
  ]
  for cmd in commands:
    let (code, _) = runShell(cmd)
    if code != 0: return false
  return true


proc runWithSelinuxContext(command: string): bool =
  let contexts = @["u:r:shell:s0", "u:r:init:s0", "u:r:system_app:s0", "u:r:su:s0"]
  for ctx in contexts:
    let cmd = "su --context " & ctx & " -c \"" & command & "\""
    let (code, _) = runShell(cmd)
    if code == 0: return true
  return false

proc executeWithBypass(command: string): BypassResult =
  if disableSelinuxViaMapping():
    let (code, output) = runShell(command)
    if code == 0:
      return BypassResult(success: true, message: "OK via mapping", methodName: "mapping", timestamp: $now())

  if disableSelinuxWithMagisk():
    let (code, output) = runShell(command)
    if code == 0:
      return BypassResult(success: true, message: "OK via magisk", methodName: "magisk", timestamp: $now())

  let (code, output) = runShell(command)
  if code == 0:
    return BypassResult(success: true, message: "OK direct", methodName: "direct", timestamp: $now())
  return BypassResult(success: false, message: "Failed: " & output, methodName: "none", timestamp: $now())


proc installPolicy(): bool =
  let policyRules = @[
    "allow shell app_data_file dir { read write open create}",
    "allow shell app_data_file file { read write open create}",
    "allow untrusted_app app_data_file dir { read write}",
    "allow untrusted_app app_data_file file { read write}",
    "allow shell system_file file { execute execute_no_trans}",
    "allow shell proc file { read write}",
    "allow shell sysfs file { read write}"
  ]

  let policyPath = "/data/local/tmp/android_spoof_policy"
  writeFile(policyPath, policyRules.join("\n"))
  let (code, _) = runShell("magiskpolicy --live --load " & policyPath)
  discard runShell("rm -f " & policyPath)
  return code == 0


proc getSelinuxStatus(): string =
  let (_, status) = runShell("getenforce 2>/dev/null")
  return status.strip()

proc isEnforcing(): bool =
  return getSelinuxStatus() == "Enforcing"


proc main() =
  let args = commandLineParams()

  if args.len == 0:
    echo $(%*{"error": "No command specified"})
    return

  case args[0]:
  of "bypass":
    if args.len < 2:
      echo $(%*{"error": "No command to execute"})
      return
    let result = executeWithBypass(args[1])
    echo $(%result)

  of "install_policy":
    let success = installPolicy()
    echo $(%*{
      "success": success,
      "message": (if success: "Policy installed" else: "Policy install failed"),
      "timestamp": $now()
    })

  of "status":
    let status = getSelinuxStatus()
    echo $(%*{
      "selinux_status": status,
      "enforcing": status == "Enforcing",
      "timestamp": $now()
    })

  of "execute":
    if args.len < 2:
      echo $(%*{"error": "No command to execute"})
      return
    let (code, output) = runShell(args[1])
    echo $(%*{
      "exit_code": code,
      "output": output,
      "success": code == 0
    })

  else:
    echo $(%*{"error": "Unknown command: " & args[0]})


when isMainModule:
  main()


# nim c \
#   --os:linux \
#   --cpu:arm64 \
#   --gcc.exe="/home/unknown/AndroidNDKr30/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android37-clang" \
#   --gcc.linkerexe="/home/unknown/AndroidNDKr30/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android37-clang++" \
#   -passC:"-target aarch64-linux-android37" \
#   --passL:"-target aarch64-linux-android37" \
#   --passL:"-Wl,--allow-shlib-undefined" \
#   -d:release \
#   --opt:size \
#   -d:strip \
#   -d:useMalloc \
#   --app:console \
#   --out:app/src/main/assets/bin/SELinuxNim \
#   /home/unknown/AndroidStudio/Projects/AndroidSpoof/app/src/main/java/com/islavikfx/spoof/utils/SELinuxNim.nim