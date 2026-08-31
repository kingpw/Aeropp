import fs from "node:fs/promises"
import path from "node:path"
import {
  createScene,
  makeModelScene,
  prepareAssets,
  renderModelScene,
} from "block-model-renderer"

const [scenePath, packsPath, outputDir] = process.argv.slice(2)
if (!scenePath || !packsPath || !outputDir) {
  throw new Error("Usage: node render_structure.mjs scene.json packs.json output-dir")
}

const data = JSON.parse(await fs.readFile(scenePath, "utf8"))
const packs = JSON.parse(await fs.readFile(packsPath, "utf8"))
await fs.mkdir(outputDir, { recursive: true })

const assets = await prepareAssets(packs, {
  cache: true,
  defaults: "game",
  version: "1.21.1",
})

async function renderSet(name, blocks, views) {
  const { scene, camera } = makeModelScene()
  const handle = await createScene(assets, blocks, {
    animate: false,
    defaults: "game",
    lighting: { daytime: 6000, brightness: 0.3 },
    optimize: true,
  })
  scene.add(handle.group)
  const center = handle.bounds.getCenter(camera.position.clone())
  const size = handle.bounds.getSize(camera.position.clone())
  const radius = Math.max(size.x, size.y, size.z) * 0.72

  for (const [viewName, direction] of views) {
    const length = Math.hypot(...direction)
    const unit = direction.map(value => value / length)
    const distance = radius * 3
    camera.position.set(center.x + unit[0] * distance, center.y + unit[1] * distance, center.z + unit[2] * distance)
    camera.up.set(0, 1, 0)
    camera.lookAt(center)
    camera.left = -radius
    camera.right = radius
    camera.top = radius
    camera.bottom = -radius
    camera.near = 0.1
    camera.far = distance * 4
    camera.updateProjectionMatrix()
    handle.sortTranslucent(camera)
    await renderModelScene(scene, camera, {
      path: path.join(outputDir, `${name}_${viewName}.png`),
      width: 1400,
      height: 1000,
      background: "#b8cce0",
    })
  }
  handle.dispose()
}

await renderSet("exterior", data.blocks, [
  ["southeast", [1, 0.7, 1]],
  ["northwest", [-1, 0.7, -1]],
])

await renderSet("plan_cut", data.blocks.filter(block => block.pos[1] <= 8), [
  ["high", [0.8, 1.7, 1]],
])

await renderSet("center_section", data.blocks.filter(block => block.pos[0] <= Math.floor(data.size[0] / 2)), [
  ["east", [1, 0.35, 0]],
])

await renderSet("boiler_section", data.blocks.filter(block => block.pos[0] <= 12), [
  ["east", [1, 0.45, 0.2]],
])

await renderSet("assembly_section", data.blocks.filter(block => block.pos[2] >= 50), [
  ["north", [0, 0.55, -1]],
])

console.log(outputDir)
