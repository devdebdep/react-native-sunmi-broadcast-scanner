# Publish Guide

This document captures the steps required to publish
`@devdebdep/react-native-sunmi-broadcast-scanner` later from another machine.

## 1. Prepare The Repo

Make sure the working tree is clean:

```sh
git status
```

If the `NOTICE` file is still uncommitted, add and push it first.

## 2. Use Node 18

This repository expects Node 18.

```sh
export NVM_DIR="$HOME/.nvm"
. "$NVM_DIR/nvm.sh"
nvm use 18
```

## 3. Install And Verify

Run these commands from the repository root:

```sh
corepack yarn install
corepack yarn typecheck
corepack yarn lint
corepack yarn prepare
```

`yarn prepare` builds the package output in `lib/`, which is required before publish.

## 4. Confirm Package Metadata

Check [package.json](./package.json) and confirm:

- `name` is `@devdebdep/react-native-sunmi-broadcast-scanner`
- `version` is the version you want to publish
- `repository`, `bugs`, and `homepage` point to the `devdebdep` fork
- `license` is `MIT`

If the version was already published before, bump it before continuing.

## 5. Log In To npm

Check current npm auth:

```sh
npm whoami
```

If needed, log in:

```sh
npm login
```

## 6. Publish

Publish from the repository root:

```sh
npm publish --access public
```

Because this is a scoped package, `--access public` is important for the first publish.

## 7. Verify Publish

Check that the package is live:

```sh
npm view @devdebdep/react-native-sunmi-broadcast-scanner version
```

Optionally verify install in another project:

```sh
npm install @devdebdep/react-native-sunmi-broadcast-scanner
```

## Notes

- If npm rejects the publish because the version already exists, bump the version in `package.json` and try again.
- If you publish from another machine, make sure Node 18 and Yarn/Corepack are available there too.
- Run `corepack yarn prepare` again after any source change before publishing a new version.
