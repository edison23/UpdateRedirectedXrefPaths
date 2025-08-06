# Update redirected xref paths in AsciiDoc projects

<img src="dokuro-chan.png" align="right" width="130px" />

This is Dokuro-chan, a tool that mercilessly bludgeons obsolete redirected paths (URI) from `xref` macros and replaces them with up-to-date paths.

Dokuro depends on the following:
- Redirects are specified using `:page-moved-from: /some/old/path` in the files that are targets of the redirect.
- The physical paths to files on the file system correspond to the URI used in the `xref` macros.
- The filter for `.adoc` extension is hard-coded. You can change it in the `fileExtension` variable.

## How it works

1. You give Dokuro at least one parameter, that is the path to the root of your AsciiDoc project.
2. Dokuro lists recursively all nodes (files and directories) under that root path, and 
3. filters out those the name of which ends with `.adoc`.
4. She searches every found file for redirects (`:page-moved-from:`) (needs to be on the beginning of line, unintended).
5. For every redirect found, all files found in step 2 & 3 are searched for `xref` macros that contain the path from the redirect. If found, Dokuro rewrites the path so that it points to the file containing the redirect, thus bypassing the redirect. The new `xref` paths (URIs) are constructed from the file system location of the file containing the redirect.

Files that don't contain any `xref` that needs rewriting are not touched.
Files that are touched, inevitably _will_ contain new line character on the end of each line.
This can result in one more change at the end of the file if there previously was no NL character.

## Available parameters and how to use them


| parameter | example value | description | required? |
|---|---|---|---|
| ‑‑root | /home/dokuro/projects/docs/guides | Specify where are the files to fix. This part of the path to files is omitted from the new `xref` paths. | yes |
| ‑‑auxroot | /home/dokuro/projects/other-docs/ | Specify if your portal is split into two distinct yet interlinked file trees. Files under this auxiliary root path are scanned for `xref` paths to be fixed, not for redirects. `-root` and `‑‑auxroot` are not commutative. See the section on multiple roots below for details. | no |
| ‑‑root-prefix | /home/dokuro/projects/docs/ | Specify if you run Dokuro only for portion of the whole tree, meaning the root to which `xref` paths must be relative is higher in the file system than what you have in `--root`. For example, your actual root is in `/foo/bar` but you run Dokuro only for `/foo/bar/lorem/`, then your parameters are `--root /foo/bar/lorem/ --root-prefix /foo/bar/`. | no |
| ‑‑reference | true | Set if you need to prefix the physical paths to files with an arbitrary string. See the section on extra string in URLs below. No need to use `‑‑reference false`; just leave out the argument altogether. | no |
| ‑‑help |  | Print this list of parameters. | no |




## Multiple roots or extra string in URLs? Dokuro has you covered!

If your project spans multiple Git repositories, for example, which are then combined into one web portal, `xref` macros can point from one directory tree to another.
If this is your case, use the `--auxroot` parameter to specify the root of the second tree.

The auxiliary root node is recursively scanned for files the same as the primary root, with the exception that Dokuro does _not_ extract redirects from it. She only uses it to check if it contains `xref` macros with paths that have been redirected (they are in `:page-moved-from:` in some file under the primary root). This means that you need to run Dokuro against both trees and switch the primary and auxiliary root to rewrite redirected URIs in both trees.

If URLs in your project contain some extra string (e.g., `mytool/reference`) that's not part of the directory structure (because it sits in a separate repository from which the reference is built, for example), you can use the `--reference true` option. With this optional argument, Dokuro prepends the physical paths to files with the preset string when rewriting the obsolete redirected `xref` paths. You can customize the string to use in the code. Currently, it is set to `/midpoint/reference`. You can change it in the `extraPath` variable.