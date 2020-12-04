# vscode-clj-zprint

A VS Code wrapper for [clj-zprint](https://github.com/kkinnear/zprint) written in `cljs`, built with [Shadow CLJS](http://shadow-cljs.org/).

## Configuration

First, we look for a `.zprintrc` file in the current workspace and uses that for configuration.  If not found, then we look in the user's home directory.  Finally, `zprint` defaults will be used if no configuration file is found.  Granular, in-editor configuration to be considered for future versions.

## Disclaimer

Please note that this software is still very much in alpha stages of development.  I do not accept liability for any loss or damage caused by any party as a result of the use of this software.


## Acknowledgements

[Pedro Girardi](https://github.com/pedrorgirardi), who's work on the extension [vscode-cljfmt](https://github.com/pedrorgirardi/vscode-cljfmt) formed the basis of this extension.