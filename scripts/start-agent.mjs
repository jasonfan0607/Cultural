import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, openSync, closeSync, statSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('../agent-service/', import.meta.url))
const jar = path.join(root, 'target', 'cultural-agent-1.0.0.jar')
const port = process.env.AGENT_PORT || '8081'
if (!/^\d+$/.test(port) || Number(port) < 1 || Number(port) > 65535) throw new Error('AGENT_PORT 必须是有效端口')
const healthUrl = `http://127.0.0.1:${port}/api/agent/health`

async function health() {
  try {
    const response = await fetch(healthUrl, { signal: AbortSignal.timeout(1500) })
    if (!response.ok) return null
    const data = await response.json()
    return data.status === 'UP' && typeof data.modelConfigured === 'boolean' ? data : null
  } catch { return null }
}

function newest(folder) {
  return Math.max(0, ...readdirSync(folder, { withFileTypes: true }).map(entry => {
    const item = path.join(folder, entry.name)
    return entry.isDirectory() ? newest(item) : statSync(item).mtimeMs
  }))
}

async function start() {
  if (await health()) { console.log(`智能体已运行：${healthUrl}`); return }
  if (!existsSync(jar) || statSync(jar).mtimeMs < Math.max(statSync(path.join(root, 'pom.xml')).mtimeMs, newest(path.join(root, 'src', 'main')))) {
    console.log('正在构建智能体服务……')
    await new Promise((resolve, reject) => {
      // Fixed command: never interpolate user input or credentials into shell text.
      const build = process.platform === 'win32'
        ? spawn('mvn -B -q -DskipTests package', { cwd: root, shell: true, windowsHide: true, stdio: 'inherit' })
        : spawn('mvn', ['-B', '-q', '-DskipTests', 'package'], { cwd: root, stdio: 'inherit' })
      build.on('error', () => reject(new Error('无法启动 Maven，请安装 Maven 和 JDK 并配置 PATH')))
      build.on('exit', code => code === 0 ? resolve() : reject(new Error('智能体构建失败，请检查上方输出')))
    })
  }
  const logs = path.join(root, 'logs')
  mkdirSync(logs, { recursive: true })
  const stdout = openSync(path.join(logs, 'agent.out.log'), 'a')
  const stderr = openSync(path.join(logs, 'agent.err.log'), 'a')
  let child
  let startupError
  try {
    child = spawn('java', ['-jar', jar, `--server.port=${port}`], {
      cwd: root, detached: true, windowsHide: true, stdio: ['ignore', stdout, stderr]
    })
    child.on('error', () => { startupError = new Error('无法启动 Java，请安装 JDK 17+ 并配置 PATH') })
  } finally { closeSync(stdout); closeSync(stderr) }
  for (let attempt = 0; attempt < 60; attempt++) {
    if (startupError) throw startupError
    if (child.exitCode !== null) throw new Error(`智能体启动失败，请查看 ${logs}`)
    const result = await health()
    if (result) {
      child.unref()
      console.log(`智能体已启动（后台运行，PID ${child.pid}）：${healthUrl}`)
      if (!result.modelConfigured) console.log('请在 agent-service/application-local.yml 配置 DeepSeek 密钥后重启智能体。')
      return
    }
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  child.kill()
  throw new Error(`智能体启动超时，请查看 ${logs}`)
}

start().catch(error => { console.error(error.message); process.exitCode = 1 })
