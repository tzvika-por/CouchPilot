# CouchPilot contextual UI

Approved direction: one contextual remote, navy surfaces/cyan selected state and original line glyphs. No permanent Watch yes+ banner and no Power Off All.

Common header shows CouchPilot, settings, explicit power target and48dp power control. LG/Xiaomi/Samsung status chips use readable text, state descriptions and wrapping layout; setup lives in scrollable device management. An explicitly labelled LG power action remains reachable there even with saved Xiaomi context and a sleeping TV; it does not switch inputs. Four direct source tiles map PS5/HDMI1, Mac mini/2, Xiaomi/3 and PC/4. Selected semantics and border/dot accompany color. Large font uses two source columns.

Sound is always available and routes to Samsung, with accessible icon-only volume/mute and a dedicated soundbar power control. Xiaomi selected exposes Home/Back, D-pad/OK, media and a grouped yes+ channel/keypad section. Navigation precedes the keypad. Other sources hide all Xiaomi-only controls and shorten the screen. Previous channel consistently uses ערוץ קודם; channel row is down/previous/up. Digit layout remains1–9 with centered0.

Failures use dismissible, localized actionable overlay feedback at the top plus a Snackbar visible regardless of scroll; raw protocol/socket errors never render. Pending activity uses a thin overlaid progress indicator. Both are outside the scrolling remote column, so command execution, failure, dismissal and recovery do not change control geometry or the scroll range. Buttons retain local ripples without changing measured size; unrelated controls remain available. Saved drafts survive rotation; transient pairing codes are not saved. Unknown state is not falsely shown as Connected or an acknowledged power state.

Hebrew/RTL uses bidi-isolated brand names, natural helper text and stable physical direction/digit order. Status text and selected semantics avoid color-only information. Buttons are generally at least48dp, text controls grow rather than ellipsize, and setup/management content scrolls at200% font. Emulator functional tests and screenshot scenarios cover100/150/200% and RTL; no human TalkBack audit is claimed.

Current emulator fixtures: [Xiaomi](screenshots/couchpilot-xiaomi-active.png), [other source](screenshots/couchpilot-non-xiaomi-active.png), [management](screenshots/couchpilot-device-management.png). Fixtures do not establish a physical connection.
