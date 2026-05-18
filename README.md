# Mod Patch Workspace

This repository can contain multiple independent patch mods. Each patch mod lives in its own subdirectory so future patches can be added side by side.

## Patches

- `nsuk-preview-toggle-patch/` - Forge 1.20.1 client-side patch that toggles New Sim-U-Kraft large building previews.

## GitHub Actions

Workflows live in the repository-level `.github/workflows/` directory because GitHub only discovers Actions workflows from there. Each workflow builds its corresponding patch subproject from that subdirectory.

Workflows are manually triggered. The NSUK workflow accepts a release asset jar file name and a GitHub Release name, then uploads the built jar as a release asset.
