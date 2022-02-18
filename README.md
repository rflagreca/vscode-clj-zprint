# vscode-clj-zprint

A VS Code wrapper for [clj-zprint](https://github.com/kkinnear/zprint) written in `cljs`, built with [Shadow CLJS](http://shadow-cljs.org/).

## Configuration

### External Configuration (`.zprintrc` and `.zprint.edn`)

By default, the extension will configure zprint from the normal
zprint configuration files, allowing you to use zprint within vscode
and zprint outside of vscode with the same zprint configuration.

You can configure the extension to ignore the external zprint
configuration files, see the next section for how to do this.

If you don't configure the extension to ignore the zprint configuration
files, then configuration follows zprint as closely as possible.
First, we look for a `.zprintrc` or `.zprint.edn` in the user's
home directory.  Either will be used, if found.  If neither are
found, configuration is complete.  If either are found, configuration
continues if there is a current workspace.  If there is not a current
workspace, then an error is shown in a popup and logged in the
`vscode-clj-zprint Messages` OUTPUT channel.

If `:search-config?` is true then we will look in the current
workspace for either a `.zprintrc` or `.zprint.edn`.  If we don't
find either, we will look in the parent of the current workspace
for either file.  This continues until we find a file with either
name, encounter the original configuration file in the user's home
directory, or run out of directories.

If `:search-config?` is not true but `:cwd-zprintrc?` is true,
we will look for `.zprintrc` or `.zprint.edn` in the current workspace
and use either if found.

We will only ever use one additional file beyond the file in the 
user's home directory.

Note that the files used will be listed in the extensions OUTPUT
channel: `vscode-clj-zprint Messages`.

Once the external configuration is completed successfully, any changes
to the external configuration files will not be noticed by the
extension until either the VSCode configuration changes or the
command `Zprint: Refresh Configuration` is executed.  See `Commands`
below for details.

If there are any configuration errors in the external files, the
entire configuration process (including reading any external files)
will be repeated on every use of the extension until the errors
are fixed.

### VSCode Configuration

There are a number of configuration options within VSCode to allow
you to configure zprint.  You can use these options to extend the
zprint configuration found in the external configuration files (if
any), or you can explicity configure the extension to ignore any
zprint external configuration files.  Then all of zprint's configuration
will be held in the VSCode Configuration for this extension.

The VSCode configuration is available in the VSCode `Settings`, under 
`Extensions`>`Zprint`

Every time the extension is used, it will check the VSCode configuration
and if it has changed, the extension will erase any current zprint
configuration, load any external configuration files (unless configured
to not do so), and then load the current VSCode configuration.
There is no need to use the command `Zprint: Refresh Configuration`
after changing the VSCode zprint configuration -- any changes are
detected automatically.

#### Ignore External Files

If you check this box, the extension will not examine any zprint
configuration files -- it will not read any `.zprintrc` or
`.zprint.edn` files.  Default: _unchecked_

#### Community Formatting

If you check this box, the zprint style `:community` will be configured
after any styles specified in the external files and before any styles
configured in the VSCode configuration.  This will configure zprint
to use the "Community Formatting" guidelines for Clojure.
Default: _unchecked_

#### Options Map 

A (potentially multi-line) string field into which you can enter 
a zprint options map in EDN format.  Any valid zprint options map will
be acceptable.  It will be applied after the option maps resulting from
any external configuration files, and after all other options from the
VSCode zprint configuration.

#### Styles: Array Of Styles

An array of strings, where each string must be a keyword that references
a zprint style.  You could reference a style defined in the `Options Map`,
above, since both of these configuration elements are placed into a single
options map.

#### Styles: Use Only These Styles

If you want the `Array Of Styles` to be the only styles used, then
you should check this box.  Any styles configured using the `:style`
key in the zprint external configuration files (if any) are ignored,
as are any styles configured using the `:style` key in the `Options Map` 
VSCode configuration as well as the `Community Formatting`
checkbox. Default: _unchecked_

#### Width

This will set the width of the zprint formatting within VSCode.
A null string, the default, will be ignored.  Any positive value
will be used as the width of the formatting.  A `:width` configured
in the `Options Map` will override a width configured here.

### Error Handling

There are (at most) three distinct groups of configuration options 
that can configure zprint.  Two possible external configuration
files and the internal VSCode configuration.

In each case, there are two points where errors can appear.  The
configuration options themselves might be incorrectly formatted or
otherwise be syntactically incorrect, or they might be rejected by
zprint because they didn't pass validation successfully.

If any of the three groups of configuration options has either of
these failures, that set of options will not be loaded into zprint
but the other two groups will nevertheless be configured (only if,
of course, they had no problems themselves).

All errors will be signalled in two ways -- a popup will appear and
the equivalent information will also be logged in the 
`vscode-clj-zprint Messages` channel of OUTPUT.

Any errors will cause the configuration process to retried
from scratch at the next use of the extension.  This will continue
until the errors go away.  Thus, if there are errors in one
of the `.zprintrc` or `.zprint.edn` files, they will continue
to be read on each use of the extension until the errors
are fixed, at which time they will be read once more and then
not examined again until a VSCode configuration element changes
or the `Zprint: Refresh Configuration` command is executed.

### Commands

The following commands are available in VSCode in the `Command Pallet`.
Note that these commands are not available until a Clojure or Clojurescript
file is selected.

#### Zprint: Refresh Configuration

This command will cause the extension to re-read any external 
configuration.  You would trigger this command whenever you might
change any `.zprintrc` or `.zprint.edn` external configuration
files.

#### Zprint: Output Current non-Default Configuration

This command will cause all zprint configuration options which are
not currently set to their default vales to be output to the OUTPUT
on the channel `vscode-clj-zprint Messages`.

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

There is an channel in the OUTPUT for this extension: `vscode-clj-zprint
Messages`.  It is not necessary to examine it, but if you are
wondering about the operation of the extension, you might find it
useful.  If you submit an issue regarding this extension, including
information from this channel would probably be helpful.

## Disclaimer

Please note that this software is still very much in alpha stages of development.  I do not accept liability for any loss or damage caused by any party as a result of the use of this software.


## Acknowledgements

[Pedro Girardi](https://github.com/pedrorgirardi), who's work on the extension [vscode-cljfmt](https://github.com/pedrorgirardi/vscode-cljfmt) formed the basis of this extension.