export function rehypeResponsiveTables() {
  return tree => {
    const wrapTables = parent => {
      if (!parent.children) return;

      const children = [];
      for (const child of parent.children) {
        wrapTables(child);
        if (child.type !== "element" || child.tagName !== "table") {
          children.push(child);
          continue;
        }

        children.push({
          type: "element",
          tagName: "div",
          properties: {
            className: [
              "overflow-hidden",
              "[&_table]:my-0",
              "[&_table]:min-w-xl",
            ],
          },
          children: [
            {
              type: "element",
              tagName: "div",
              properties: {
                className: ["relative", "w-full", "overflow-x-auto"],
              },
              children: [child],
            },
          ],
        });
      }
      parent.children = children;
    };

    wrapTables(tree);
  };
}
