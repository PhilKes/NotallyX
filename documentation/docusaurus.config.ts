import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'NotallyX',
  tagline: 'A simple and minimalistic open source notes app',
  favicon: 'img/favicon.ico',

  // Set the production url of your site here
  url: 'https://github.com',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/NotallyX/',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'PhilKes', // Usually your GitHub org/user name.
  projectName: 'NotallyX', // Usually your repo name.
  deploymentBranch: 'gh-pages', // Usually your repo name.

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl:
            'https://github.com/PhilKes/NotallyX/blob/main/documentation',
        },
        blog: {
          showReadingTime: true,
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl:
            'https://github.com/facebook/docusaurus/tree/main/packages/create-docusaurus/templates/shared/',
          // Useful options to enforce blogging best practices
          onInlineTags: 'warn',
          onInlineAuthors: 'warn',
          onUntruncatedBlogPosts: 'warn',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    // Replace with your project's social card
    image: 'img/featureGraphic.png',
    navbar: {
      title: 'NotallyX',
      logo: {
        alt: 'NotallyX Logo',
        src: 'img/logo.png',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: 'Get Started',
        },
        {
          type: 'doc',
          docId: '/category/features',
          label: 'Features',
          position: 'left',
        },
        {
          href: 'https://ko-fi.com/philkes',
          position: 'right',
          className: "header--kofi-link",
        },
        {
          href: 'https://github.com/PhilKes/NotallyX',
          position: 'right',
          className: "header--github-link",
          "aria-label": "GitHub repository",
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [],
      copyright: `Copyright © ${new Date().getFullYear()} NotallyX, Inc. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
