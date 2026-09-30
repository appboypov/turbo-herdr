## ADDED Requirements

### Requirement: Herdr's bound keys win while the panel has focus
While the Herdr panel has keyboard focus, every key Herdr binds in its config (`prefix` and each entry under `[keys]`, and each `key` under `[[keys.command]]` in `~/.config/herdr/config.toml`) SHALL go to Herdr, and the IDE SHALL NOT run the action bound to the same key. Herdr SHALL receive those keys with their `cmd`, `ctrl`, `alt` and `shift` modifiers intact.

#### Scenario Outline: A Herdr key beats the IDE shortcut
- **GIVEN** the Herdr panel has focus
- **AND** Herdr binds `<key>` to `<herdr action>` and IntelliJ binds it to `<ide action>`
- **WHEN** Brian presses `<key>`
- **THEN** Herdr performs `<herdr action>`
- **AND** IntelliJ does not perform `<ide action>`

| key | herdr action | ide action |
| --- | --- | --- |
| `cmd+k` | open Herdr search | Commit |
| `cmd+p` | open quick actions | Parameter Info |
| `cmd+w` | close pane | Close editor tab |
| `cmd+t` | new tab | Update Project |
| `cmd+e` | split vertical | Recent Files |
| `cmd+shift+k` | goto | Push |

#### Scenario: Herdr prefix sequence
- **GIVEN** the Herdr panel has focus
- **WHEN** Brian presses `ctrl+;` and then `v`
- **THEN** Herdr splits the focused pane vertically

### Requirement: Esc goes to Herdr
While the Herdr panel has focus, Esc SHALL go to Herdr and focus SHALL stay in the panel.

#### Scenario: Brian interrupts a working agent
- **GIVEN** the Herdr panel has focus
- **AND** a Claude Code agent is working in the focused Herdr pane
- **WHEN** Brian presses Esc
- **THEN** the agent is interrupted
- **AND** the Herdr panel keeps focus

### Requirement: Other IDE shortcuts keep working
While the Herdr panel has focus, a key Herdr does not bind SHALL keep its IDE meaning. When the IDE has no enabled action for that key, it SHALL go to Herdr.

#### Scenario: Brian opens Find in Files from the panel
- **GIVEN** the Herdr panel has focus
- **AND** Herdr does not bind `cmd+shift+f`
- **WHEN** Brian presses `cmd+shift+f`
- **THEN** IntelliJ opens Find in Files

#### Scenario: Brian toggles the Project panel from the Herdr panel
- **GIVEN** the Herdr panel has focus
- **AND** Herdr does not bind `cmd+1`
- **WHEN** Brian presses `cmd+1`
- **THEN** IntelliJ shows the Project panel

#### Scenario: Terminal keys without an IDE action reach Herdr
- **GIVEN** the Herdr panel has focus
- **WHEN** Brian types `git status`, presses Tab, the arrow keys, Enter and `ctrl+c`
- **THEN** Herdr receives each of those keys
- **AND** focus stays in the panel

### Requirement: Herdr config changes apply without restart
When Herdr's config file changes, the plugin SHALL use the new set of Herdr keys for key routing without an IDE restart.

#### Scenario: Brian binds a new key in Herdr
- **GIVEN** the Herdr panel is open
- **AND** `cmd+j` opens Insert Live Template in IntelliJ
- **WHEN** Brian adds a Herdr binding for `cmd+j` and saves the Herdr config
- **AND** Brian presses `cmd+j` in the Herdr panel
- **THEN** Herdr performs the new binding
- **AND** IntelliJ does not open Insert Live Template

### Requirement: Keys outside the panel are unaffected
When the Herdr panel does not have focus, the plugin SHALL NOT change what any key does.

#### Scenario: cmd+k in the editor
- **GIVEN** the editor has focus
- **WHEN** Brian presses `cmd+k`
- **THEN** IntelliJ opens the Commit tool window
