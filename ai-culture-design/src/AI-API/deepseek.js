// Text generation through the authenticated backend agent.
import { agentText } from './agent.js'
export async function generateCultureDesign(designType, culturalElements, style, purpose, options = {}) {
  const content = await agentText(`请设计${designType}。文化元素：${culturalElements}；风格：${style}；用途：${purpose}。先检索文化依据，再给出具体方案。仅生成文字。`, options)
  return { success: true, content, elements: culturalElements, style, purpose }
}
export async function generateCreativeContent(prompt, culturalContext, options = {}) {
  return { success: true, content: await agentText(`文化背景：${culturalContext}\n需求：${prompt}`, options) }
}
export async function analyzeCulturalElements(elements, options = {}) {
  return { success: true, analysis: await agentText(`请检索并分析这些文化元素的历史、视觉特征及设计用法：${elements}`, options) }
}
