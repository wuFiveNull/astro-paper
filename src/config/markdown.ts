import remarkCollapse from "remark-collapse";
import remarkToc from "remark-toc";
import rehypeCallouts from "rehype-callouts";
import {
  transformerNotationDiff,
  transformerNotationHighlight,
  transformerNotationWordHighlight,
} from "@shikijs/transformers";
import { transformerFileName } from "../utils/transformers/fileName";
import type {
  RehypePlugins,
  RemarkPlugins,
  ShikiConfig,
} from "@astrojs/markdown-remark";

export const remarkPlugins = [
  remarkToc,
  [remarkCollapse, { test: "Table of contents" }],
] satisfies RemarkPlugins;

export const rehypePlugins = [rehypeCallouts] satisfies RehypePlugins;

export const shikiConfig = {
  themes: { light: "min-light", dark: "night-owl" },
  defaultColor: false,
  wrap: false,
  transformers: [
    transformerFileName({ style: "v2", hideDot: false }),
    transformerNotationHighlight(),
    transformerNotationWordHighlight(),
    transformerNotationDiff({ matchAlgorithm: "v3" }),
  ],
} satisfies ShikiConfig;
