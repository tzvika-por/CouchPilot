# Screenshot evidence

Generated at 1080x1920 / 360dpi, 100% text, from actual production Compose screens on the API35 emulator using CouchPilotScreenshotTest and sanitized fixtures. Connected state is injected, not proof of physical device connection. No personal notification, account, IP or MAC appears in these main/management captures.

- couchpilot-xiaomi-active.png: Hebrew/RTL main remote, Xiaomi selected; navigation before yes+ keypad.
- couchpilot-non-xiaomi-active.png: non-Xiaomi source selected; only common controls.
- couchpilot-device-management.png: management dialog with setup/help/connection controls.

Automated scenarios additionally cover default/disconnected/error, LG authorization, LAN/HID pairing, Samsung permission, English/Hebrew,100/150/200 percent fonts and landscape. Native dialog windows reset injected Compose density; tools/check-font-scale.sh additionally runs six captures and four contextual tests using the actual Android 200% font setting, then restores it. Screenshots are review evidence rather than golden pixel comparisons; assertions verify selected/contextual controls and separate functional tests verify routing/accessibility.
