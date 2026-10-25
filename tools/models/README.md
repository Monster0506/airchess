# Chess model generator

Run: `pip install -r tools/models/requirements.txt && python tools/models/generate_models.py`

It regenerates the 13 chess models deterministically in two formats from the same in-memory meshes (no glTF round trip):

- GLB for Android in `app/src/main/assets/models/`
- USDZ for iOS (RealityKit) in `iosApp/Models/`: Y-up, metersPerUnit 1, `UsdPreviewSurface` materials with the glTF base color, metallic and roughness, normals as a `normals` primvar, packaged with `UsdUtils.CreateNewARKitUsdzPackage`

Options:

- `--formats glb|usdz|both` (default `both`)
- `--out DIR` GLB output directory, `--usdz-out DIR` USDZ output directory
- `--preview out.png` quick matplotlib render (needs the GLB output)

Every run checks piece bounds and winding, validates each USDZ with `UsdUtils.ComplianceChecker(arkit=True)` (zero errors required), and asserts that each written file reads back with the same vertex, triangle and normal data as the in-memory mesh. It prints each file's bounds, triangle count and size.

Output is byte-identical across runs and machines: no randomness or timestamps, and the zip entry times inside each USDZ are pinned to 1980-01-01.
