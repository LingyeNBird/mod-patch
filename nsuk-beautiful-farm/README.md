# NSUK Beautiful Farm Patch

Forge 1.20.1 patch mod for New Sim-U-Kraft `1.0.5b3-fix`.

Planned behavior:

- Adds farm decoration selection controls to the farmland box free-camera area selection screen.
- Supports the first three decoration groups: border, water, and water cover.
- Saves decoration choices per farmland box on the server.
- Adds preview blocks while selecting the farmland area.
- Starts a server-side decoration placement queue after farmland is created.

This project intentionally uses reflection for NSUK integration so GitHub Actions can build without bundling the NSUK jar.
