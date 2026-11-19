export function agentProxyOptions(port = '8081') {
  if (!/^\d+$/.test(String(port))) throw new Error('AGENT_PORT 必须是有效端口')
  return {
    target: `http://127.0.0.1:${port}`,
    changeOrigin: true,
    rewrite: path => path.replace(/^\/agent-api/, '/api/agent'),
    timeout: 180000,
    proxyTimeout: 180000,
    configure(proxy) {
      // Vite otherwise emits an empty HTTP 500 when the agent is offline.
      proxy.on('error', (_error, _request, response) => {
        if (!response || typeof response.writeHead !== 'function' || response.headersSent || response.writableEnded) return
        response.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' })
        response.end(JSON.stringify({ code: 503, message: '智能体服务暂时无法连接，请在项目根目录运行 npm run start:agent 后重试。' }))
      })
    }
  }
}
