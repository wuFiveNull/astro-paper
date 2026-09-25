import { createMarkdownProcessor } from "@astrojs/markdown-remark";
import { rehypePlugins, remarkPlugins, shikiConfig } from "@/config/markdown";

const processor = createMarkdownProcessor({
  syntaxHighlight: "shiki",
  shikiConfig,
  remarkPlugins,
  rehypePlugins,
});

export async function renderMarkdown(markdown: string): Promise<string> {
  const renderer = await processor;
  const rendered = await renderer.render(markdown);
  return rendered.code;
}
