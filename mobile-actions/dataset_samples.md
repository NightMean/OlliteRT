# Dataset samples

## premium
### Example 1
```json
{
  "metadata": "eval",
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "termux_am",
        "description": "termux-am is a wrapper script that converts the arguments array",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_am_socket",
        "description": "Could not connect to socket: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_start",
        "description": "Starting service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_stop",
        "description": "Stopping service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.bash",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.sh",
        "description": "termux-apps-info-app-version-name.sh can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.bash",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.sh",
        "description": "termux-apps-info-env-variable.sh can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_audio_info",
        "description": "termux-audio-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_backup",
        "description": "Usage: termux-backup [options] [output file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_battery_status",
        "description": "termux-battery-status: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_brightness",
        "description": "ERROR: Arg must be a number between 0 - 255 or auto!",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_call_log",
        "description": "termux-call-log: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_info",
        "description": "termux-camera-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_photo",
        "description": "termux-camera-photo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_change_repo",
        "description": "Script for choosing a group of mirrors to use.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_chroot",
        "description": "termux-chroot: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_get",
        "description": "termux-clipboard-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_set",
        "description": "termux-clipboard-set: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_color",
        "description": "\u2717 This script must be run inside Termux",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_contact_list",
        "description": "termux-contact-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_dialog",
        "description": "termux-dialog: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_download",
        "description": "termux-download: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_ld_preload_lib",
        "description": "termux-exec-ld-preload-lib can be used to manage Termux '$LD_PRELOAD'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_system_linker_exec",
        "description": "termux-exec-system-linker-exec can be used to get states of",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fastest_repo",
        "description": "/data/data/com.termux/files/usr/bin/termux-fastest-repo: line 25: /bin/termux-setup-package-manager: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fingerprint",
        "description": "termux-fingerprint: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fix_shebang",
        "description": "sed: can't read Usage: realpath [OPTION]... FILE...",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_info",
        "description": "usage: termux-info [--no-set-clipboard]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_frequencies",
        "description": "termux-infrared-frequencies: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_transmit",
        "description": "termux-infrared-transmit: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_job_scheduler",
        "description": "Usage: termux-job-scheduler [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_keystore",
        "description": "Usage: termux-keystore command",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_location",
        "description": "termux-location: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_player",
        "description": "termux-media-player: Invalid cmd: '--help'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_scan",
        "description": "termux-media-scan: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_microphone_record",
        "description": "/data/data/com.termux/files/usr/bin/termux-microphone-record: illegal option -- -",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nf",
        "description": "ERROR: Command '['/data/data/com.termux/files/usr/bin/termux-nf', '--help']' timed out after 3 seconds",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nfc",
        "description": "Error: unknown parameters: ? -;",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification",
        "description": "Usage: termux-notification [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_channel",
        "description": "Usage: termux-notification-channel -d channel-id",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_list",
        "description": "termux-notification-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_remove",
        "description": "termux-notification-remove: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open",
        "description": "Usage: termux-open [options] path-or-url",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open_url",
        "description": "Error: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW dat= flg=0x10000000 }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reload_settings",
        "description": "usage: termux-reload-settings",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reset",
        "description": "You are going to reset your Termux installation.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_restore",
        "description": "Usage: termux-restore [input file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_create",
        "description": "termux-saf-create: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_dirs",
        "description": "termux-saf-dirs: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_ls",
        "description": "termux-saf-ls: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_managedir",
        "description": "termux-saf-managedir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_mkdir",
        "description": "termux-saf-mkdir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_read",
        "description": "termux-saf-read: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_rm",
        "description": "termux-saf-rm: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_stat",
        "description": "termux-saf-stat: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_write",
        "description": "termux-saf-write: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.bash",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.sh",
        "description": "termux-scoped-env-variable.sh can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sensor",
        "description": "termux-sensor: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_package_manager",
        "description": "Termux API command: termux-setup-package-manager",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_storage",
        "description": "usage: termux-setup-storage",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_share",
        "description": "termux-share: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_inbox",
        "description": "termux-sms-inbox: This script has been replaced by termux-sms-list.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_list",
        "description": "termux-sms-list command lists SMS conversations and messages.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_send",
        "description": "termux-sms-send: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_speech_to_text",
        "description": "termux-speech-to-text: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_ssh",
        "description": "termux-ssh start    to start ssh",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_storage_get",
        "description": "termux-storage-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sv_profile",
        "description": "Usage: termux-sv-profile <minimal|dev|status>",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_call",
        "description": "termux-telephony-call: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_cellinfo",
        "description": "termux-telephony-cellinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_deviceinfo",
        "description": "termux-telephony-deviceinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_toast",
        "description": "termux-toast: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_torch",
        "description": "Illegal parameter: --help",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_engines",
        "description": "termux-tts-engines: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_speak",
        "description": "termux-tts-speak: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_usb",
        "description": "termux-usb: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_vibrate",
        "description": "termux-vibrate: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_volume",
        "description": "Invalid argument count",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_lock",
        "description": "usage: termux-wake-lock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_unlock",
        "description": "usage: termux-wake-unlock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wallpaper",
        "description": "termux-wallpaper: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_connectioninfo",
        "description": "termux-wifi-connectioninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_enable",
        "description": "Usage: termux-wifi-enable [true | false]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_scaninfo",
        "description": "termux-wifi-scaninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "exec_proot",
        "description": "Run a shell command inside the default proot distro (the primary shell environment).",
        "parameters": {
          "type": "object",
          "properties": {
            "cmd": {
              "type": "string",
              "description": "Shell command to run inside proot. Executed via /bin/sh -lc in the proot environment."
            },
            "cwd": {
              "type": "string",
              "description": "Working directory inside proot. If omitted, uses the default home directory."
            },
            "env": {
              "type": "object",
              "description": "Environment variables to set for the command.",
              "additionalProperties": {
                "type": "string"
              }
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            },
            "timeout_seconds": {
              "type": "integer",
              "description": "Optional timeout for the command execution.",
              "minimum": 1
            }
          },
          "required": [
            "cmd"
          ]
        }
      }
    }
  ],
  "messages": [
    {
      "role": "developer",
      "content": "Current date and time given in YYYY-MM-DDTHH:MM:SS format: 2026-01-19T12:00:00\nDay of week is Monday\nYou are a model that can do function calling with the following functions\n"
    },
    {
      "role": "user",
      "content": "Ejecuta termux-am."
    },
    {
      "role": "assistant",
      "tool_calls": [
        {
          "id": "call_1",
          "type": "function",
          "function": {
            "name": "termux_am",
            "arguments": {
              "args": []
            }
          }
        }
      ]
    }
  ]
}
```

### Example 2
```json
{
  "metadata": "train",
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "termux_am",
        "description": "termux-am is a wrapper script that converts the arguments array",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_am_socket",
        "description": "Could not connect to socket: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_start",
        "description": "Starting service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_stop",
        "description": "Stopping service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.bash",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.sh",
        "description": "termux-apps-info-app-version-name.sh can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.bash",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.sh",
        "description": "termux-apps-info-env-variable.sh can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_audio_info",
        "description": "termux-audio-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_backup",
        "description": "Usage: termux-backup [options] [output file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_battery_status",
        "description": "termux-battery-status: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_brightness",
        "description": "ERROR: Arg must be a number between 0 - 255 or auto!",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_call_log",
        "description": "termux-call-log: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_info",
        "description": "termux-camera-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_photo",
        "description": "termux-camera-photo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_change_repo",
        "description": "Script for choosing a group of mirrors to use.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_chroot",
        "description": "termux-chroot: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_get",
        "description": "termux-clipboard-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_set",
        "description": "termux-clipboard-set: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_color",
        "description": "\u2717 This script must be run inside Termux",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_contact_list",
        "description": "termux-contact-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_dialog",
        "description": "termux-dialog: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_download",
        "description": "termux-download: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_ld_preload_lib",
        "description": "termux-exec-ld-preload-lib can be used to manage Termux '$LD_PRELOAD'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_system_linker_exec",
        "description": "termux-exec-system-linker-exec can be used to get states of",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fastest_repo",
        "description": "/data/data/com.termux/files/usr/bin/termux-fastest-repo: line 25: /bin/termux-setup-package-manager: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fingerprint",
        "description": "termux-fingerprint: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fix_shebang",
        "description": "sed: can't read Usage: realpath [OPTION]... FILE...",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_info",
        "description": "usage: termux-info [--no-set-clipboard]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_frequencies",
        "description": "termux-infrared-frequencies: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_transmit",
        "description": "termux-infrared-transmit: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_job_scheduler",
        "description": "Usage: termux-job-scheduler [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_keystore",
        "description": "Usage: termux-keystore command",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_location",
        "description": "termux-location: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_player",
        "description": "termux-media-player: Invalid cmd: '--help'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_scan",
        "description": "termux-media-scan: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_microphone_record",
        "description": "/data/data/com.termux/files/usr/bin/termux-microphone-record: illegal option -- -",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nf",
        "description": "ERROR: Command '['/data/data/com.termux/files/usr/bin/termux-nf', '--help']' timed out after 3 seconds",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nfc",
        "description": "Error: unknown parameters: ? -;",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification",
        "description": "Usage: termux-notification [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_channel",
        "description": "Usage: termux-notification-channel -d channel-id",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_list",
        "description": "termux-notification-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_remove",
        "description": "termux-notification-remove: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open",
        "description": "Usage: termux-open [options] path-or-url",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open_url",
        "description": "Error: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW dat= flg=0x10000000 }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reload_settings",
        "description": "usage: termux-reload-settings",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reset",
        "description": "You are going to reset your Termux installation.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_restore",
        "description": "Usage: termux-restore [input file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_create",
        "description": "termux-saf-create: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_dirs",
        "description": "termux-saf-dirs: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_ls",
        "description": "termux-saf-ls: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_managedir",
        "description": "termux-saf-managedir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_mkdir",
        "description": "termux-saf-mkdir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_read",
        "description": "termux-saf-read: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_rm",
        "description": "termux-saf-rm: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_stat",
        "description": "termux-saf-stat: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_write",
        "description": "termux-saf-write: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.bash",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.sh",
        "description": "termux-scoped-env-variable.sh can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sensor",
        "description": "termux-sensor: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_package_manager",
        "description": "Termux API command: termux-setup-package-manager",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_storage",
        "description": "usage: termux-setup-storage",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_share",
        "description": "termux-share: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_inbox",
        "description": "termux-sms-inbox: This script has been replaced by termux-sms-list.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_list",
        "description": "termux-sms-list command lists SMS conversations and messages.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_send",
        "description": "termux-sms-send: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_speech_to_text",
        "description": "termux-speech-to-text: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_ssh",
        "description": "termux-ssh start    to start ssh",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_storage_get",
        "description": "termux-storage-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sv_profile",
        "description": "Usage: termux-sv-profile <minimal|dev|status>",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_call",
        "description": "termux-telephony-call: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_cellinfo",
        "description": "termux-telephony-cellinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_deviceinfo",
        "description": "termux-telephony-deviceinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_toast",
        "description": "termux-toast: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_torch",
        "description": "Illegal parameter: --help",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_engines",
        "description": "termux-tts-engines: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_speak",
        "description": "termux-tts-speak: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_usb",
        "description": "termux-usb: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_vibrate",
        "description": "termux-vibrate: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_volume",
        "description": "Invalid argument count",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_lock",
        "description": "usage: termux-wake-lock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_unlock",
        "description": "usage: termux-wake-unlock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wallpaper",
        "description": "termux-wallpaper: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_connectioninfo",
        "description": "termux-wifi-connectioninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_enable",
        "description": "Usage: termux-wifi-enable [true | false]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_scaninfo",
        "description": "termux-wifi-scaninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "exec_proot",
        "description": "Run a shell command inside the default proot distro (the primary shell environment).",
        "parameters": {
          "type": "object",
          "properties": {
            "cmd": {
              "type": "string",
              "description": "Shell command to run inside proot. Executed via /bin/sh -lc in the proot environment."
            },
            "cwd": {
              "type": "string",
              "description": "Working directory inside proot. If omitted, uses the default home directory."
            },
            "env": {
              "type": "object",
              "description": "Environment variables to set for the command.",
              "additionalProperties": {
                "type": "string"
              }
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            },
            "timeout_seconds": {
              "type": "integer",
              "description": "Optional timeout for the command execution.",
              "minimum": 1
            }
          },
          "required": [
            "cmd"
          ]
        }
      }
    }
  ],
  "messages": [
    {
      "role": "developer",
      "content": "Current date and time given in YYYY-MM-DDTHH:MM:SS format: 2026-01-19T12:00:00\nDay of week is Monday\nYou are a model that can do function calling with the following functions\n"
    },
    {
      "role": "user",
      "content": "Necesito correr termux-am."
    },
    {
      "role": "assistant",
      "tool_calls": [
        {
          "id": "call_1",
          "type": "function",
          "function": {
            "name": "termux_am",
            "arguments": {
              "args": []
            }
          }
        }
      ]
    }
  ]
}
```

### Example 3
```json
{
  "metadata": "train",
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "termux_am",
        "description": "termux-am is a wrapper script that converts the arguments array",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_am_socket",
        "description": "Could not connect to socket: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_start",
        "description": "Starting service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_stop",
        "description": "Stopping service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.bash",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.sh",
        "description": "termux-apps-info-app-version-name.sh can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.bash",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.sh",
        "description": "termux-apps-info-env-variable.sh can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_audio_info",
        "description": "termux-audio-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_backup",
        "description": "Usage: termux-backup [options] [output file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_battery_status",
        "description": "termux-battery-status: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_brightness",
        "description": "ERROR: Arg must be a number between 0 - 255 or auto!",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_call_log",
        "description": "termux-call-log: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_info",
        "description": "termux-camera-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_photo",
        "description": "termux-camera-photo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_change_repo",
        "description": "Script for choosing a group of mirrors to use.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_chroot",
        "description": "termux-chroot: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_get",
        "description": "termux-clipboard-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_set",
        "description": "termux-clipboard-set: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_color",
        "description": "\u2717 This script must be run inside Termux",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_contact_list",
        "description": "termux-contact-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_dialog",
        "description": "termux-dialog: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_download",
        "description": "termux-download: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_ld_preload_lib",
        "description": "termux-exec-ld-preload-lib can be used to manage Termux '$LD_PRELOAD'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_system_linker_exec",
        "description": "termux-exec-system-linker-exec can be used to get states of",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fastest_repo",
        "description": "/data/data/com.termux/files/usr/bin/termux-fastest-repo: line 25: /bin/termux-setup-package-manager: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fingerprint",
        "description": "termux-fingerprint: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fix_shebang",
        "description": "sed: can't read Usage: realpath [OPTION]... FILE...",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_info",
        "description": "usage: termux-info [--no-set-clipboard]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_frequencies",
        "description": "termux-infrared-frequencies: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_transmit",
        "description": "termux-infrared-transmit: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_job_scheduler",
        "description": "Usage: termux-job-scheduler [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_keystore",
        "description": "Usage: termux-keystore command",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_location",
        "description": "termux-location: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_player",
        "description": "termux-media-player: Invalid cmd: '--help'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_scan",
        "description": "termux-media-scan: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_microphone_record",
        "description": "/data/data/com.termux/files/usr/bin/termux-microphone-record: illegal option -- -",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nf",
        "description": "ERROR: Command '['/data/data/com.termux/files/usr/bin/termux-nf', '--help']' timed out after 3 seconds",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nfc",
        "description": "Error: unknown parameters: ? -;",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification",
        "description": "Usage: termux-notification [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_channel",
        "description": "Usage: termux-notification-channel -d channel-id",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_list",
        "description": "termux-notification-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_remove",
        "description": "termux-notification-remove: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open",
        "description": "Usage: termux-open [options] path-or-url",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open_url",
        "description": "Error: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW dat= flg=0x10000000 }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reload_settings",
        "description": "usage: termux-reload-settings",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reset",
        "description": "You are going to reset your Termux installation.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_restore",
        "description": "Usage: termux-restore [input file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_create",
        "description": "termux-saf-create: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_dirs",
        "description": "termux-saf-dirs: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_ls",
        "description": "termux-saf-ls: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_managedir",
        "description": "termux-saf-managedir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_mkdir",
        "description": "termux-saf-mkdir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_read",
        "description": "termux-saf-read: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_rm",
        "description": "termux-saf-rm: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_stat",
        "description": "termux-saf-stat: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_write",
        "description": "termux-saf-write: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.bash",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.sh",
        "description": "termux-scoped-env-variable.sh can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sensor",
        "description": "termux-sensor: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_package_manager",
        "description": "Termux API command: termux-setup-package-manager",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_storage",
        "description": "usage: termux-setup-storage",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_share",
        "description": "termux-share: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_inbox",
        "description": "termux-sms-inbox: This script has been replaced by termux-sms-list.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_list",
        "description": "termux-sms-list command lists SMS conversations and messages.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_send",
        "description": "termux-sms-send: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_speech_to_text",
        "description": "termux-speech-to-text: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_ssh",
        "description": "termux-ssh start    to start ssh",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_storage_get",
        "description": "termux-storage-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sv_profile",
        "description": "Usage: termux-sv-profile <minimal|dev|status>",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_call",
        "description": "termux-telephony-call: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_cellinfo",
        "description": "termux-telephony-cellinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_deviceinfo",
        "description": "termux-telephony-deviceinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_toast",
        "description": "termux-toast: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_torch",
        "description": "Illegal parameter: --help",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_engines",
        "description": "termux-tts-engines: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_speak",
        "description": "termux-tts-speak: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_usb",
        "description": "termux-usb: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_vibrate",
        "description": "termux-vibrate: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_volume",
        "description": "Invalid argument count",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_lock",
        "description": "usage: termux-wake-lock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_unlock",
        "description": "usage: termux-wake-unlock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wallpaper",
        "description": "termux-wallpaper: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_connectioninfo",
        "description": "termux-wifi-connectioninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_enable",
        "description": "Usage: termux-wifi-enable [true | false]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_scaninfo",
        "description": "termux-wifi-scaninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "exec_proot",
        "description": "Run a shell command inside the default proot distro (the primary shell environment).",
        "parameters": {
          "type": "object",
          "properties": {
            "cmd": {
              "type": "string",
              "description": "Shell command to run inside proot. Executed via /bin/sh -lc in the proot environment."
            },
            "cwd": {
              "type": "string",
              "description": "Working directory inside proot. If omitted, uses the default home directory."
            },
            "env": {
              "type": "object",
              "description": "Environment variables to set for the command.",
              "additionalProperties": {
                "type": "string"
              }
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            },
            "timeout_seconds": {
              "type": "integer",
              "description": "Optional timeout for the command execution.",
              "minimum": 1
            }
          },
          "required": [
            "cmd"
          ]
        }
      }
    }
  ],
  "messages": [
    {
      "role": "developer",
      "content": "Current date and time given in YYYY-MM-DDTHH:MM:SS format: 2026-01-19T12:00:00\nDay of week is Monday\nYou are a model that can do function calling with the following functions\n"
    },
    {
      "role": "user",
      "content": "Haz termux-am."
    },
    {
      "role": "assistant",
      "tool_calls": [
        {
          "id": "call_1",
          "type": "function",
          "function": {
            "name": "termux_am",
            "arguments": {
              "args": []
            }
          }
        }
      ]
    }
  ]
}
```

## combined
### Example 1
```json
{
  "metadata": "train",
  "tools": [
    {
      "function": {
        "name": "turn_off_flashlight",
        "description": "Turns the flashlight off.",
        "parameters": {
          "type": "OBJECT",
          "properties": {}
        }
      }
    },
    {
      "function": {
        "name": "open_wifi_settings",
        "description": "Opens the Wi-Fi settings.",
        "parameters": {
          "type": "OBJECT",
          "properties": {}
        }
      }
    },
    {
      "function": {
        "name": "create_calendar_event",
        "description": "Creates a new calendar event.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "title": {
              "type": "STRING",
              "description": "The title of the event."
            },
            "datetime": {
              "type": "STRING",
              "description": "The date and time of the event in the format YYYY-MM-DDTHH:MM:SS."
            }
          },
          "required": [
            "title",
            "datetime"
          ]
        }
      }
    },
    {
      "function": {
        "name": "show_map",
        "description": "Shows a location on the map.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "query": {
              "type": "STRING",
              "description": "The location to search for. May be the name of a place, a business, or an address."
            }
          },
          "required": [
            "query"
          ]
        }
      }
    },
    {
      "function": {
        "name": "send_email",
        "description": "Sends an email.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "subject": {
              "type": "STRING",
              "description": "The subject of the email."
            },
            "body": {
              "type": "STRING",
              "description": "The body of the email."
            },
            "to": {
              "type": "STRING",
              "description": "The email address of the recipient."
            }
          },
          "required": [
            "to",
            "subject"
          ]
        }
      }
    },
    {
      "function": {
        "name": "turn_on_flashlight",
        "description": "Turns the flashlight on.",
        "parameters": {
          "type": "OBJECT",
          "properties": {}
        }
      }
    },
    {
      "function": {
        "name": "create_contact",
        "description": "Creates a contact in the phone's contact list.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "first_name": {
              "type": "STRING",
              "description": "The first name of the contact."
            },
            "last_name": {
              "type": "STRING",
              "description": "The last name of the contact."
            },
            "phone_number": {
              "type": "STRING",
              "description": "The phone number of the contact."
            },
            "email": {
              "type": "STRING",
              "description": "The email address of the contact."
            }
          },
          "required": [
            "first_name",
            "last_name"
          ]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_am",
        "description": "termux-am is a wrapper script that converts the arguments array",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_am_socket",
        "description": "Could not connect to socket: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_start",
        "description": "Starting service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_stop",
        "description": "Stopping service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.bash",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.sh",
        "description": "termux-apps-info-app-version-name.sh can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.bash",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.sh",
        "description": "termux-apps-info-env-variable.sh can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_audio_info",
        "description": "termux-audio-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_backup",
        "description": "Usage: termux-backup [options] [output file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_battery_status",
        "description": "termux-battery-status: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_brightness",
        "description": "ERROR: Arg must be a number between 0 - 255 or auto!",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_call_log",
        "description": "termux-call-log: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_info",
        "description": "termux-camera-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_photo",
        "description": "termux-camera-photo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_change_repo",
        "description": "Script for choosing a group of mirrors to use.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_chroot",
        "description": "termux-chroot: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_get",
        "description": "termux-clipboard-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_set",
        "description": "termux-clipboard-set: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_color",
        "description": "\u2717 This script must be run inside Termux",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_contact_list",
        "description": "termux-contact-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_dialog",
        "description": "termux-dialog: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_download",
        "description": "termux-download: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_ld_preload_lib",
        "description": "termux-exec-ld-preload-lib can be used to manage Termux '$LD_PRELOAD'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_system_linker_exec",
        "description": "termux-exec-system-linker-exec can be used to get states of",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fastest_repo",
        "description": "/data/data/com.termux/files/usr/bin/termux-fastest-repo: line 25: /bin/termux-setup-package-manager: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fingerprint",
        "description": "termux-fingerprint: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fix_shebang",
        "description": "sed: can't read Usage: realpath [OPTION]... FILE...",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_info",
        "description": "usage: termux-info [--no-set-clipboard]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_frequencies",
        "description": "termux-infrared-frequencies: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_transmit",
        "description": "termux-infrared-transmit: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_job_scheduler",
        "description": "Usage: termux-job-scheduler [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_keystore",
        "description": "Usage: termux-keystore command",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_location",
        "description": "termux-location: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_player",
        "description": "termux-media-player: Invalid cmd: '--help'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_scan",
        "description": "termux-media-scan: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_microphone_record",
        "description": "/data/data/com.termux/files/usr/bin/termux-microphone-record: illegal option -- -",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nf",
        "description": "ERROR: Command '['/data/data/com.termux/files/usr/bin/termux-nf', '--help']' timed out after 3 seconds",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nfc",
        "description": "Error: unknown parameters: ? -;",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification",
        "description": "Usage: termux-notification [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_channel",
        "description": "Usage: termux-notification-channel -d channel-id",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_list",
        "description": "termux-notification-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_remove",
        "description": "termux-notification-remove: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open",
        "description": "Usage: termux-open [options] path-or-url",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open_url",
        "description": "Error: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW dat= flg=0x10000000 }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reload_settings",
        "description": "usage: termux-reload-settings",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reset",
        "description": "You are going to reset your Termux installation.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_restore",
        "description": "Usage: termux-restore [input file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_create",
        "description": "termux-saf-create: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_dirs",
        "description": "termux-saf-dirs: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_ls",
        "description": "termux-saf-ls: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_managedir",
        "description": "termux-saf-managedir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_mkdir",
        "description": "termux-saf-mkdir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_read",
        "description": "termux-saf-read: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_rm",
        "description": "termux-saf-rm: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_stat",
        "description": "termux-saf-stat: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_write",
        "description": "termux-saf-write: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.bash",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.sh",
        "description": "termux-scoped-env-variable.sh can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sensor",
        "description": "termux-sensor: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_package_manager",
        "description": "Termux API command: termux-setup-package-manager",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_storage",
        "description": "usage: termux-setup-storage",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_share",
        "description": "termux-share: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_inbox",
        "description": "termux-sms-inbox: This script has been replaced by termux-sms-list.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_list",
        "description": "termux-sms-list command lists SMS conversations and messages.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_send",
        "description": "termux-sms-send: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_speech_to_text",
        "description": "termux-speech-to-text: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_ssh",
        "description": "termux-ssh start    to start ssh",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_storage_get",
        "description": "termux-storage-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sv_profile",
        "description": "Usage: termux-sv-profile <minimal|dev|status>",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_call",
        "description": "termux-telephony-call: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_cellinfo",
        "description": "termux-telephony-cellinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_deviceinfo",
        "description": "termux-telephony-deviceinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_toast",
        "description": "termux-toast: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_torch",
        "description": "Illegal parameter: --help",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_engines",
        "description": "termux-tts-engines: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_speak",
        "description": "termux-tts-speak: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_usb",
        "description": "termux-usb: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_vibrate",
        "description": "termux-vibrate: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_volume",
        "description": "Invalid argument count",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_lock",
        "description": "usage: termux-wake-lock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_unlock",
        "description": "usage: termux-wake-unlock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wallpaper",
        "description": "termux-wallpaper: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_connectioninfo",
        "description": "termux-wifi-connectioninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_enable",
        "description": "Usage: termux-wifi-enable [true | false]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_scaninfo",
        "description": "termux-wifi-scaninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "exec_proot",
        "description": "Run a shell command inside the default proot distro (the primary shell environment).",
        "parameters": {
          "type": "object",
          "properties": {
            "cmd": {
              "type": "string",
              "description": "Shell command to run inside proot. Executed via /bin/sh -lc in the proot environment."
            },
            "cwd": {
              "type": "string",
              "description": "Working directory inside proot. If omitted, uses the default home directory."
            },
            "env": {
              "type": "object",
              "description": "Environment variables to set for the command.",
              "additionalProperties": {
                "type": "string"
              }
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            },
            "timeout_seconds": {
              "type": "integer",
              "description": "Optional timeout for the command execution.",
              "minimum": 1
            }
          },
          "required": [
            "cmd"
          ]
        }
      }
    }
  ],
  "messages": [
    {
      "role": "developer",
      "content": "Current date and time given in YYYY-MM-DDTHH:MM:SS format: 2025-06-04T15:29:23\nDay of week is Wednesday\nYou are a model that can do function calling with the following functions\n"
    },
    {
      "role": "user",
      "content": "Please set a reminder for a \"Team Sync Meeting\" this Friday, June 6th, 2025, at 2 PM."
    },
    {
      "role": "assistant",
      "tool_calls": [
        {
          "function": {
            "name": "create_calendar_event",
            "arguments": {
              "datetime": "2025-06-06T14:00:00",
              "title": "Team Sync Meeting"
            }
          }
        }
      ]
    }
  ]
}
```

### Example 2
```json
{
  "metadata": "train",
  "tools": [
    {
      "function": {
        "name": "turn_off_flashlight",
        "description": "Turns the flashlight off.",
        "parameters": {
          "type": "OBJECT",
          "properties": {}
        }
      }
    },
    {
      "function": {
        "name": "open_wifi_settings",
        "description": "Opens the Wi-Fi settings.",
        "parameters": {
          "type": "OBJECT",
          "properties": {}
        }
      }
    },
    {
      "function": {
        "name": "create_calendar_event",
        "description": "Creates a new calendar event.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "title": {
              "type": "STRING",
              "description": "The title of the event."
            },
            "datetime": {
              "type": "STRING",
              "description": "The date and time of the event in the format YYYY-MM-DDTHH:MM:SS."
            }
          },
          "required": [
            "title",
            "datetime"
          ]
        }
      }
    },
    {
      "function": {
        "name": "show_map",
        "description": "Shows a location on the map.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "query": {
              "type": "STRING",
              "description": "The location to search for. May be the name of a place, a business, or an address."
            }
          },
          "required": [
            "query"
          ]
        }
      }
    },
    {
      "function": {
        "name": "send_email",
        "description": "Sends an email.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "subject": {
              "type": "STRING",
              "description": "The subject of the email."
            },
            "body": {
              "type": "STRING",
              "description": "The body of the email."
            },
            "to": {
              "type": "STRING",
              "description": "The email address of the recipient."
            }
          },
          "required": [
            "to",
            "subject"
          ]
        }
      }
    },
    {
      "function": {
        "name": "turn_on_flashlight",
        "description": "Turns the flashlight on.",
        "parameters": {
          "type": "OBJECT",
          "properties": {}
        }
      }
    },
    {
      "function": {
        "name": "create_contact",
        "description": "Creates a contact in the phone's contact list.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "first_name": {
              "type": "STRING",
              "description": "The first name of the contact."
            },
            "last_name": {
              "type": "STRING",
              "description": "The last name of the contact."
            },
            "phone_number": {
              "type": "STRING",
              "description": "The phone number of the contact."
            },
            "email": {
              "type": "STRING",
              "description": "The email address of the contact."
            }
          },
          "required": [
            "first_name",
            "last_name"
          ]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_am",
        "description": "termux-am is a wrapper script that converts the arguments array",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_am_socket",
        "description": "Could not connect to socket: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_start",
        "description": "Starting service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_stop",
        "description": "Stopping service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.bash",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.sh",
        "description": "termux-apps-info-app-version-name.sh can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.bash",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.sh",
        "description": "termux-apps-info-env-variable.sh can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_audio_info",
        "description": "termux-audio-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_backup",
        "description": "Usage: termux-backup [options] [output file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_battery_status",
        "description": "termux-battery-status: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_brightness",
        "description": "ERROR: Arg must be a number between 0 - 255 or auto!",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_call_log",
        "description": "termux-call-log: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_info",
        "description": "termux-camera-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_photo",
        "description": "termux-camera-photo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_change_repo",
        "description": "Script for choosing a group of mirrors to use.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_chroot",
        "description": "termux-chroot: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_get",
        "description": "termux-clipboard-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_set",
        "description": "termux-clipboard-set: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_color",
        "description": "\u2717 This script must be run inside Termux",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_contact_list",
        "description": "termux-contact-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_dialog",
        "description": "termux-dialog: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_download",
        "description": "termux-download: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_ld_preload_lib",
        "description": "termux-exec-ld-preload-lib can be used to manage Termux '$LD_PRELOAD'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_system_linker_exec",
        "description": "termux-exec-system-linker-exec can be used to get states of",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fastest_repo",
        "description": "/data/data/com.termux/files/usr/bin/termux-fastest-repo: line 25: /bin/termux-setup-package-manager: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fingerprint",
        "description": "termux-fingerprint: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fix_shebang",
        "description": "sed: can't read Usage: realpath [OPTION]... FILE...",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_info",
        "description": "usage: termux-info [--no-set-clipboard]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_frequencies",
        "description": "termux-infrared-frequencies: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_transmit",
        "description": "termux-infrared-transmit: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_job_scheduler",
        "description": "Usage: termux-job-scheduler [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_keystore",
        "description": "Usage: termux-keystore command",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_location",
        "description": "termux-location: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_player",
        "description": "termux-media-player: Invalid cmd: '--help'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_scan",
        "description": "termux-media-scan: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_microphone_record",
        "description": "/data/data/com.termux/files/usr/bin/termux-microphone-record: illegal option -- -",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nf",
        "description": "ERROR: Command '['/data/data/com.termux/files/usr/bin/termux-nf', '--help']' timed out after 3 seconds",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nfc",
        "description": "Error: unknown parameters: ? -;",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification",
        "description": "Usage: termux-notification [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_channel",
        "description": "Usage: termux-notification-channel -d channel-id",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_list",
        "description": "termux-notification-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_remove",
        "description": "termux-notification-remove: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open",
        "description": "Usage: termux-open [options] path-or-url",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open_url",
        "description": "Error: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW dat= flg=0x10000000 }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reload_settings",
        "description": "usage: termux-reload-settings",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reset",
        "description": "You are going to reset your Termux installation.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_restore",
        "description": "Usage: termux-restore [input file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_create",
        "description": "termux-saf-create: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_dirs",
        "description": "termux-saf-dirs: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_ls",
        "description": "termux-saf-ls: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_managedir",
        "description": "termux-saf-managedir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_mkdir",
        "description": "termux-saf-mkdir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_read",
        "description": "termux-saf-read: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_rm",
        "description": "termux-saf-rm: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_stat",
        "description": "termux-saf-stat: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_write",
        "description": "termux-saf-write: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.bash",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.sh",
        "description": "termux-scoped-env-variable.sh can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sensor",
        "description": "termux-sensor: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_package_manager",
        "description": "Termux API command: termux-setup-package-manager",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_storage",
        "description": "usage: termux-setup-storage",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_share",
        "description": "termux-share: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_inbox",
        "description": "termux-sms-inbox: This script has been replaced by termux-sms-list.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_list",
        "description": "termux-sms-list command lists SMS conversations and messages.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_send",
        "description": "termux-sms-send: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_speech_to_text",
        "description": "termux-speech-to-text: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_ssh",
        "description": "termux-ssh start    to start ssh",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_storage_get",
        "description": "termux-storage-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sv_profile",
        "description": "Usage: termux-sv-profile <minimal|dev|status>",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_call",
        "description": "termux-telephony-call: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_cellinfo",
        "description": "termux-telephony-cellinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_deviceinfo",
        "description": "termux-telephony-deviceinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_toast",
        "description": "termux-toast: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_torch",
        "description": "Illegal parameter: --help",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_engines",
        "description": "termux-tts-engines: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_speak",
        "description": "termux-tts-speak: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_usb",
        "description": "termux-usb: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_vibrate",
        "description": "termux-vibrate: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_volume",
        "description": "Invalid argument count",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_lock",
        "description": "usage: termux-wake-lock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_unlock",
        "description": "usage: termux-wake-unlock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wallpaper",
        "description": "termux-wallpaper: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_connectioninfo",
        "description": "termux-wifi-connectioninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_enable",
        "description": "Usage: termux-wifi-enable [true | false]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_scaninfo",
        "description": "termux-wifi-scaninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "exec_proot",
        "description": "Run a shell command inside the default proot distro (the primary shell environment).",
        "parameters": {
          "type": "object",
          "properties": {
            "cmd": {
              "type": "string",
              "description": "Shell command to run inside proot. Executed via /bin/sh -lc in the proot environment."
            },
            "cwd": {
              "type": "string",
              "description": "Working directory inside proot. If omitted, uses the default home directory."
            },
            "env": {
              "type": "object",
              "description": "Environment variables to set for the command.",
              "additionalProperties": {
                "type": "string"
              }
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            },
            "timeout_seconds": {
              "type": "integer",
              "description": "Optional timeout for the command execution.",
              "minimum": 1
            }
          },
          "required": [
            "cmd"
          ]
        }
      }
    }
  ],
  "messages": [
    {
      "role": "developer",
      "content": "Current date and time given in YYYY-MM-DDTHH:MM:SS format: 2024-08-18T20:15:44\nDay of week is Sunday\nYou are a model that can do function calling with the following functions\n"
    },
    {
      "role": "user",
      "content": "Please schedule a meeting with Ms. Chen about the project proposal for Tuesday, August 20th, at 2:00 PM."
    },
    {
      "role": "assistant",
      "tool_calls": [
        {
          "function": {
            "name": "create_calendar_event",
            "arguments": {
              "title": "Meeting with Ms. Chen about the project proposal",
              "datetime": "2024-08-20T14:00:00"
            }
          }
        }
      ]
    }
  ]
}
```

### Example 3
```json
{
  "metadata": "train",
  "tools": [
    {
      "function": {
        "name": "turn_off_flashlight",
        "description": "Turns the flashlight off.",
        "parameters": {
          "type": "OBJECT",
          "properties": {}
        }
      }
    },
    {
      "function": {
        "name": "open_wifi_settings",
        "description": "Opens the Wi-Fi settings.",
        "parameters": {
          "type": "OBJECT",
          "properties": {}
        }
      }
    },
    {
      "function": {
        "name": "create_calendar_event",
        "description": "Creates a new calendar event.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "title": {
              "type": "STRING",
              "description": "The title of the event."
            },
            "datetime": {
              "type": "STRING",
              "description": "The date and time of the event in the format YYYY-MM-DDTHH:MM:SS."
            }
          },
          "required": [
            "title",
            "datetime"
          ]
        }
      }
    },
    {
      "function": {
        "name": "show_map",
        "description": "Shows a location on the map.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "query": {
              "type": "STRING",
              "description": "The location to search for. May be the name of a place, a business, or an address."
            }
          },
          "required": [
            "query"
          ]
        }
      }
    },
    {
      "function": {
        "name": "send_email",
        "description": "Sends an email.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "subject": {
              "type": "STRING",
              "description": "The subject of the email."
            },
            "body": {
              "type": "STRING",
              "description": "The body of the email."
            },
            "to": {
              "type": "STRING",
              "description": "The email address of the recipient."
            }
          },
          "required": [
            "to",
            "subject"
          ]
        }
      }
    },
    {
      "function": {
        "name": "turn_on_flashlight",
        "description": "Turns the flashlight on.",
        "parameters": {
          "type": "OBJECT",
          "properties": {}
        }
      }
    },
    {
      "function": {
        "name": "create_contact",
        "description": "Creates a contact in the phone's contact list.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "first_name": {
              "type": "STRING",
              "description": "The first name of the contact."
            },
            "last_name": {
              "type": "STRING",
              "description": "The last name of the contact."
            },
            "phone_number": {
              "type": "STRING",
              "description": "The phone number of the contact."
            },
            "email": {
              "type": "STRING",
              "description": "The email address of the contact."
            }
          },
          "required": [
            "first_name",
            "last_name"
          ]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_am",
        "description": "termux-am is a wrapper script that converts the arguments array",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_am_socket",
        "description": "Could not connect to socket: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_start",
        "description": "Starting service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_api_stop",
        "description": "Stopping service: Intent { cmp=com.termux.api/.KeepAliveService }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.bash",
        "description": "termux-apps-info-app-version-name.bash can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_app_version_name.sh",
        "description": "termux-apps-info-app-version-name.sh can be used to get/unset",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.bash",
        "description": "termux-apps-info-env-variable.bash can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_apps_info_env_variable.sh",
        "description": "termux-apps-info-env-variable.sh can be used to source the",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_audio_info",
        "description": "termux-audio-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_backup",
        "description": "Usage: termux-backup [options] [output file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_battery_status",
        "description": "termux-battery-status: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_brightness",
        "description": "ERROR: Arg must be a number between 0 - 255 or auto!",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_call_log",
        "description": "termux-call-log: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_info",
        "description": "termux-camera-info: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_camera_photo",
        "description": "termux-camera-photo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_change_repo",
        "description": "Script for choosing a group of mirrors to use.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_chroot",
        "description": "termux-chroot: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_get",
        "description": "termux-clipboard-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_clipboard_set",
        "description": "termux-clipboard-set: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_color",
        "description": "\u2717 This script must be run inside Termux",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_contact_list",
        "description": "termux-contact-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_dialog",
        "description": "termux-dialog: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_download",
        "description": "termux-download: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_ld_preload_lib",
        "description": "termux-exec-ld-preload-lib can be used to manage Termux '$LD_PRELOAD'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_exec_system_linker_exec",
        "description": "termux-exec-system-linker-exec can be used to get states of",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fastest_repo",
        "description": "/data/data/com.termux/files/usr/bin/termux-fastest-repo: line 25: /bin/termux-setup-package-manager: No such file or directory",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fingerprint",
        "description": "termux-fingerprint: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_fix_shebang",
        "description": "sed: can't read Usage: realpath [OPTION]... FILE...",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_info",
        "description": "usage: termux-info [--no-set-clipboard]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_frequencies",
        "description": "termux-infrared-frequencies: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_infrared_transmit",
        "description": "termux-infrared-transmit: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_job_scheduler",
        "description": "Usage: termux-job-scheduler [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_keystore",
        "description": "Usage: termux-keystore command",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_location",
        "description": "termux-location: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_player",
        "description": "termux-media-player: Invalid cmd: '--help'",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_media_scan",
        "description": "termux-media-scan: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_microphone_record",
        "description": "/data/data/com.termux/files/usr/bin/termux-microphone-record: illegal option -- -",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nf",
        "description": "ERROR: Command '['/data/data/com.termux/files/usr/bin/termux-nf', '--help']' timed out after 3 seconds",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_nfc",
        "description": "Error: unknown parameters: ? -;",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification",
        "description": "Usage: termux-notification [options]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_channel",
        "description": "Usage: termux-notification-channel -d channel-id",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_list",
        "description": "termux-notification-list: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_notification_remove",
        "description": "termux-notification-remove: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open",
        "description": "Usage: termux-open [options] path-or-url",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_open_url",
        "description": "Error: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW dat= flg=0x10000000 }",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reload_settings",
        "description": "usage: termux-reload-settings",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_reset",
        "description": "You are going to reset your Termux installation.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_restore",
        "description": "Usage: termux-restore [input file]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_create",
        "description": "termux-saf-create: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_dirs",
        "description": "termux-saf-dirs: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_ls",
        "description": "termux-saf-ls: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_managedir",
        "description": "termux-saf-managedir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_mkdir",
        "description": "termux-saf-mkdir: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_read",
        "description": "termux-saf-read: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_rm",
        "description": "termux-saf-rm: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_stat",
        "description": "termux-saf-stat: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_saf_write",
        "description": "termux-saf-write: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.bash",
        "description": "termux-scoped-env-variable.bash can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_scoped_env_variable.sh",
        "description": "termux-scoped-env-variable.sh can be used to get/set/unset variable",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sensor",
        "description": "termux-sensor: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_package_manager",
        "description": "Termux API command: termux-setup-package-manager",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_setup_storage",
        "description": "usage: termux-setup-storage",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_share",
        "description": "termux-share: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_inbox",
        "description": "termux-sms-inbox: This script has been replaced by termux-sms-list.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_list",
        "description": "termux-sms-list command lists SMS conversations and messages.",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sms_send",
        "description": "termux-sms-send: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_speech_to_text",
        "description": "termux-speech-to-text: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_ssh",
        "description": "termux-ssh start    to start ssh",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_storage_get",
        "description": "termux-storage-get: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_sv_profile",
        "description": "Usage: termux-sv-profile <minimal|dev|status>",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_call",
        "description": "termux-telephony-call: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_cellinfo",
        "description": "termux-telephony-cellinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_telephony_deviceinfo",
        "description": "termux-telephony-deviceinfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_toast",
        "description": "termux-toast: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_torch",
        "description": "Illegal parameter: --help",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_engines",
        "description": "termux-tts-engines: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_tts_speak",
        "description": "termux-tts-speak: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_usb",
        "description": "termux-usb: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_vibrate",
        "description": "termux-vibrate: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_volume",
        "description": "Invalid argument count",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_lock",
        "description": "usage: termux-wake-lock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wake_unlock",
        "description": "usage: termux-wake-unlock",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wallpaper",
        "description": "termux-wallpaper: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_connectioninfo",
        "description": "termux-wifi-connectioninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_enable",
        "description": "Usage: termux-wifi-enable [true | false]",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "termux_wifi_scaninfo",
        "description": "termux-wifi-scaninfo: illegal option --",
        "parameters": {
          "type": "object",
          "properties": {
            "args": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "description": "CLI arguments to pass in order."
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            }
          },
          "required": []
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "exec_proot",
        "description": "Run a shell command inside the default proot distro (the primary shell environment).",
        "parameters": {
          "type": "object",
          "properties": {
            "cmd": {
              "type": "string",
              "description": "Shell command to run inside proot. Executed via /bin/sh -lc in the proot environment."
            },
            "cwd": {
              "type": "string",
              "description": "Working directory inside proot. If omitted, uses the default home directory."
            },
            "env": {
              "type": "object",
              "description": "Environment variables to set for the command.",
              "additionalProperties": {
                "type": "string"
              }
            },
            "stdin": {
              "type": "string",
              "description": "Optional stdin payload for the command."
            },
            "timeout_seconds": {
              "type": "integer",
              "description": "Optional timeout for the command execution.",
              "minimum": 1
            }
          },
          "required": [
            "cmd"
          ]
        }
      }
    }
  ],
  "messages": [
    {
      "role": "developer",
      "content": "Current date and time given in YYYY-MM-DDTHH:MM:SS format: 2024-05-29T01:40:38\nDay of week is Wednesday\nYou are a model that can do function calling with the following functions\n"
    },
    {
      "role": "user",
      "content": "Please schedule a new calendar event for me titled \"Project Alpha Review\" on Thursday, June 6th, 2024, at 10:30 AM."
    },
    {
      "role": "assistant",
      "tool_calls": [
        {
          "function": {
            "name": "create_calendar_event",
            "arguments": {
              "datetime": "2024-06-06T10:30:00",
              "title": "Project Alpha Review"
            }
          }
        }
      ]
    }
  ]
}
```
