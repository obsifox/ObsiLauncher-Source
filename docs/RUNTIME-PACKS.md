# Runtime packs

ObsiLauncher (Android) manages versions, downloads and the game process itself.
The actual game execution needs a **runtime pack**: a JVM built for Android plus the
native glue libraries (LWJGL port + the MobileGlues OpenGL renderer).

> ObsiLauncher does not bundle a JVM — like every Minecraft launcher on Android, the
> JVM comes from a pack, which keeps the launcher APK small and the runtimes updatable.

## Pack format

A pack is a `.tar.xz` archive that unpacks to:

```
pack.json          # manifest (below)
lib/               # native libraries loaded by the JVM (LD_LIBRARY_PATH)
lib/launcher.so    # the JVM launcher shared object — the entry point ObsiLauncher execs
<files>            # whatever else the JVM needs (rt modules, security, jexec helpers…)
```

`pack.json`:

```json
{
  "name": "JRE 21 (arm64)",
  "abi": "arm64-v8a",
  "launcher_so": "lib/launcher.so",
  "jvm_args": ["-Dorg.lwjgl.opengl.libname=libMobileGlues.so"],
  "lib_dirs": ["lib"]
}
```

| Field | Meaning |
|---|---|
| `name` | Shown in Settings → Runtime |
| `abi` | Architecture the pack was built for (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) |
| `launcher_so` | Path (inside the pack) of the shared object passed to `/system/bin/linker64` |
| `jvm_args` | Extra JVM flags the pack needs (renderer selection, LWJGL paths, …) |
| `lib_dirs` | Directories added to `LD_LIBRARY_PATH` |

## How it is started

Android 10+ forbids `exec()` of files inside app data. ObsiLauncher therefore
`execve()`s the **system linker** and lets it load the pack's launcher:

```
execve("/system/bin/linker64",
       ["/system/bin/linker64", "<pack>/lib/launcher.so",
        "-Xmx2048M", …, "-cp", "<client.jar>:<libraries>", "net.minecraft.client.main.Main",
        "--username", …, "--gameDir", …, "--assetsDir", …],
       envp)
```

stdout/stderr of the child are piped into the launcher's Console page.

## Getting packs

Build one yourself from any open-source Android JVM build (GPL JRE builds exist from
the PoJavLauncher-family build scripts) and package it with the layout above — the
format is deliberately tiny. Place the required LWJGL Android natives and your chosen
renderer glue (e.g. **MobileGlues**) into `lib/` and reference them through
`jvm_args`. Then install it in the app: **Settings → Runtime → paste URL → Download
& install**, or point the field at a local HTTP mirror you control.
