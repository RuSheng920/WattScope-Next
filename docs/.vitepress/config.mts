import { defineConfig } from 'vitepress'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

// 版本号唯一来源：主仓库 app/build.gradle.kts 的 versionName。
// release.json 由部署流水线写入（见 .github/workflows/deploy-docs.yml），只在拿不到 gradle 时兜底。
//
// 这里不直接读 release.json 的原因：下载页正文里的版本号曾经是手写死的文本，导航栏跟着
// release.json 走所以一直是对的，正文却在 v1.2.4 / v1.2.5 两次发版后仍停在 v1.2.3。
// 现在正文统一写 {{VERSION}} 占位，由下面的 markdown-it 插件在解析阶段替换。
function resolveVersion(): string {
  try {
    const gradle = readFileSync(
      fileURLToPath(new URL('../../app/build.gradle.kts', import.meta.url)),
      'utf-8'
    )
    const matched = gradle.match(/versionName\s*=\s*"([^"]+)"/)
    if (matched) return `v${matched[1]}`
  } catch {
    // 拿不到 gradle（例如只检出了 docs/）就退回流水线写入的 release.json
  }
  try {
    const release = JSON.parse(
      readFileSync(fileURLToPath(new URL('./release.json', import.meta.url)), 'utf-8')
    )
    if (release?.version) return String(release.version)
  } catch {
    // 两者都拿不到时退化为占位，不让构建中断
  }
  return 'v0.0.0'
}

const VERSION = resolveVersion()

// 在 markdown 解析阶段替换 {{VERSION}}：此时还没进入 Vue 模板编译，
// 所以代码块里的示例命令（`v-pre` 保护范围）也能替换到。
const VERSION_TOKEN = '{{VERSION}}'

function versionTokenPlugin(md: any) {
  md.core.ruler.push('wattscope_version', (state: any) => {
    const walk = (token: any) => {
      if (typeof token.content === 'string' && token.content.includes(VERSION_TOKEN)) {
        token.content = token.content.split(VERSION_TOKEN).join(VERSION)
      }
      if (Array.isArray(token.children)) token.children.forEach(walk)
    }
    state.tokens.forEach(walk)
  })
}

const REPO = 'https://github.com/RuSheng920/WattScope-Next'

// 中文站侧边栏：入门 / 使用 / 深入
const GUIDE_SIDEBAR = [
            {
              text: '入门',
              items: [
                { text: '什么是 WattScope Next', link: '/guide/what-is-wattscope' },
                { text: '安装与上手', link: '/guide/getting-started' },
              ],
            },
            {
              text: '使用',
              items: [
                { text: '功能详解', link: '/guide/features' },
                { text: '悬浮球与 OSD', link: '/guide/overlay' },
                { text: '权限与故障排查', link: '/guide/permissions' },
                { text: '常见问题', link: '/guide/faq' },
              ],
            },
            {
              text: '深入',
              items: [
                { text: '功耗读取方法', link: '/power-reading' },
                { text: '记录链路开销剖析', link: '/power-review-recording' },
              ],
            },
          ]

// https://vitepress.dev/reference/site-config
export default defineConfig({
  base: '/',
  title: 'WattScope Next',
  description: '高通平台 SoC 功耗遥测工具：基于 Qualcomm Power Telemetry，以接近零的运行时开销观测并记录功耗、频率、温度与帧率。',
  lang: 'zh-CN',
  cleanUrls: true,
  lastUpdated: true,

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/logo.svg' }],
    ['meta', { name: 'theme-color', content: '#0a0a0c' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'WattScope Next' }],
    ['meta', { property: 'og:title', content: 'WattScope Next · 高通平台 SoC 功耗遥测' }],
    [
      'meta',
      {
        property: 'og:description',
        content: '以接近零的运行时开销实时观测并记录功耗、频率、温度与帧率。测量工具本身，尽可能不干扰测量结果。',
      },
    ],
    ['meta', { property: 'og:image', content: 'https://watt.rusheng.top/og.png' }],
  ],

  sitemap: {
    hostname: 'https://watt.rusheng.top',
  },

  markdown: {
    config: versionTokenPlugin,
  },

  themeConfig: {
    logo: '/logo.svg',
    socialLinks: [{ icon: 'github', link: REPO }],
    search: {
      provider: 'local',
    },
  },

  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
      themeConfig: {
        nav: [
          { text: '指南', link: '/guide/what-is-wattscope', activeMatch: '/guide/' },
          { text: '深入', link: '/power-reading', activeMatch: '^/(power-reading|power-review-recording)' },
          { text: '下载', link: '/download' },
          {
            text: VERSION,
            items: [
              { text: '更新日志', link: `${REPO}/releases` },
              { text: '问题反馈', link: `${REPO}/issues` },
              { text: '源代码', link: REPO },
            ],
          },
        ],
        sidebar: {
          // 指南与「深入」两篇共用同一套侧边栏，保证从导航进入深度页后导航不断
          '/guide/': GUIDE_SIDEBAR,
          '/power-reading': GUIDE_SIDEBAR,
          '/power-review-recording': GUIDE_SIDEBAR,
        },
        footer: {
          message:
            '基于 <a href="https://github.com/Alita23333/WattScope" target="_blank" rel="noopener">Alita23333/WattScope</a> 二次开发 · 仅供学习与研究使用，请勿用于商业用途',
          copyright: 'Copyright © 2026 RuSheng920 · 应用 ID io.github.rusheng920.wattscope',
        },
        docFooter: { prev: '上一页', next: '下一页' },
        outline: { label: '本页目录' },
        lastUpdated: { text: '最后更新' },
        returnToTopLabel: '回到顶部',
        sidebarMenuLabel: '菜单',
        darkModeSwitchLabel: '主题',
        lightModeSwitchTitle: '切换到浅色模式',
        darkModeSwitchTitle: '切换到深色模式',
      },
    },

    en: {
      label: 'English',
      lang: 'en-US',
      link: '/en/',
      themeConfig: {
        nav: [
          { text: 'Guide', link: '/en/guide/what-is-wattscope', activeMatch: '/en/guide/' },
          { text: 'Download', link: '/en/download' },
          {
            text: VERSION,
            items: [
              { text: 'Changelog', link: `${REPO}/releases` },
              { text: 'Issues', link: `${REPO}/issues` },
              { text: 'Source', link: REPO },
            ],
          },
        ],
        sidebar: {
          '/en/guide/': [
            {
              text: 'Getting Started',
              items: [
                { text: 'What is WattScope Next', link: '/en/guide/what-is-wattscope' },
                { text: 'Install & Setup', link: '/en/guide/getting-started' },
              ],
            },
            {
              text: 'Usage',
              items: [
                { text: 'Features', link: '/en/guide/features' },
                { text: 'Floating Ball & OSD', link: '/en/guide/overlay' },
                { text: 'Permissions & Troubleshooting', link: '/en/guide/permissions' },
                { text: 'FAQ', link: '/en/guide/faq' },
              ],
            },
          ],
        },
        footer: {
          message:
            'Built on <a href="https://github.com/Alita23333/WattScope" target="_blank" rel="noopener">Alita23333/WattScope</a> · For learning and research only, not for commercial use',
          copyright: 'Copyright © 2026 RuSheng920',
        },
        docFooter: { prev: 'Previous page', next: 'Next page' },
        outline: { label: 'On this page' },
        lastUpdated: { text: 'Last updated' },
        returnToTopLabel: 'Return to top',
        sidebarMenuLabel: 'Menu',
        darkModeSwitchLabel: 'Appearance',
        lightModeSwitchTitle: 'Switch to light theme',
        darkModeSwitchTitle: 'Switch to dark theme',
      },
    },
  },
})
