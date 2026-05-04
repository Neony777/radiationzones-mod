# Auto-Release Workflow (manual install required)

The Replit GitHub connector that pushes this repo wasn't granted GitHub's
`workflow` scope, so it isn't allowed to create or modify any file under
`.github/workflows/`. The release workflow below has to be added once
through the GitHub web UI. After that, every `vX.Y.Z` tag push will
build the mod with JDK 21 and publish a GitHub Release with the jar
attached automatically.

## One-time install

1. Open https://github.com/Neony777/Radiation-Zone-Mod
2. Click **Add file → Create new file**
3. Name it exactly: `.github/workflows/release.yml`
4. Paste the YAML below
5. **Commit directly to `main`**

## The workflow

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: radiationzones-mod
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build with Gradle
        run: gradle --no-daemon --console=plain build -x test

      - name: List built artifacts
        run: ls -la build/libs/

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: radiationzones-mod/build/libs/*.jar
          generate_release_notes: true
          fail_on_unmatched_files: true
          body: |
            RadiationZones for Minecraft 1.21.1 (NeoForge 21.1.x).

            **Created by Neo (Neonekpro).**

            Drop the `.jar` from the assets below into your server's `mods/` folder.
            See the [README](https://github.com/Neony777/Radiation-Zone-Mod/blob/main/radiationzones-mod/README.md)
            for the full feature list and admin commands.
```

## Cutting future releases

Once the workflow file above is in place:

1. Bump `mod_version` in `radiationzones-mod/gradle.properties`
2. `git tag vX.Y.Z && git push origin vX.Y.Z`

The workflow takes it from there.
