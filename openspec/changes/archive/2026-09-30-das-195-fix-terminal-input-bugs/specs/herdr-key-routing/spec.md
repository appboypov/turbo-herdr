## ADDED Requirements

### Requirement: macOS line-editing shortcuts work as in Ghostty
On macOS, while the Herdr panel has focus, when neither Herdr nor an enabled IDE action handles the key, Cmd+Left SHALL send `ctrl+a` (0x01), Cmd+Right SHALL send `ctrl+e` (0x05) and Cmd+Backspace SHALL send `ctrl+u` (0x15) to Herdr, whatever keyboard protocol is active, so the focused pane's program receives that byte.

#### Scenario: A line-editing chord reaches the pane as Ghostty sends it
- **GIVEN** the Herdr panel has focus on macOS
- **AND** Herdr does not bind `<key>`
- **WHEN** Brian presses `<key>`
- **THEN** the program in the focused pane receives the byte `<byte>`
- **AND** it receives no other bytes for that keypress

| key | byte |
| --- | --- |
| `cmd+left` | 0x01 |
| `cmd+right` | 0x05 |
| `cmd+backspace` | 0x15 |

#### Scenario: Brian jumps to the start of an omp prompt
- **GIVEN** the Herdr panel has focus on macOS
- **AND** omp's prompt holds `aaa bbb` with the caret at its end
- **WHEN** Brian presses `cmd+left` and types `x`
- **THEN** omp's prompt holds `xaaa bbb`

#### Scenario: Linux keeps the encoded chord
- **GIVEN** the Herdr panel has focus on Linux
- **WHEN** Brian presses `super+left`
- **THEN** the pane receives the chord as the terminal encodes it, not 0x01

### Requirement: The key after the Herdr prefix goes to Herdr only when Herdr handles it in prefix mode
After the Herdr prefix key, the next key SHALL go to Herdr as a prefix follow-up only when Herdr handles it in prefix mode: a key Herdr binds after the prefix, the prefix key itself, or Esc. For any other next key, the plugin SHALL first close Herdr's prefix mode, and the key MUST then keep the routing it has without a pending prefix.

#### Scenario: A bound prefix key still reaches Herdr
- **GIVEN** the Herdr panel has focus
- **AND** Herdr binds `prefix+v` to split vertical
- **WHEN** Brian presses `ctrl+;` and then `v`
- **THEN** Herdr splits the focused pane vertically

#### Scenario: An IDE shortcut after the prefix runs in IntelliJ
- **GIVEN** the Herdr panel has focus
- **AND** Herdr binds nothing to `cmd+o` after the prefix
- **WHEN** Brian presses `ctrl+;` and then `cmd+o`
- **THEN** IntelliJ opens its Go to Class dialog
- **AND** Herdr does not receive `cmd+o`
- **AND** Herdr no longer shows its PREFIX mode bar

#### Scenario: The key after an IDE-handled follow-up is routed normally
- **GIVEN** the Herdr panel has focus
- **WHEN** Brian presses `ctrl+;`, then `cmd+o`, closes the dialog, and presses `v`
- **THEN** the program in the focused pane receives `v`
- **AND** Herdr does not split the pane

#### Scenario: An unbound plain key after the prefix types normally
- **GIVEN** the Herdr panel has focus
- **AND** Herdr binds nothing to `x` after the prefix
- **AND** no enabled IDE action handles `x`
- **WHEN** Brian presses `ctrl+;` and then `x`
- **THEN** the program in the focused pane receives `x`
- **AND** Herdr no longer shows its PREFIX mode bar

### Requirement: A shortcut the IDE handles sends nothing to the pane
While the Herdr panel has focus, a keypress that IntelliJ or macOS handles as a shortcut MUST NOT also send any bytes to the focused pane.

#### Scenario: Brian opens Settings from the panel
- **GIVEN** the Herdr panel has focus on macOS
- **AND** Herdr does not bind `cmd+comma`
- **WHEN** Brian presses `cmd+comma`
- **THEN** IntelliJ opens Settings
- **AND** the program in the focused pane receives no bytes
