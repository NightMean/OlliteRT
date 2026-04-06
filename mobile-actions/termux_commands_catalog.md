# Termux API catalog

Total commands: 89

## termux-am
- Path: `/data/data/com.termux/files/usr/bin/termux-am`
- Help exit code: 0
```text
termux-am is a wrapper script that converts the arguments array
passed to a single string that can be reused as shell input with the
bash 'printf "%q"' built-in and passes the string to termux-am-socket.


Usage:
  termux-am [command_options]


Available command_options:
  [ -h | --help ]    Display termux-am help screen.
  [ --am-help ]      Display am command help screen.
  [ --version ]      Display version.


termux-am-socket sends the converted string to
"/data/data/com.termux/files/apps/com.termux/termux-am/am.sock"
local unix socket server that is run by termux-app if enabled, which
executes it as an android activity manager (am) command from within
termux-app java process via termux/termux-am-library.

The termux-am provides similar functionality to "$PREFIX/bin/am"
(and "/system/bin/am"), but should be faster since it does not
require starting a dalvik vm for every command as done by "am" via
termux/TermuxAm.

The server normally only allows termux-app user and root user to
connect to it. If you run termux-am with root, then the am commands
executed will be run as the termux user and its permissions,
capabilities and selinux context instead of root.

The server is enabled 
...
```

## termux-am-socket
- Path: `/data/data/com.termux/files/usr/bin/termux-am-socket`
- Help exit code: 1
```text
Could not connect to socket: No such file or directory
```

## termux-api-start
- Path: `/data/data/com.termux/files/usr/bin/termux-api-start`
- Help exit code: 0
```text
Starting service: Intent { cmp=com.termux.api/.KeepAliveService }
```

## termux-api-stop
- Path: `/data/data/com.termux/files/usr/bin/termux-api-stop`
- Help exit code: 0
```text
Stopping service: Intent { cmp=com.termux.api/.KeepAliveService }
Service stopped
```

## termux-apps-info-app-version-name
- Path: `/data/data/com.termux/files/usr/bin/termux-apps-info-app-version-name`
- Help exit code: 0
```text
termux-apps-info-app-version-name.bash can be used to get/unset
variable values of the app version name environment variables
`*_APP__APP_VERSION_NAME` for Termux app `TERMUX_APP__`, its plugin
apps `TERMUX_*_APP__` and external apps `*_APP__` app scoped that
exist in the `termux-apps-info.env` file, with support for validation
of values.


Usage:
  termux-apps-info-app-version-name.bash <command> <args...>


Available commands:
    get-value                    Get app version name value from
                                 Termux scoped variable.
    unset-value                  Unset Termux scoped variable value
                                 for app version name.



get-value:
  termux-apps-info-app-version-name.bash get-value [<command_options>] \
    <output_mode> \
    <scoped_var_scope_mode>

Available command options:
  [ --skip-sourcing ]
                     Skip sourcing of `termux-apps-info.env` file
                     before getting value. By default, the
                     '--skip-sourcing-if-cur-app-var' flag is passed
                     to 'termux-apps-info-env-variable.bash' instead.
  [ --extended-validator=<validator> ]
                     The extended 
...
```

## termux-apps-info-app-version-name.bash
- Path: `/data/data/com.termux/files/usr/bin/termux-apps-info-app-version-name.bash`
- Help exit code: 0
```text
termux-apps-info-app-version-name.bash can be used to get/unset
variable values of the app version name environment variables
`*_APP__APP_VERSION_NAME` for Termux app `TERMUX_APP__`, its plugin
apps `TERMUX_*_APP__` and external apps `*_APP__` app scoped that
exist in the `termux-apps-info.env` file, with support for validation
of values.


Usage:
  termux-apps-info-app-version-name.bash <command> <args...>


Available commands:
    get-value                    Get app version name value from
                                 Termux scoped variable.
    unset-value                  Unset Termux scoped variable value
                                 for app version name.



get-value:
  termux-apps-info-app-version-name.bash get-value [<command_options>] \
    <output_mode> \
    <scoped_var_scope_mode>

Available command options:
  [ --skip-sourcing ]
                     Skip sourcing of `termux-apps-info.env` file
                     before getting value. By default, the
                     '--skip-sourcing-if-cur-app-var' flag is passed
                     to 'termux-apps-info-env-variable.bash' instead.
  [ --extended-validator=<validator> ]
                     The extended 
...
```

## termux-apps-info-app-version-name.sh
- Path: `/data/data/com.termux/files/usr/bin/termux-apps-info-app-version-name.sh`
- Help exit code: 0
```text
termux-apps-info-app-version-name.sh can be used to get/unset
variable values of the app version name environment variables
`*_APP__APP_VERSION_NAME` for Termux app `TERMUX_APP__`, its plugin
apps `TERMUX_*_APP__` and external apps `*_APP__` app scoped that
exist in the `termux-apps-info.env` file, with support for validation
of values.


Usage:
  termux-apps-info-app-version-name.sh <command> <args...>


Available commands:
    get-value                    Get app version name value from
                                 Termux scoped variable.
    unset-value                  Unset Termux scoped variable value
                                 for app version name.



get-value:
  termux-apps-info-app-version-name.sh get-value [<command_options>] \
    <output_mode> \
    <scoped_var_scope_mode>

Available command options:
  [ --skip-sourcing ]
                     Skip sourcing of `termux-apps-info.env` file
                     before getting value. By default, the
                     '--skip-sourcing-if-cur-app-var' flag is passed
                     to 'termux-apps-info-env-variable.sh' instead.
  [ --posix-validator=<validator> ]
                     The posix validator to p
...
```

## termux-apps-info-env-variable
- Path: `/data/data/com.termux/files/usr/bin/termux-apps-info-env-variable`
- Help exit code: 0
```text
termux-apps-info-env-variable.bash can be used to source the
`termux-apps-info.env` file into the current environment or get
variable values of Termux app `TERMUX_APP__`, its plugin apps
`TERMUX_*_APP__` and external apps `*_APP__` app scoped environment
variables that exist in the `termux-apps-info.env` file, with
support for fallback values and validation of values.


Usage:
  termux-apps-info-env-variable.bash <command> <args...>


Available commands:
    source-env                   Source the apps info environment.
    get-value                    Get Termux scoped variable value.



source-env:
  termux-apps-info-env-variable.bash source-env \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name>



get-value:
  termux-apps-info-env-variable.bash get-value [<command_options>] \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name> \
    <extended_validator> [<default_values...>]

Available command options:
  [ --ensure-sourcing ]
                     Ensure sourcing of `termux-apps-info.env` file
                     before getting value.
  [ --skip-sourcing ]
                     Skip sourcing of `termux-apps-info.env` file
                   
...
```

## termux-apps-info-env-variable.bash
- Path: `/data/data/com.termux/files/usr/bin/termux-apps-info-env-variable.bash`
- Help exit code: 0
```text
termux-apps-info-env-variable.bash can be used to source the
`termux-apps-info.env` file into the current environment or get
variable values of Termux app `TERMUX_APP__`, its plugin apps
`TERMUX_*_APP__` and external apps `*_APP__` app scoped environment
variables that exist in the `termux-apps-info.env` file, with
support for fallback values and validation of values.


Usage:
  termux-apps-info-env-variable.bash <command> <args...>


Available commands:
    source-env                   Source the apps info environment.
    get-value                    Get Termux scoped variable value.



source-env:
  termux-apps-info-env-variable.bash source-env \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name>



get-value:
  termux-apps-info-env-variable.bash get-value [<command_options>] \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name> \
    <extended_validator> [<default_values...>]

Available command options:
  [ --ensure-sourcing ]
                     Ensure sourcing of `termux-apps-info.env` file
                     before getting value.
  [ --skip-sourcing ]
                     Skip sourcing of `termux-apps-info.env` file
                   
...
```

## termux-apps-info-env-variable.sh
- Path: `/data/data/com.termux/files/usr/bin/termux-apps-info-env-variable.sh`
- Help exit code: 0
```text
termux-apps-info-env-variable.sh can be used to source the
`termux-apps-info.env` file into the current environment or get
variable values of Termux app `TERMUX_APP__`, its plugin apps
`TERMUX_*_APP__` and external apps `*_APP__` app scoped environment
variables that exist in the `termux-apps-info.env` file, with
support for fallback values and validation of values.


Usage:
  termux-apps-info-env-variable.sh <command> <args...>


Available commands:
    source-env                   Source the apps info environment.
    get-value                    Get Termux scoped variable value.



source-env:
  termux-apps-info-env-variable.sh source-env \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name>



get-value:
  termux-apps-info-env-variable.sh get-value [<command_options>] \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name> \
    <posix_validator> [<default_values...>]

Available command options:
  [ --ensure-sourcing ]
                     Ensure sourcing of `termux-apps-info.env` file
                     before getting value.
  [ --skip-sourcing ]
                     Skip sourcing of `termux-apps-info.env` file
                     before ge
...
```

## termux-audio-info
- Path: `/data/data/com.termux/files/usr/bin/termux-audio-info`
- Help exit code: 1
```text
termux-audio-info: illegal option --
```

## termux-backup
- Path: `/data/data/com.termux/files/usr/bin/termux-backup`
- Help exit code: 0
```text
Usage: termux-backup [options] [output file]

Script for backing up Termux installation directory, $PREFIX.
It WILL NOT backup your home directory.

Options:

 -h, --help             Show this information.
 -f, --force            Force write operations.
 --ignore-read-failure  Suppress read permission denials.

Backup is performed as TAR archive. Compression is determined
by output file extension. If file name is '-', then tarball is
written to stdout and is uncompressed.
```

## termux-battery-status
- Path: `/data/data/com.termux/files/usr/bin/termux-battery-status`
- Help exit code: 1
```text
termux-battery-status: illegal option --
```

## termux-brightness
- Path: `/data/data/com.termux/files/usr/bin/termux-brightness`
- Help exit code: 0
```text
ERROR: Arg must be a number between 0 - 255 or auto!
Usage: termux-brightness brightness
Set the screen brightness between 0 and 255 or auto
```

## termux-call-log
- Path: `/data/data/com.termux/files/usr/bin/termux-call-log`
- Help exit code: 1
```text
termux-call-log: illegal option --
```

## termux-camera-info
- Path: `/data/data/com.termux/files/usr/bin/termux-camera-info`
- Help exit code: 1
```text
termux-camera-info: illegal option --
```

## termux-camera-photo
- Path: `/data/data/com.termux/files/usr/bin/termux-camera-photo`
- Help exit code: 1
```text
termux-camera-photo: illegal option --
```

## termux-change-repo
- Path: `/data/data/com.termux/files/usr/bin/termux-change-repo`
- Help exit code: 0
```text
Script for choosing a group of mirrors to use.
All mirrors are listed at
https://github.com/termux/termux-packages/wiki/Mirrors
```

## termux-chroot
- Path: `/data/data/com.termux/files/usr/bin/termux-chroot`
- Help exit code: 1
```text
termux-chroot: illegal option --
```

## termux-clipboard-get
- Path: `/data/data/com.termux/files/usr/bin/termux-clipboard-get`
- Help exit code: 1
```text
termux-clipboard-get: illegal option --
```

## termux-clipboard-set
- Path: `/data/data/com.termux/files/usr/bin/termux-clipboard-set`
- Help exit code: 1
```text
termux-clipboard-set: illegal option --
```

## termux-color
- Path: `/data/data/com.termux/files/usr/bin/termux-color`
- Help exit code: 1
```text
✗ This script must be run inside Termux
```

## termux-contact-list
- Path: `/data/data/com.termux/files/usr/bin/termux-contact-list`
- Help exit code: 1
```text
termux-contact-list: illegal option --
```

## termux-dialog
- Path: `/data/data/com.termux/files/usr/bin/termux-dialog`
- Help exit code: 1
```text
termux-dialog: illegal option --
```

## termux-download
- Path: `/data/data/com.termux/files/usr/bin/termux-download`
- Help exit code: 1
```text
termux-download: illegal option --
```

## termux-exec-ld-preload-lib
- Path: `/data/data/com.termux/files/usr/bin/termux-exec-ld-preload-lib`
- Help exit code: 0
```text
termux-exec-ld-preload-lib can be used to manage Termux '$LD_PRELOAD'
library.


Usage:
    termux-exec-ld-preload-lib [command_options] <command>

Available commands:
    setup                     Setup the primary Termux '$LD_PRELOAD'
                              library in 'libtermux-exec-ld-preload.so'
                              to direct or linker variant.

Available command_options:
    [ -h | --help ]           Display this help screen.
    [ --version ]             Display version.
    [ -q | --quiet ]          Set log level to 'OFF'.
    [ -v ]
                              Set log level to 'DEBUG'.
```

## termux-exec-system-linker-exec
- Path: `/data/data/com.termux/files/usr/bin/termux-exec-system-linker-exec`
- Help exit code: 0
```text
termux-exec-system-linker-exec can be used to get states of
'system_linker_exec' in Termux.


Usage:
    termux-exec-system-linker-exec [command_options] <command>

Available commands:
    is-enabled                Get whether 'system_linker_exec'
                              is enabled in Termux.

Available command_options:
    [ -h | --help ]           Display this help screen.
    [ --version ]             Display version.
    [ -q | --quiet ]          Set log level to 'OFF'.
    [ -v ]
                              Set log level to 'DEBUG'.
```

## termux-fastest-repo
- Path: `/data/data/com.termux/files/usr/bin/termux-fastest-repo`
- Help exit code: 1
```text
/data/data/com.termux/files/usr/bin/termux-fastest-repo: line 25: /bin/termux-setup-package-manager: No such file or directory
```

## termux-fingerprint
- Path: `/data/data/com.termux/files/usr/bin/termux-fingerprint`
- Help exit code: 1
```text
termux-fingerprint: illegal option --
```

## termux-fix-shebang
- Path: `/data/data/com.termux/files/usr/bin/termux-fix-shebang`
- Help exit code: 2
```text
sed: can't read Usage: realpath [OPTION]... FILE...
Print the resolved absolute file name;
all but the last component must exist

  -e, --canonicalize-existing  all components of the path must exist
  -m, --canonicalize-missing   no path components need exist or be a directory
  -L, --logical                resolve '..' components before symlinks
  -P, --physical               resolve symlinks as encountered (default)
  -q, --quiet                  suppress most error messages
      --relative-to=DIR        print the resolved path relative to DIR
      --relative-base=DIR      print absolute paths unless paths below DIR
  -s, --strip, --no-symlinks   don't expand symlinks
  -z, --zero                   end each output line with NUL, not newline
      --help        display this help and exit
      --version     output version information and exit

GNU coreutils online help: <https://www.gnu.org/software/coreutils/>
Report any translation bugs to <https://translationproject.org/team/>
Full documentation <https://www.gnu.org/software/coreutils/realpath>
or available locally via: info '(coreutils) realpath invocation': File name too long
```

## termux-info
- Path: `/data/data/com.termux/files/usr/bin/termux-info`
- Help exit code: 0
```text
usage: termux-info [--no-set-clipboard]
Provides information about Termux, and the current system. Helpful for debugging.
```

## termux-infrared-frequencies
- Path: `/data/data/com.termux/files/usr/bin/termux-infrared-frequencies`
- Help exit code: 1
```text
termux-infrared-frequencies: illegal option --
```

## termux-infrared-transmit
- Path: `/data/data/com.termux/files/usr/bin/termux-infrared-transmit`
- Help exit code: 1
```text
termux-infrared-transmit: illegal option --
```

## termux-job-scheduler
- Path: `/data/data/com.termux/files/usr/bin/termux-job-scheduler`
- Help exit code: 0
```text
Usage: termux-job-scheduler [options]
Schedule a script to run at specified intervals.
  -p/--pending               list pending jobs and exit
  --cancel-all               cancel all pending jobs and exit
  --cancel                   cancel given job-id and exit
Options for scheduling:
  -s/--script path           path to the script to be called
  --job-id int               job id (will overwrite any previous job with the same id)
  --period-ms int            schedule job approximately every period-ms milliseconds (default 0 means once).
                             Note that since Android N, the minimum period is 900,000ms (15 minutes).
  --network text             run only when this type of network available (default any): any|unmetered|cellular|not_roaming|none
  --battery-not-low boolean  run only when battery is not low, default true (at least Android O)
  --storage-not-low boolean  run only when storage is not low, default false (at least Android O)
  --charging boolean         run only when charging, default false
  --persisted boolean        should the job survive reboots, default false
  --trigger-content-uri text (at least Android N)
  --trigger-content-flag int default 1
...
```

## termux-keystore
- Path: `/data/data/com.termux/files/usr/bin/termux-keystore`
- Help exit code: 0
```text
Usage: termux-keystore command
These commands are supported:
  list [-d]
  delete <alias>
  generate <alias> [-a alg] [-s size] [-u validity]
  sign <alias> <algorithm>
  verify <alias> <algorithm> <signature>

list: List the keys stored inside the keystore.
  -d           Detailed results (includes key parameters).

delete: Permanently delete a given key from the keystore.
  alias        Alias of the key to delete.

generate: Create a new key inside the hardware keystore.
  alias        Alias of the key.
  -a alg       Algorithm to use (either 'RSA' or 'EC'). Defaults to RSA.
  -s size      Key size to use. For RSA, the options are 2048, 3072
               and 4096. For EC, the options are 256, 384 and 521.
  -u validity  User validity duration in seconds. Omit to disable.
               When enabled, the key can only be used for the
               duration specified after the device unlocks. After
               the duration has passed, the user needs to re-lock
               and unlock the device again to be able to use this key.

sign: Sign using the given key, the data is read from stdin and the
signature is output to stdout.
  alias        Alias of the key to use for signin
...
```

## termux-location
- Path: `/data/data/com.termux/files/usr/bin/termux-location`
- Help exit code: 1
```text
termux-location: illegal option --
```

## termux-media-player
- Path: `/data/data/com.termux/files/usr/bin/termux-media-player`
- Help exit code: 1
```text
termux-media-player: Invalid cmd: '--help'
Usage: termux-media-player cmd [args]

help        Shows this help
info        Displays current playback information
play        Resumes playback if paused
play <file> Plays specified media file
pause       Pauses playback
stop        Quits playback
```

## termux-media-scan
- Path: `/data/data/com.termux/files/usr/bin/termux-media-scan`
- Help exit code: 1
```text
termux-media-scan: illegal option --
```

## termux-microphone-record
- Path: `/data/data/com.termux/files/usr/bin/termux-microphone-record`
- Help exit code: 1
```text
/data/data/com.termux/files/usr/bin/termux-microphone-record: illegal option -- -

Usage: termux-microphone-record [args]
Record using microphone on your device

-h           Shows this help
-d           Start recording w/ defaults
-f <file>    Start recording to specific file
-l <limit>   Start recording w/ specified limit (in seconds, unlimited for 0)
-e <encoder> Start recording w/ specified encoder (aac, amr_wb, amr_nb, opus)
-b <bitrate> Start recording w/ specified bitrate (in kbps)
-r <rate>    Start recording w/ specified sampling rate (in Hz)
-c <count>   Start recording w/ specified channel count (1, 2, ...)
-i           Get info about current recording
-q           Quits recording
```

## termux-nf
- Path: `/data/data/com.termux/files/usr/bin/termux-nf`
- Help exit code: 124
```text
ERROR: Command '['/data/data/com.termux/files/usr/bin/termux-nf', '--help']' timed out after 3 seconds
```

## termux-nfc
- Path: `/data/data/com.termux/files/usr/bin/termux-nfc`
- Help exit code: 0
```text
Error: unknown parameters: ? -;
Usage: termux-nfc [-r [short|full]] [-w] [-t [text for TAG] 
 read/write data from/to NDEF tag 
   -r, read tag 
     short, read short information from tag  
     full,  read full information from tag 
   -w, write information on tag  
   -t, text  for tag
```

## termux-notification
- Path: `/data/data/com.termux/files/usr/bin/termux-notification`
- Help exit code: 0
```text
Usage: termux-notification [options]
Display a system notification. Content text is specified using -c/--content or read from stdin.
Please read --help-actions for help with action arguments.
  --action action          action to execute when pressing the notification
  --alert-once             do not alert when the notification is edited
  --button1 text           text to show on the first notification button
  --button1-action action  action to execute on the first notification button
  --button2 text           text to show on the second notification button
  --button2-action action  action to execute on the second notification button
  --button3 text           text to show on the third notification button
  --button3-action action  action to execute on the third notification button
  -c/--content content     content to show in the notification. Will take
                           precedence over stdin. If content is not passed as
                           an argument or with stdin, then there will be a 3s delay.
  --channel channel-id     Specifies the notification channel id this notification should be send on.
                           On Android versions lower than 8.0 this
...
```

## termux-notification-channel
- Path: `/data/data/com.termux/files/usr/bin/termux-notification-channel`
- Help exit code: 0
```text
Usage: termux-notification-channel -d channel-id
       termux-notification-channel channel-id channel-name
Create or delete a notification channel.
Only usable on Android 8.0 and higher.
Use -d to delete a channel.
Creating a channel requires a channel id and a channel name.
The name will be visible in the options, the id is used to send notifications on that specific channel.
Creating a channel with the same id again will change the name.
Creating a channel with the same id as a deleted channel will restore the user settings of the deleted channel.
Use termux-notification --channel channel-id to send a notification on a custom channel.
```

## termux-notification-list
- Path: `/data/data/com.termux/files/usr/bin/termux-notification-list`
- Help exit code: 1
```text
termux-notification-list: illegal option --
```

## termux-notification-remove
- Path: `/data/data/com.termux/files/usr/bin/termux-notification-remove`
- Help exit code: 1
```text
termux-notification-remove: illegal option --
```

## termux-open
- Path: `/data/data/com.termux/files/usr/bin/termux-open`
- Help exit code: 0
```text
Usage: termux-open [options] path-or-url
Open a file or URL in an external app.
  --send               if the file should be shared for sending
  --view               if the file should be shared for viewing (default)
  --chooser            if an app chooser should always be shown
  --content-type type  specify the content type to use
```

## termux-open-url
- Path: `/data/data/com.termux/files/usr/bin/termux-open-url`
- Help exit code: 1
```text
Error: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW dat= flg=0x10000000 }
```

## termux-reload-settings
- Path: `/data/data/com.termux/files/usr/bin/termux-reload-settings`
- Help exit code: 0
```text
usage: termux-reload-settings
Use without arguments to reload settings after modifying any of:
  ~/.termux/colors.properties
  ~/.termux/font.ttf 
  ~/.termux/termux.properties
```

## termux-reset
- Path: `/data/data/com.termux/files/usr/bin/termux-reset`
- Help exit code: 1
```text
You are going to reset your Termux installation.

This script will erase everything under $PREFIX. All files in that directory will be lost, that includes packages, configuration files, databases, etc.

Termux home directory as well as data in your shared or external storage will not be removed and left as-is.

Leaving $PREFIX intact.
```

## termux-restore
- Path: `/data/data/com.termux/files/usr/bin/termux-restore`
- Help exit code: 0
```text
Usage: termux-restore [input file]

Script for restoring Termux installation directory ($PREFIX)
from the given TAR archive.

It is expected that backup file was made by 'termux-backup'.
Restoring procedure will erase all files in $PREFIX directory
that are not present in the given backup file. Be careful.

Backup contents may be supplied via stdin by specifying input
file as '-'. Note that piped TAR archive must be uncompressed.
```

## termux-saf-create
- Path: `/data/data/com.termux/files/usr/bin/termux-saf-create`
- Help exit code: 1
```text
termux-saf-create: illegal option --
```

## termux-saf-dirs
- Path: `/data/data/com.termux/files/usr/bin/termux-saf-dirs`
- Help exit code: 1
```text
termux-saf-dirs: illegal option --
```

## termux-saf-ls
- Path: `/data/data/com.termux/files/usr/bin/termux-saf-ls`
- Help exit code: 1
```text
termux-saf-ls: illegal option --
```

## termux-saf-managedir
- Path: `/data/data/com.termux/files/usr/bin/termux-saf-managedir`
- Help exit code: 1
```text
termux-saf-managedir: illegal option --
```

## termux-saf-mkdir
- Path: `/data/data/com.termux/files/usr/bin/termux-saf-mkdir`
- Help exit code: 1
```text
termux-saf-mkdir: illegal option --
```

## termux-saf-read
- Path: `/data/data/com.termux/files/usr/bin/termux-saf-read`
- Help exit code: 1
```text
termux-saf-read: illegal option --
```

## termux-saf-rm
- Path: `/data/data/com.termux/files/usr/bin/termux-saf-rm`
- Help exit code: 1
```text
termux-saf-rm: illegal option --
```

## termux-saf-stat
- Path: `/data/data/com.termux/files/usr/bin/termux-saf-stat`
- Help exit code: 1
```text
termux-saf-stat: illegal option --
```

## termux-saf-write
- Path: `/data/data/com.termux/files/usr/bin/termux-saf-write`
- Help exit code: 1
```text
termux-saf-write: illegal option --
```

## termux-scoped-env-variable
- Path: `/data/data/com.termux/files/usr/bin/termux-scoped-env-variable`
- Help exit code: 0
```text
termux-scoped-env-variable.bash can be used to get/set/unset variable
names and values for `TERMUX*__` and other scoped environment
variables exported by different Termux runtime components, with
support for fallback values and validation of values.


Usage:
  termux-scoped-env-variable.bash <command> <args...>


Available commands:
    get-name                     Get Termux scoped variable name.
    get-value                    Get Termux scoped variable value.
    set-value                    Set Termux scoped variable value.
    unset-value                  Unset Termux scoped variable value.



get-name:
  termux-scoped-env-variable.bash get-name \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name>



get-value:
  termux-scoped-env-variable.bash get-value \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name> \
    <extended_validator> [<default_values...>]



set-value:
  termux-scoped-env-variable.bash set-value \
    <scoped_var_scope_mode> <scoped_var_sub_name> \
    <value_to_set>



unset-value:
  termux-scoped-env-variable.bash unset-value \
    <scoped_var_scope_mode> <scoped_var_sub_name>



Available arguments:
- `output_mode`: The
...
```

## termux-scoped-env-variable.bash
- Path: `/data/data/com.termux/files/usr/bin/termux-scoped-env-variable.bash`
- Help exit code: 0
```text
termux-scoped-env-variable.bash can be used to get/set/unset variable
names and values for `TERMUX*__` and other scoped environment
variables exported by different Termux runtime components, with
support for fallback values and validation of values.


Usage:
  termux-scoped-env-variable.bash <command> <args...>


Available commands:
    get-name                     Get Termux scoped variable name.
    get-value                    Get Termux scoped variable value.
    set-value                    Set Termux scoped variable value.
    unset-value                  Unset Termux scoped variable value.



get-name:
  termux-scoped-env-variable.bash get-name \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name>



get-value:
  termux-scoped-env-variable.bash get-value \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name> \
    <extended_validator> [<default_values...>]



set-value:
  termux-scoped-env-variable.bash set-value \
    <scoped_var_scope_mode> <scoped_var_sub_name> \
    <value_to_set>



unset-value:
  termux-scoped-env-variable.bash unset-value \
    <scoped_var_scope_mode> <scoped_var_sub_name>



Available arguments:
- `output_mode`: The
...
```

## termux-scoped-env-variable.sh
- Path: `/data/data/com.termux/files/usr/bin/termux-scoped-env-variable.sh`
- Help exit code: 0
```text
termux-scoped-env-variable.sh can be used to get/set/unset variable
names and values for `TERMUX*__` and other scoped environment
variables exported by different Termux runtime components, with
support for fallback values and validation of values.


Usage:
  termux-scoped-env-variable.sh <command> <args...>


Available commands:
    get-name                     Get Termux scoped variable name.
    get-value                    Get Termux scoped variable value.
    set-value                    Set Termux scoped variable value.
    unset-value                  Unset Termux scoped variable value.



get-name:
  termux-scoped-env-variable.sh get-name \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name>



get-value:
  termux-scoped-env-variable.sh get-value \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name> \
    <posix_validator> [<default_values...>]



set-value:
  termux-scoped-env-variable.sh set-value \
    <scoped_var_scope_mode> <scoped_var_sub_name> \
    <value_to_set>



unset-value:
  termux-scoped-env-variable.sh unset-value \
    <scoped_var_scope_mode> <scoped_var_sub_name>



Available arguments:
- `output_mode`: The output mode fo
...
```

## termux-sensor
- Path: `/data/data/com.termux/files/usr/bin/termux-sensor`
- Help exit code: 1
```text
termux-sensor: illegal option --
```

## termux-setup-package-manager
- Path: `/data/data/com.termux/files/usr/bin/termux-setup-package-manager`
- Help exit code: 0
_No help output captured._

## termux-setup-storage
- Path: `/data/data/com.termux/files/usr/bin/termux-setup-storage`
- Help exit code: 0
```text
usage: termux-setup-storage
Use without arguments to ensure storage permission granted
and symlinks to storage available in $HOME/storage
```

## termux-share
- Path: `/data/data/com.termux/files/usr/bin/termux-share`
- Help exit code: 1
```text
termux-share: illegal option --
```

## termux-sms-inbox
- Path: `/data/data/com.termux/files/usr/bin/termux-sms-inbox`
- Help exit code: 1
```text
termux-sms-inbox: This script has been replaced by termux-sms-list.
```

## termux-sms-list
- Path: `/data/data/com.termux/files/usr/bin/termux-sms-list`
- Help exit code: 0
```text
termux-sms-list command lists SMS conversations and messages.

Usage:
    termux-sms-list [command_options]

Available command_options:
    [ -h | --help ]           Display this help screen.
#    [ --version ]             Display version.
    [ -q | --quiet ]          Set log level to 'OFF'.
    [ -v | -vv | -vvv | -vvvvv ]
                              Set log level to 'DEBUG', 'VERBOSE',
                              'VVERBOSE' and 'VVVERBOSE'.
    [ -c | --conversation-list ]
                              Show SMS conversations list instead of
                              messages list.
                              By default only one SMS message per
                              conversation is returned unless the
                              '--conversation-return-multiple-messages'
                              option is passed.
                              By default all SMS messages are returned
                              as a single array without nesting
                              unless the
                              '--conversation-return-nested-view'
                              option is passed.
    [ --conversation-limit=<limit> ]
                      
...
```

## termux-sms-send
- Path: `/data/data/com.termux/files/usr/bin/termux-sms-send`
- Help exit code: 1
```text
termux-sms-send: illegal option --
```

## termux-speech-to-text
- Path: `/data/data/com.termux/files/usr/bin/termux-speech-to-text`
- Help exit code: 1
```text
termux-speech-to-text: illegal option --
```

## termux-ssh
- Path: `/data/data/com.termux/files/usr/bin/termux-ssh`
- Help exit code: 0
```text
termux-ssh start    to start ssh

termux-ssh stop     to stop ssh

termux-ssh          to set passwd and start ssh
```

## termux-storage-get
- Path: `/data/data/com.termux/files/usr/bin/termux-storage-get`
- Help exit code: 1
```text
termux-storage-get: illegal option --
```

## termux-sv-profile
- Path: `/data/data/com.termux/files/usr/bin/termux-sv-profile`
- Help exit code: 2
```text
Usage: termux-sv-profile <minimal|dev|status>

minimal: disable desktop/X11 + ssh + printing services
dev:     enable desktop/X11 + ssh services
status:  show current service status
```

## termux-telephony-call
- Path: `/data/data/com.termux/files/usr/bin/termux-telephony-call`
- Help exit code: 1
```text
termux-telephony-call: illegal option --
```

## termux-telephony-cellinfo
- Path: `/data/data/com.termux/files/usr/bin/termux-telephony-cellinfo`
- Help exit code: 1
```text
termux-telephony-cellinfo: illegal option --
```

## termux-telephony-deviceinfo
- Path: `/data/data/com.termux/files/usr/bin/termux-telephony-deviceinfo`
- Help exit code: 1
```text
termux-telephony-deviceinfo: illegal option --
```

## termux-toast
- Path: `/data/data/com.termux/files/usr/bin/termux-toast`
- Help exit code: 1
```text
termux-toast: illegal option --
```

## termux-torch
- Path: `/data/data/com.termux/files/usr/bin/termux-torch`
- Help exit code: 1
```text
Illegal parameter: --help
Usage: termux-torch [on | off]
Toggle LED Torch on device
```

## termux-tts-engines
- Path: `/data/data/com.termux/files/usr/bin/termux-tts-engines`
- Help exit code: 1
```text
termux-tts-engines: illegal option --
```

## termux-tts-speak
- Path: `/data/data/com.termux/files/usr/bin/termux-tts-speak`
- Help exit code: 1
```text
termux-tts-speak: illegal option --
```

## termux-usb
- Path: `/data/data/com.termux/files/usr/bin/termux-usb`
- Help exit code: 1
```text
termux-usb: illegal option --
```

## termux-vibrate
- Path: `/data/data/com.termux/files/usr/bin/termux-vibrate`
- Help exit code: 1
```text
termux-vibrate: illegal option --
```

## termux-volume
- Path: `/data/data/com.termux/files/usr/bin/termux-volume`
- Help exit code: 0
```text
Invalid argument count
Usage: termux-volume stream volume
Change volume of audio stream
Valid audio streams are: alarm, music, notification, ring, system, call
Call w/o arguments to show information about each audio stream
```

## termux-wake-lock
- Path: `/data/data/com.termux/files/usr/bin/termux-wake-lock`
- Help exit code: 1
```text
usage: termux-wake-lock
Acquire the Termux wake lock to prevent the CPU from sleeping.
```

## termux-wake-unlock
- Path: `/data/data/com.termux/files/usr/bin/termux-wake-unlock`
- Help exit code: 1
```text
usage: termux-wake-unlock
Release the Termux wake lock to allow the CPU to sleep.
```

## termux-wallpaper
- Path: `/data/data/com.termux/files/usr/bin/termux-wallpaper`
- Help exit code: 1
```text
termux-wallpaper: illegal option --
```

## termux-wifi-connectioninfo
- Path: `/data/data/com.termux/files/usr/bin/termux-wifi-connectioninfo`
- Help exit code: 1
```text
termux-wifi-connectioninfo: illegal option --
```

## termux-wifi-enable
- Path: `/data/data/com.termux/files/usr/bin/termux-wifi-enable`
- Help exit code: 1
```text
Usage: termux-wifi-enable [true | false]
Toggle Wi-Fi On/Off
```

## termux-wifi-scaninfo
- Path: `/data/data/com.termux/files/usr/bin/termux-wifi-scaninfo`
- Help exit code: 1
```text
termux-wifi-scaninfo: illegal option --
```
