import rehypeRaw from "rehype-raw";
import rehypeSanitize, { defaultSchema } from "rehype-sanitize";

const calloutClasses = [
  "callout",
  "callout-title",
  "callout-title-icon",
  "callout-title-text",
  "callout-content",
  "callout-fold-icon",
];

const responsiveTableClasses = [
  "overflow-hidden",
  "[&_table]:my-0",
  "[&_table]:min-w-xl",
  "relative",
  "w-full",
  "overflow-x-auto",
];

const shikiPreClasses = [
  "astro-code",
  "astro-code-themes",
  "min-light",
  "night-owl",
  "mt-8",
  "rounded-tl-none",
];

const shikiSpanClasses = [
  "line",
  "diff",
  "add",
  "remove",
  "highlighted",
  "highlighted-word",
  "absolute",
  "py-1",
  "text-foreground",
  "text-xs",
  "font-medium",
  "leading-4",
  "pl-4",
  "pr-2",
  "before:inline-block",
  "before:size-1",
  "before:bg-green-500",
  "before:rounded-full",
  "before:absolute",
  "before:top-[45%]",
  "before:left-2",
  "px-2",
  "left-2",
  "top-(--file-name-offset)",
  "border",
  "rounded-md",
  "bg-background",
  "left-0",
  "-top-6",
  "rounded-t-md",
  "border-b-0",
  "bg-muted/50",
];

const schema = {
  ...defaultSchema,
  tagNames: [
    ...defaultSchema.tagNames,
    "article",
    "figcaption",
    "figure",
    "video",
    "svg",
    "path",
    "line",
    "polyline",
    "rect",
    "circle",
    "polygon",
  ],
  attributes: {
    ...defaultSchema.attributes,
    div: [
      ...(defaultSchema.attributes.div ?? []),
      ["className", ...calloutClasses, ...responsiveTableClasses],
      ["dataCallout", /^[a-z0-9-]{1,32}$/],
      ["dataCollapsible", "true", "false"],
      ["ariaHidden", "true", "false"],
    ],
    details: [
      ...(defaultSchema.attributes.details ?? []),
      ["className", "callout"],
      ["dataCallout", /^[a-z0-9-]{1,32}$/],
      ["dataCollapsible", "true", "false"],
    ],
    summary: [
      ...(defaultSchema.attributes.summary ?? []),
      ["className", "callout-title"],
    ],
    pre: [
      ...(defaultSchema.attributes.pre ?? []),
      ["className", ...shikiPreClasses],
      ["dataLanguage", /^[a-z0-9+#.-]{1,32}$/i],
      "style",
    ],
    span: [
      ...(defaultSchema.attributes.span ?? []),
      ["className", ...shikiSpanClasses],
      "style",
    ],
    figure: [
      ...(defaultSchema.attributes.figure ?? []),
      ["className", "border", "border-skin-line"],
    ],
    figcaption: [
      ...(defaultSchema.attributes.figcaption ?? []),
      ["className", "text-center"],
    ],
    video: [
      "autoPlay",
      "controls",
      "loop",
      "muted",
      "playsInline",
      "poster",
      "src",
      ["className", "border", "border-skin-line"],
    ],
    source: [...(defaultSchema.attributes.source ?? []), "src", "type"],
    svg: [
      "ariaHidden",
      "fill",
      "height",
      "stroke",
      "strokeLineCap",
      "strokeLineJoin",
      "strokeWidth",
      "viewBox",
      "width",
      "xmlns",
    ],
    path: ["d"],
    line: ["x1", "x2", "y1", "y2"],
    polyline: ["points"],
    polygon: ["points"],
    rect: ["height", "rx", "ry", "width", "x", "y"],
    circle: ["cx", "cy", "r"],
    "*": [...(defaultSchema.attributes["*"] ?? []), "ariaHidden"],
  },
  protocols: {
    ...defaultSchema.protocols,
    poster: ["http", "https"],
  },
};

function safeShikiStyle(style) {
  const safeDeclarations = [];
  for (const declaration of String(style).split(";")) {
    const separator = declaration.indexOf(":");
    if (separator < 1) continue;

    const property = declaration.slice(0, separator).trim().toLowerCase();
    const value = declaration.slice(separator + 1).trim();
    const isShikiColor = [
      "--shiki-light",
      "--shiki-dark",
      "--shiki-light-bg",
      "--shiki-dark-bg",
    ].includes(property);
    const isShikiFontStyle =
      property === "--shiki-light-font-style" ||
      property === "--shiki-dark-font-style";
    const isFileNameOffset = property === "--file-name-offset";
    const isCodeOverflow = property === "overflow-x";

    if (isShikiColor && /^#[\da-f]{6}(?:[\da-f]{2})?$/i.test(value)) {
      safeDeclarations.push(property + ":" + value);
    } else if (
      isShikiFontStyle &&
      ["inherit", "normal", "italic", "oblique"].includes(value.toLowerCase())
    ) {
      safeDeclarations.push(property + ":" + value.toLowerCase());
    } else if (isFileNameOffset && /^-?0\.75rem$/.test(value)) {
      safeDeclarations.push(property + ":" + value);
    } else if (isCodeOverflow && value === "auto") {
      safeDeclarations.push(property + ":" + value);
    }
  }
  return safeDeclarations.join(";");
}

function sanitizeStyles(node) {
  if (node.type === "element" && Object.hasOwn(node.properties, "style")) {
    const style =
      node.tagName === "pre" || node.tagName === "span"
        ? safeShikiStyle(node.properties.style)
        : "";

    if (style) node.properties.style = style;
    else delete node.properties.style;
  }

  if (node.children) {
    for (const child of node.children) sanitizeStyles(child);
  }
}

export function rehypeSanitizeMarkdown() {
  const parseRawHtml = rehypeRaw();
  const sanitizeTree = rehypeSanitize(schema);

  return function sanitizeMarkdownTree(tree, file) {
    const parsedTree = parseRawHtml(tree, file) ?? tree;
    sanitizeStyles(parsedTree);
    return sanitizeTree(parsedTree, file);
  };
}
