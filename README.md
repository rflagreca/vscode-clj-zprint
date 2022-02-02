# vscode-clj-zprint

A VS Code wrapper for [clj-zprint](https://github.com/kkinnear/zprint) written in `cljs`, built with [Shadow CLJS](http://shadow-cljs.org/).


## Configuration

Configuration follows `zprint` as closely as possible.  First, we look
for a `.zprintrc` or `.zprint.edn` in the user's home directory.
Either will be used, if found.  If neither are found, configuration
is complete.  If either are found, configuration continues.

If `:search-config?` is true then we will look in the current
workspace for either a `.zprintrc` or `.zprint.edn`.  If we don't
find either, we will look in the parent of the current workspace
for either file.  This continues until we find a file with either
name, encounter the original configuration file in the user's home
directory, or run out of directories.

If `:search-config?` was not true but `:cwd-zprintrc?` was true,
we will look for `.zprintrc` or `.zprint.edn` in the current workspace
and use either if found.

We will only every use one additional file beyond the file in the 
user's home directory.

Note that the files used will be listed in the extensions OUTPUT
channel: "vscode-clj-zprint Messages".

## Operation

`zprint` can only successfully format "top level" expressions.  If
you choose to format an entire file that isn't an issue.  To format
less than an entire file, you can select any amount of code and
when you format that selection, `zprint` will expand the range to
encompass the minimal top level expression or expressions included 
in the selection and format those.

If you configure `vscode-clj-zprint` as the default formatter, you
can simply hit the key-code CMD-K CMD-F (on Macos) and it will
format the expression in which the cursor currently resides without
you having to explicitly select anything.

## Communication

There is an channel in the OUTPUT for this extension: "vscode-clj-zprint
Messages".  It is not necessary to examine it, but if you are
wondering about the operation of the extension, you might find it
useful.  If you submit an issue regarding this extension, including
information from this channel would probably be helpful.

## Disclaimer

Please note that this software is still very much in alpha stages of development.  I do not accept liability for any loss or damage caused by any party as a result of the use of this software.


## Acknowledgements

[Pedro Girardi](https://github.com/pedrorgirardi), who's work on the extension [vscode-cljfmt](https://github.com/pedrorgirardi/vscode-cljfmt) formed the basis of this extension.