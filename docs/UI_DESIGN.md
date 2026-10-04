# Remote interface design

The owner supplied a dark Hebrew remote reference on 2026-10-04. The implementation uses its compact device cards, navy surfaces, cool line icons, cyan selected-source border/glow, number grid and circular navigation ring. Icons are original density-independent Compose paths; the existing volume vector assets remain. No raster illustration, icon dependency, device frame or fake system status bar is shipped.

## Main remote

- Header: settings, localized title, current power target, power and help. The contextual power action and individual Xiaomi Off/soundbar power actions retain their existing domain routes.
- Three compact, tappable device cards show actual controller connection states with status text and colored dots. Unknown, disconnected and simulated states are never displayed as Connected. Each card opens the existing device setup.
- Four HDMI source tiles: PS5/HDMI_1, Mac mini/HDMI_2, Xiaomi/HDMI_3 and PC/HDMI_4. Selected source has a cyan border and dot plus an accessibility selected state. No invented fifth TV input. No yes+ launch banner: the owner previously removed that shortcut because the supported transport cannot launch the app.
- Sound uses icon-only volume/mute buttons with localized descriptions, following the owner's earlier request. Mute/unmute follows reported status. Soundbar power remains in the sound section.
- Channels retain Last Channel, down and up. The number pad keeps conventional LTR 1–9, centered 0. Circular navigation has independent direction/OK hit targets with Home/Back beside it; media controls remain below.
- Settings contains existing setup actions and explicit connection management/background guidance. Help explains current device targeting; it initiates no connection, device command or setting change.

## Layout and accessibility

Hebrew/RTL remains driven by localization. Numeric, source and physical navigation order stays stable; direction commands never reverse under RTL. Controls are at least 48 dp, text controls can grow with Android font scaling, and the screen scrolls on smaller displays instead of shrinking hit targets. High-contrast text, status labels and selected semantics accompany color. Existing service/lifecycle behavior and automatic post-wake recovery are unchanged by the visual redesign.

## Verification boundary

Existing UI routing regressions remain and connection management coverage now opens the settings sheet. Three new isolated tests cover setup/connection management without device commands, Hebrew rendering/selected sources/circular controls, and RTL direction/digit behavior at 150% font scaling. Emulator screenshots use synthetic Connected states and a selected Xiaomi fixture; they are visual examples, not evidence of physical connections or control. No repeated owner pairing, network, volume, input or background test is requested for this UI change.

Automated gates and shared package delivery are recorded in DEVICE_VALIDATION.md and EXECUTION_PLAN.md after completion.

## Visual examples

These are actual emulator renders of the production Compose screen using synthetic device states, not physical-device evidence. Controls remain scrollable with full-size touch targets.

- [Hebrew header, devices, sources and sound](screenshots/remote-hebrew-top.png)
- [Hebrew number pad, circular navigation and media](screenshots/remote-hebrew-navigation.png)
