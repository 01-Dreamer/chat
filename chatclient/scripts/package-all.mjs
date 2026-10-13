#!/usr/bin/env node

import { existsSync, readdirSync, rmSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const projectDir = dirname(dirname(fileURLToPath(import.meta.url)))
const releaseDir = join(projectDir, 'release')
const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm'
const npxCommand = process.platform === 'win32' ? 'npx.cmd' : 'npx'
const finalArtifacts = new Set(['chat-win.exe', 'chat-linux.deb'])
const requiredIcons = [
  'build/icon.png',
  'build/icon.ico',
  'build/icons/16x16.png',
  'build/icons/32x32.png',
  'build/icons/128x128.png',
  'build/icons/256x256.png',
  'build/icons/512x512.png'
]

function run(command, args) {
  const result = spawnSync(command, args, {
    cwd: projectDir,
    env: process.env,
    stdio: 'inherit'
  })

  if (result.error) {
    throw result.error
  }

  if (result.status !== 0) {
    process.exit(result.status ?? 1)
  }
}

function removeBuilderTemporaryFiles() {
  if (!existsSync(releaseDir)) {
    return
  }

  const temporaryNames = new Set([
    'builder-debug.yml',
    'builder-effective-config.yaml',
    'win-unpacked',
    'linux-unpacked',
    'mac',
    'mac-arm64'
  ])

  for (const entry of readdirSync(releaseDir, { withFileTypes: true })) {
    if (finalArtifacts.has(entry.name)) {
      continue
    }

    const isKnownTemporary = temporaryNames.has(entry.name) || entry.name.endsWith('.blockmap')
    const isLegacyArtifact = entry.name.startsWith('ChatClient-') || entry.name.startsWith('Chat-')
    if (isKnownTemporary || isLegacyArtifact) {
      rmSync(join(releaseDir, entry.name), { recursive: entry.isDirectory(), force: true })
    }
  }
}

function validateIcons() {
  const missingIcons = requiredIcons.filter((relativePath) => {
    const absolutePath = join(projectDir, relativePath)
    return !existsSync(absolutePath) || statSync(absolutePath).size === 0
  })

  if (missingIcons.length > 0) {
    console.error('打包图标缺失或为空：')
    for (const icon of missingIcons) {
      console.error(`  ${icon}`)
    }
    process.exit(1)
  }
}

function showArtifacts() {
  const artifacts = existsSync(releaseDir)
    ? readdirSync(releaseDir).filter((name) => finalArtifacts.has(name)).sort()
    : []

  console.log('\n当前安装包：')
  for (const artifact of artifacts) {
    console.log(`  release/${artifact}`)
  }
}

if (process.platform !== 'linux') {
  console.error('请在 Linux 环境执行此脚本，以同时生成 Windows EXE 和 Linux DEB。')
  process.exit(1)
}

removeBuilderTemporaryFiles()
validateIcons()
run(npmCommand, ['run', 'build'])
run(npxCommand, ['electron-builder', '--win', 'nsis', '--linux', 'deb', '--publish', 'never'])
removeBuilderTemporaryFiles()
showArtifacts()
console.log('\nWindows EXE 和 Linux DEB 已生成。')
