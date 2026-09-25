import assert from "node:assert/strict";
import test from "node:test";
import { createMarkdownProcessor } from "@astrojs/markdown-remark";
import rehypeCallouts from "rehype-callouts";
import { rehypeResponsiveTables } from "./rehypeResponsiveTables.mjs";
import { rehypeSanitizeMarkdown } from "./rehypeSanitizeMarkdown.mjs";

const processor = await createMarkdownProcessor({
  syntaxHighlight: "shiki",
  shikiConfig: {
    themes: { light: "min-light", dark: "night-owl" },
    defaultColor: false,
    wrap: false,
  },
  rehypePlugins: [
    rehypeCallouts,
    rehypeResponsiveTables,
    rehypeSanitizeMarkdown,
  ],
});

async function render(markdown) {
  return (await processor.render(markdown)).code;
}

test("removes unsafe HTML, event handlers, protocols, and inline CSS", async () => {
  const html = await render(
    [
      '<script>alert("xss")</script>',
      '<img src="https://example.test/image.png" onerror="alert(1)" />',
      '<a href="javascript:alert(1)" onclick="alert(2)">unsafe link</a>',
      '<span style="position:fixed;inset:0;background:url(javascript:alert(1))">styled</span>',
      '<pre style="display:none;--shiki-light:#abcdef" class="astro-code"><span style="color:red;--shiki-dark:#123456">code</span></pre>',
    ].join("\n")
  );

  assert.doesNotMatch(
    html,
    /<script|onerror|onclick|javascript:|position:fixed|display:none|inset:0|color:red/
  );
  assert.match(html, /<img src="https:\/\/example\.test\/image\.png">/);
  assert.match(html, /--shiki-light:#abcdef/);
  assert.match(html, /--shiki-dark:#123456/);
  assert.match(html, /unsafe link/);
});

test("retains AstroPaper callouts, responsive tables, and Shiki highlighting", async () => {
  const fence = String.fromCharCode(96).repeat(3);
  const markdown = [
    "> [!NOTE] Safe title",
    "> Callout content.",
    "",
    "| Name | Value |",
    "| --- | --- |",
    "| one | two |",
    "",
    fence + "js",
    "const answer = 42;",
    fence,
  ].join("\n");
  const html = await render(markdown);

  assert.match(html, /class="callout"/);
  assert.match(html, /data-callout="note"/);
  assert.match(html, /stroke-linecap="round"/);
  assert.match(html, /stroke-linejoin="round"/);
  assert.match(html, /\[&#x26;_table\]:min-w-xl/);
  assert.match(html, /class="astro-code/);
  assert.match(html, /class="line"/);
  assert.match(html, /--shiki-light:/);
  assert.match(html, /--shiki-dark:/);
});

test("keeps safe video embeds used by existing articles", async () => {
  const html = await render(
    '<video controls autoplay src="https://example.test/demo.mp4" onplay="alert(1)"><source src="https://example.test/demo.mp4" type="video/mp4"></video>'
  );

  assert.match(html, /<video[^>]*controls[^>]*>/);
  assert.match(html, /autoplay/);
  assert.match(
    html,
    /<source src="https:\/\/example\.test\/demo\.mp4" type="video\/mp4">/
  );
  assert.doesNotMatch(html, /onplay/);
});
