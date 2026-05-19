# NSUK Preview Toggle Patch

Client-side Forge 1.20.1 patch mod for New Sim-U-Kraft. It adds a keybind to toggle an active large-building preview between the original range-box preview and full block preview.

## Usage

- Minecraft: 1.20.1
- Forge: 47.4.4 or newer 47.x
- Default key: `P`
- Required mod: New Sim-U-Kraft with mod id `simukraft`

When a New Sim-U-Kraft building preview is active, press `P` to switch modes. Full block preview can freeze or lag the client for very large buildings.

## Build On GitHub

This repository is intended to build on GitHub Actions.

1. Create a GitHub repository.
2. Push these files to the repository's `main` or `master` branch.
3. Open the Actions tab and manually run the `Build NSUK Preview Toggle Patch` workflow.
4. Enter the GitHub Release name. Leave the jar file name empty to use `[NSUK预览切换]nsuk-preview-toggle-forge-0.<run-number>-1.0.5b3-fix.jar`.
5. Download the jar from the generated GitHub Release, or from the `nsuk-preview-toggle-patch` workflow artifact.

No local build is required.

## Implementation Notes

The patch does not compile against the New Sim-U-Kraft jar. It uses reflection to call `com.xiaoliang.simukraft.client.preview.BuildingPreviewManager` at runtime.
