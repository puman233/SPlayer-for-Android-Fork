import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import ts from "typescript";

const files = (directory: string): string[] => readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
  const path = join(directory, entry.name);
  return entry.isDirectory() ? files(path) : /\.(ts|vue|js)$/.test(path) && !/\.(test|d)\./.test(path) ? [path] : [];
});

test("HTTP catch consumers cannot send their raw error or message to Console/UI", () => {
  const findings: string[] = [];
  for (const path of [...files("src"), ...files("API")]) {
    const raw = readFileSync(path, "utf8");
    const source = path.endsWith(".vue") ? /<script[^>]*>([\s\S]*?)<\/script>/.exec(raw)?.[1] ?? "" : raw;
    const ast = ts.createSourceFile(path, source, ts.ScriptTarget.Latest, true);
    const httpNames = new Set(["fetch", "promiseFunc"]);
    for (const statement of ast.statements) {
      if (!ts.isImportDeclaration(statement) || !/\/(api\/|utils\/request["']?$)/.test(statement.moduleSpecifier.getText(ast))) continue;
      const clause = statement.importClause;
      if (clause?.name) httpNames.add(clause.name.text);
      if (clause?.namedBindings && ts.isNamedImports(clause.namedBindings)) {
        clause.namedBindings.elements.forEach(element => httpNames.add(element.name.text));
      }
    }
    const visit = (node: ts.Node) => {
      if (ts.isTryStatement(node) && node.catchClause?.variableDeclaration) {
        const binding = node.catchClause.variableDeclaration.name.getText(ast);
        let http = false;
        const inspect = (child: ts.Node) => {
          if (ts.isCallExpression(child) && (httpNames.has(child.expression.getText(ast)) || /(?:axios|server|client)\.(?:get|post|request)$/.test(child.expression.getText(ast)))) http = true;
          ts.forEachChild(child, inspect);
        };
        inspect(node.tryBlock);
        if (http) {
          const sinks = (child: ts.Node) => {
            if (ts.isCallExpression(child) && /^(?:console\.(?:error|warn|info|log|debug)|window\.\$message\.(?:error|warning))$/.test(child.expression.getText(ast))) {
              for (const arg of child.arguments) {
                let unsafe = false;
                const identifiers = (value: ts.Node) => {
                  if (ts.isCallExpression(value) && value.expression.getText(ast) === "requestFailureCategory") return;
                  if (ts.isIdentifier(value) && value.text === binding) unsafe = true;
                  ts.forEachChild(value, identifiers);
                };
                identifiers(arg);
                if (unsafe) findings.push(`${path}: ${child.getText(ast)}`);
              }
            }
            // Another catch owns its own error and is checked separately.
            if (!ts.isCatchClause(child)) ts.forEachChild(child, sinks);
          };
          ts.forEachChild(node.catchClause.block, sinks);
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(ast);
  }
  assert.deepEqual(findings, [], "Review HTTP data flow before accepting any exception to this guard");
});

test("audio source diagnostics do not include authenticated URLs or response objects", () => {
  for (const path of ["src/core/player/SongManager.ts", ...files("API/unblock")]) {
    const ast = ts.createSourceFile(path, readFileSync(path, "utf8"), ts.ScriptTarget.Latest, true);
    const unsafe: string[] = [];
    const visit = (node: ts.Node) => {
      if (ts.isCallExpression(node) && /^console\./.test(node.expression.getText(ast)) && node.arguments.some(arg => /\b(?:finalUrl|unlockUrl|songUrl|urlMatch|fallbackUrl|res)\b|(?:result|data\.data)\.(?:url|data)/.test(arg.getText(ast)))) unsafe.push(node.getText(ast));
      ts.forEachChild(node, visit);
    };
    visit(ast); assert.deepEqual(unsafe, [], path);
  }
});

test("native HTTP diagnostics exclude Throwable, reflected body and transport message", () => {
  const root = "android/app/src/main/java/top/imsyy/splayer/android";
  const resolver = readFileSync(join(root, "playback/PlaybackUrlResolver.java"), "utf8");
  assert.doesNotMatch(resolver, /Log\.w\([^;]+, e\);/);
  const manager = readFileSync(join(root, "playback/PlaybackManager.java"), "utf8");
  for (const label of ["Failed to toggle song favorite", "Failed to parse favorite response", "Failed to load cover art"]) {
    assert.doesNotMatch(manager, new RegExp(`Log\\.w\\([^;]+${label}[^;]+, error\\);`));
  }
  assert.doesNotMatch(manager, /\+ ", response="\s*\+ response/);
  assert.doesNotMatch(readFileSync(join(root, "cache/AudioCacheProvider.java"), "utf8"), /"prefetch aborted:[^;]+e\.getMessage\(\)/);
});

test("API dependency diagnostic boundary loads before request implementation and SDK tracing stays off", () => {
  for (const path of ["API/mobile-entry.ts", "API/server.ts"]) {
    assert.match(readFileSync(path, "utf8"), /^import "\.\/runtimeDiagnostics";/);
  }
  assert.match(readFileSync("capacitor.config.ts", "utf8"), /loggingBehavior: "none"/);
  assert.match(readFileSync("android/app/src/main/java/top/imsyy/splayer/android/MainActivity.java", "utf8"), /new ApplicationConsoleClient\(bridge\)/);
});
