# Change Log

## v0.0.10

- Bump zprint to v1.3.0. Note that zprint changed two formatting defaults
  (`:wrap-multi?` is now false, and tagged-literal `:indent` changed from
  0 to 1), so some output may differ from v0.0.9.
- Fix: formatting now uses the document being formatted rather than the
  active editor, which fixes format-on-save of non-focused documents.
- Fix: settings are read via the documented `WorkspaceConfiguration.get`
  API, and the `Array Of Styles` setting is now handled as the array its
  schema declares (a comma-separated string is still accepted).
- Security: the extension is disabled in untrusted workspaces, since it
  evaluates Clojure from workspace `.zprintrc`/`.zprint.edn` files.
- Packaging: the extension no longer ships development caches, shrinking
  the install from 125MB to under 5MB.
- Tooling: shadow-cljs 2.28, `@vscode/vsce` as a dev dependency, removed
  unused TypeScript/ESLint/test scaffolding, added GitHub Actions CI.
- Now requires VS Code 1.75 or later.

## v0.0.9

- Bump zprint to v1.2.9

## v0.0.8

- Bump zprint to v1.2.7

## v0.0.7

- Bump zprint to 1.2.5

## v0.0.6

- Rolls back zprint due to startup issues

## v0.0.5

- Bump zprint to 1.2.4

## v0.0.4

- Update `README.md` and `package.json` description

## v0.0.3

- Follows zprint interpretation of `.zprintrc` and `.zprint.edn` files as closely as possible.
- Automatically expands any selection to encompass "top-level" expressions.
- Can configure extension directly in VSCode -- doesn't require external files.

## v0.0.2

## v0.0.1

- Initial release
