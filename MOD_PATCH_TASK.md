# NSUK Preview Toggle Patch Task

## Goal
Create an independent Forge 1.20.1 client-side patch mod in:

```text
C:\Users\MSIK\projects\他人项目\NS\mod-patch
```

Do not build locally. Add a GitHub Actions workflow so the user can create a repository, push the files, and build the jar on GitHub.

## Background
The target jar is:

```text
C:\Users\MSIK\projects\他人项目\NS\New-Sim-U-Kraft-1.20.1Forge-src\NewSim-U-Kraft-1.0.5b3-fix.jar
```

The current source tree is unrelated to that jar, so the patch should not depend on this repo's source code.

Jar analysis found the large-building preview logic in:

```text
com.xiaoliang.simukraft.client.preview.BuildingPreviewManager
```

Relevant methods and fields:

```text
public static boolean isPreviewActive()
public static boolean isRangeOnlyPreview()
public static void loadBlocks()
private static void activateRangeOnlyPreview()
```

Large buildings switch to range-box preview when:

```text
volume >= 32768
or max(width, height, depth) >= 128
```

There is no built-in keybind or config to switch back to full block preview.

## Recommended Implementation
Make a small client-only Forge mod named for example:

```text
mod_id=nsukpreviewtoggle
mod_name=NSUK Preview Toggle Patch
```

Use reflection instead of adding the original New:Sim-U-Kraft jar as a compile dependency.

Default keybind:

```text
P = Toggle large building preview mode
```

Behavior:

```text
If no NSUK building preview is active:
  show client action-bar message

If active and currently range-only:
  call BuildingPreviewManager.loadBlocks()
  show "switched to full block preview"

If active and currently full preview:
  reflectively call private BuildingPreviewManager.activateRangeOnlyPreview()
  show "switched to range-box preview"
```

Important: full block preview for very large buildings may freeze or lag the client. The message should warn about that.

## Suggested Files
Create a normal ForgeGradle project:

```text
settings.gradle
build.gradle
gradle.properties
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
.gitignore
README.md
.github/workflows/build.yml
src/main/java/com/mskb/nsukpreviewtoggle/NSUKPreviewTogglePatch.java
src/main/java/com/mskb/nsukpreviewtoggle/client/ClientKeyHandler.java
src/main/java/com/mskb/nsukpreviewtoggle/client/SimukraftPreviewBridge.java
src/main/resources/META-INF/mods.toml
src/main/resources/pack.mcmeta
src/main/resources/assets/nsukpreviewtoggle/lang/en_us.json
src/main/resources/assets/nsukpreviewtoggle/lang/zh_cn.json
```

Use Minecraft/Forge versions matching the target mod:

```properties
minecraft_version=1.20.1
forge_version=47.4.4
mapping_channel=official
mapping_version=1.20.1
java=17
```

## Bridge Class Sketch
Use reflection against the original mod:

```java
final class SimukraftPreviewBridge {
    private static final String MANAGER_CLASS =
            "com.xiaoliang.simukraft.client.preview.BuildingPreviewManager";

    static boolean isPreviewActive() throws ReflectiveOperationException {
        return (Boolean) managerClass().getMethod("isPreviewActive").invoke(null);
    }

    static boolean isRangeOnlyPreview() throws ReflectiveOperationException {
        return (Boolean) managerClass().getMethod("isRangeOnlyPreview").invoke(null);
    }

    static void loadBlocks() throws ReflectiveOperationException {
        managerClass().getMethod("loadBlocks").invoke(null);
    }

    static void activateRangeOnlyPreview() throws ReflectiveOperationException {
        Method method = managerClass().getDeclaredMethod("activateRangeOnlyPreview");
        method.setAccessible(true);
        method.invoke(null);
    }

    private static Class<?> managerClass() throws ClassNotFoundException {
        return Class.forName(MANAGER_CLASS);
    }
}
```

Caching the reflected `Class` and `Method` objects is preferred.

## Key Handler Notes
Register a client key mapping on the MOD event bus:

```text
RegisterKeyMappingsEvent
```

Handle key presses on the Forge event bus:

```text
TickEvent.ClientTickEvent
phase == END
while (key.consumeClick()) { ... }
```

Use `LocalPlayer.displayClientMessage(message, true)` for action-bar messages.

## mods.toml Notes
This patch is client-only:

```toml
clientSideOnly=true
```

Declare dependency after `simukraft`:

```toml
[[dependencies.nsukpreviewtoggle]]
    modId="simukraft"
    mandatory=true
    versionRange="[1.0.0,)"
    ordering="AFTER"
    side="CLIENT"
```

## GitHub Actions
Add:

```text
.github/workflows/build.yml
```

Workflow:

```yaml
name: Build

on:
  push:
    branches: [ main, master ]
  pull_request:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle
      - run: chmod +x ./gradlew
      - run: ./gradlew build --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: nsuk-preview-toggle-patch
          path: build/libs/*.jar
```

## Constraints
- Do not run a local build unless the user explicitly asks.
- Do not modify `NewSim-U-Kraft-1.0.5b3-fix.jar`.
- Do not depend on the unrelated current source tree.
- Keep the patch mod client-side only.
