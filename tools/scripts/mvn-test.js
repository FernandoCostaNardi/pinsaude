#!/usr/bin/env node
'use strict';
const { spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const module_ = process.argv[2];
if (!module_) { console.error('Usage: node mvn-test.js <module>'); process.exit(1); }

const workspaceRoot = path.resolve(__dirname, '../..');

function resolveMvn() {
  const candidates = [
    process.env.MAVEN_HOME && path.join(process.env.MAVEN_HOME, 'bin', 'mvn.cmd'),
    'C:\\ProgramData\\chocolatey\\lib\\maven\\apache-maven-3.6.3\\bin\\mvn.cmd',
    'C:\\Program Files\\Apache\\maven\\bin\\mvn.cmd',
    'C:\\maven\\bin\\mvn.cmd'
  ].filter(Boolean);
  return candidates.find(p => fs.existsSync(p)) || null;
}

const mvnPath = resolveMvn();
if (!mvnPath) {
  console.error('Maven not found. Set MAVEN_HOME or install Maven.');
  process.exit(1);
}

const javaHome = process.env.JAVA_HOME || 'C:\\Program Files\\Java\\jdk-17.0.2';
const mavenOpts = [
  '-Dmaven.wagon.http.ssl.insecure=true',
  '-Dmaven.wagon.http.ssl.allowall=true',
  '-Dmaven.wagon.http.ssl.ignore.validity.dates=true'
].join(' ');

console.log(`> mvn test -pl ${module_}`);
const result = spawnSync(mvnPath, ['test', '-pl', module_], {
  cwd: workspaceRoot,
  stdio: 'inherit',
  shell: true,
  env: { ...process.env, JAVA_HOME: javaHome, MAVEN_OPTS: mavenOpts }
});
process.exit(result.status ?? 1);
