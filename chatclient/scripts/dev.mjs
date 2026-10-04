import { spawn } from 'node:child_process'

const command = process.platform === 'win32' ? 'npx.cmd' : 'npx'
const child = spawn(command, ['electron-vite', 'dev', '--noSandbox'], {
  stdio: 'inherit',
  env: {
    ...process.env,
    CHATCLIENT_DEV: '1',
    ELECTRON_DISABLE_SANDBOX: '1',
    ELECTRON_CLI_ARGS: JSON.stringify(['--disable-gpu', '--disable-software-rasterizer']),
  },
})

child.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal)
  else process.exit(code ?? 0)
})
