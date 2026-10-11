// Run a project script inside the local Blockbench application through its plugin API.
// Start Blockbench with --remote-debugging-port=9222 before invoking this helper.
const fs = require('node:fs');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..');
const sourcePath = path.join(projectRoot, 'models/cosmetics/bamboo_douli.bbmodel');
const resourcePath = 'src/main/resources/assets/setsuna/cosmetics/bamboo_douli.json';

function makeOutputDirectories(root) {
  for (const directory of ['models/cosmetics', 'src/main/resources/assets/setsuna/cosmetics',
    'src/main/resources/assets/setsuna/textures/cosmetics']) {
    fs.mkdirSync(path.join(root, directory), {recursive: true});
  }
}

async function main() {
  const operation = process.argv[2];
  if (!operation) throw new Error('Usage: node tools/blockbench-api.cjs create|export|verify');
  const { chromium } = require('playwright');
  const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
  try {
    const page = browser.contexts().flatMap(c => c.pages()).find(p => /app\.asar\/index\.html/.test(p.url()));
    if (!page) throw new Error('The local Blockbench desktop application is not open.');
    let result;
    if (['create', 'export', 'verify'].includes(operation)) {
      const outputRoot = operation === 'verify'
        ? path.join(projectRoot, 'build/reports/douli/roundtrip') : projectRoot;
      makeOutputDirectories(outputRoot);
      const model = operation === 'verify' ? JSON.parse(fs.readFileSync(sourcePath, 'utf8')) : null;
      result = await page.evaluate(async ({operation, pluginPath, sourcePath, outputRoot, model}) => {
        const installed = Plugins.all.find(plugin => plugin.id === 'setsuna_douli');
        if (installed) await installed.reload();
        else await new Plugin().loadFromFile({path: pluginPath}, false);
        if (operation === 'create') await SetsunaDouli.create();
        if (operation === 'verify') {
          Codecs.project.load(model, {path: sourcePath});
          const texture = Texture.all[0];
          if (!texture.img.complete) {
            await new Promise(resolve => texture.img.addEventListener('load', resolve, {once:true}));
          }
        }
        const exported = SetsunaDouli.exportTo(outputRoot);
        if (operation === 'verify') Project.save_path = sourcePath;
        return {blockbench: Blockbench.version, operation, ...exported};
      }, {operation, pluginPath: path.join(__dirname, 'blockbench/setsuna_douli.js'), sourcePath, outputRoot, model});
      if (operation === 'verify') {
        const expected = JSON.parse(fs.readFileSync(path.join(projectRoot, resourcePath), 'utf8'));
        const actual = JSON.parse(fs.readFileSync(path.join(outputRoot, resourcePath), 'utf8'));
        if (JSON.stringify(expected) !== JSON.stringify(actual)) {
          throw new Error('The packaged mesh differs from the reopened Blockbench source. Export it again.');
        }
        result.runtimeMeshMatchesSource = true;
      }
    } else {
      result = await page.evaluate(fs.readFileSync(operation, 'utf8'));
    }
    console.log(JSON.stringify(result, null, 2));
    if (process.argv[3]) await page.screenshot({path: process.argv[3]});
  } finally {
    await browser.close();
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
