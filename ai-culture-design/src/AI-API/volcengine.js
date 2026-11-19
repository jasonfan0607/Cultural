// Compatibility facade for the design workspace. Credentials and model selection live on the server.
import { agentImage, agentText } from './agent.js'
const VALID_SIZES = ['2048x2048', '1920x1920', '2560x1440', '1440x2560', '1920x1088', '1088x1920', '2304x1728', '1728x2304']
const SIZE_ALIASES = { '1K': '2048x2048', '2K': '2048x2048', '高清': '2048x2048', '竖版': '1440x2560', '横版': '2560x1440', '手机壁纸': '1440x2560', '电脑壁纸': '2560x1440', '方形': '2048x2048' }
export async function generateImageWithVolcEngine(prompt, model, size = '2048x2048', watermark = true, options = {}) {
  return agentImage(prompt, VALID_SIZES.includes(size) ? size : (SIZE_ALIASES[size] || '2048x2048'), options)
}
export async function generateJiangsuCultureImage(culturalElement, style = '中国风', size = '2048x2048', options = {}) {
  const prompt = `${options.description || culturalElement}，文化元素：${culturalElement}，${style}，用途：${options.purpose || '海报'}，高清画质，精细细节，完整设计作品`
  return generateImageWithVolcEngine(prompt, undefined, size, true, options)
}
export async function generateCultureDesign(culturalElement, style = '中国风', purpose = '海报', options = {}) {
  return agentText(`请结合江苏${culturalElement}，设计${style}风格的${purpose}。完整需求：${options.description || culturalElement}。检索文化依据，给出具体构图、色彩与文案。仅生成文字方案。`, options)
}
