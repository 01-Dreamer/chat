import { spawn } from 'node:child_process'

const executable = process.platform === 'win32' ? 'npx.cmd' : 'npx'
const child = spawn(executable, ['electron', '--no-sandbox', '.'], { stdio: 'inherit', env: process.env })
child.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal)
  else process.exit(code ?? 0)
})
