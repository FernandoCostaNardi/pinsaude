'use strict';
const { spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const workspaceRoot = path.resolve(__dirname, '../..');
const webRoot = path.join(workspaceRoot, 'apps', 'web');

function runNode(binPath, args) {
  console.log(`> node ${path.basename(binPath)} ${args.join(' ')}`);
  const result = spawnSync(process.execPath, [binPath, ...args], {
    cwd: workspaceRoot,
    stdio: 'inherit',
    shell: false,
    env: { ...process.env }
  });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function resolveNodeBin(pkg, binFile) {
  const pnpmDir = path.join(workspaceRoot, 'node_modules', '.pnpm');
  if (!fs.existsSync(pnpmDir)) return null;
  const entries = fs.readdirSync(pnpmDir);
  for (const entry of entries) {
    if (!entry.startsWith(pkg + '@')) continue;
    const candidate = path.join(pnpmDir, entry, 'node_modules', pkg, 'bin', binFile);
    if (fs.existsSync(candidate)) return candidate;
  }
  return null;
}

const action = process.argv[2] || 'build';

if (action === 'build') {
  const tscPath = resolveNodeBin('typescript', 'tsc');
  const vitePath = resolveNodeBin('vite', 'vite.js');

  if (!tscPath || !vitePath) {
    console.error('Could not resolve tsc or vite paths');
    process.exit(1);
  }

  runNode(tscPath, ['--noEmit', '-p', path.join(webRoot, 'tsconfig.json')]);
  runNode(vitePath, ['build', '--config', path.join(webRoot, 'vite.config.ts'), '--outDir', path.join(workspaceRoot, 'dist', 'apps', 'web')]);

} else if (action === 'dev') {
  const vitePath = resolveNodeBin('vite', 'vite.js');
  if (!vitePath) { console.error('Could not resolve vite path'); process.exit(1); }
  runNode(vitePath, ['--config', path.join(webRoot, 'vite.config.ts')]);

} else if (action === 'test') {
  const vitestPath = resolveNodeBin('vitest', 'vitest.js');
  if (!vitestPath) { console.error('Could not resolve vitest path'); process.exit(1); }
  runNode(vitestPath, ['run', '--config', path.join(webRoot, 'vite.config.ts')]);

} else if (action === 'lint') {
  const eslintPath = resolveNodeBin('eslint', 'eslint.js');
  if (!eslintPath) { console.error('Could not resolve eslint path'); process.exit(1); }
  runNode(eslintPath, [path.join(webRoot, 'src'), '--ext', 'ts,tsx']);
}
