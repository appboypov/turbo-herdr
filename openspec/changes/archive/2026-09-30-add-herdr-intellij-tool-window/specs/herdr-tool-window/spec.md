## ADDED Requirements

### Requirement: Herdr panel docks in the IDE
The plugin SHALL register a tool window named `Herdr` that docks in the IDE's side or bottom area like the Project tool window, with its own stripe button, and the IDE SHALL remember where the user placed it.

#### Scenario: Brian opens the Herdr panel
- **GIVEN** Brian has a project open in IntelliJ IDEA with the plugin installed
- **WHEN** Brian clicks the `Herdr` stripe button
- **THEN** the Herdr panel opens docked beside the editor
- **AND** it shows the live Herdr screen

#### Scenario: The panel keeps its place
- **GIVEN** Brian has moved the Herdr panel to the right side
- **WHEN** Brian restarts IntelliJ IDEA and reopens the project
- **THEN** the Herdr panel sits on the right side

### Requirement: The panel shows a live Herdr client
The panel SHALL run a `herdr` client in a pseudo-terminal and draw its screen with the text, colours, attributes and cursor Herdr produces, updating as Herdr redraws.

#### Scenario: An agent's output appears as it streams
- **GIVEN** the Herdr panel shows a pane with a working Claude Code agent
- **WHEN** the agent prints new output
- **THEN** the new output appears in the panel without Brian interacting with it

#### Scenario: Wide and styled characters render correctly
- **GIVEN** a Herdr pane prints emoji, CJK characters, box-drawing characters, bold, italic, underline and 24-bit colour text
- **WHEN** the panel draws that pane
- **THEN** each character occupies its correct number of cells
- **AND** each style is visible as Ghostty would render it

### Requirement: The panel follows the IDE's look
The panel SHALL draw with the IDE's console font, font size and line spacing, and with the colours of the IDE's active colour scheme for console output, including the 16 ANSI colours, and SHALL redraw when the user changes those settings.

#### Scenario: Brian switches the IDE theme
- **GIVEN** the Herdr panel is open with the Darcula theme
- **WHEN** Brian switches the IDE to the Light theme
- **THEN** the panel redraws with the Light theme's console background, foreground and ANSI colours

#### Scenario: Brian changes the console font size
- **GIVEN** the Herdr panel is open
- **WHEN** Brian sets the console font size from 13 to 16
- **THEN** the panel redraws with size-16 text
- **AND** Herdr receives the new column and row count

### Requirement: Herdr follows the panel's size
The plugin SHALL tell Herdr the panel's size in columns and rows whenever the panel is resized, so Herdr lays out its sidebar and panes to fill the panel.

#### Scenario: Brian widens the panel
- **GIVEN** the Herdr panel is 80 columns wide
- **WHEN** Brian drags the panel edge until it is 120 columns wide
- **THEN** Herdr redraws its layout across 120 columns

### Requirement: Mouse works as in Ghostty
The panel SHALL send mouse clicks, drags and wheel scrolling to Herdr using the mouse mode Herdr requests, so Brian can click Herdr's sidebar, tabs and panes and scroll pane output.

#### Scenario: Brian clicks a workspace in Herdr's sidebar
- **GIVEN** the Herdr panel shows two workspaces in Herdr's sidebar
- **WHEN** Brian clicks the second workspace
- **THEN** Herdr switches to the second workspace

#### Scenario: Brian scrolls a pane
- **GIVEN** a Herdr pane has more output than fits on screen
- **WHEN** Brian scrolls up with the mouse wheel over that pane
- **THEN** the pane shows earlier output

### Requirement: Copy and paste
The panel SHALL paste clipboard text into Herdr with the platform's paste key, using bracketed paste when Herdr enables it, and SHALL copy selected panel text with the platform's copy key, unless Herdr binds that key.

#### Scenario: Brian pastes a prompt
- **GIVEN** Brian's clipboard holds a three-line prompt
- **AND** a Claude Code agent is waiting for input in the focused Herdr pane
- **WHEN** Brian presses `cmd+v` in the panel
- **THEN** the agent receives the three lines as one paste

#### Scenario: Brian copies output
- **GIVEN** Brian has selected a line of agent output in the panel
- **WHEN** Brian presses `cmd+c`
- **THEN** the clipboard holds that line

### Requirement: Herdr exit and missing Herdr are visible
When the `herdr` client cannot start or exits, the panel SHALL show what happened, including the `herdr` path it tried or the exit code, and SHALL offer to start the client again. The plugin SHALL find `herdr` on the user's login-shell `PATH` and SHALL let the user set its path in the plugin settings.

#### Scenario: Herdr is not installed
- **GIVEN** no `herdr` binary is on Brian's `PATH` and no path is set in the plugin settings
- **WHEN** Brian opens the Herdr panel
- **THEN** the panel says `herdr` was not found and names the locations it searched
- **AND** offers a link to the plugin settings

#### Scenario: Brian detaches from Herdr
- **GIVEN** the Herdr panel shows a running Herdr client
- **WHEN** the Herdr client exits
- **THEN** the panel says the client exited and shows its exit code
- **AND** offers a `Reconnect` action that starts a new client on the same session

### Requirement: Closing the panel leaves Herdr running
Hiding the panel, closing the project or quitting the IDE SHALL end only the plugin's `herdr` client; the Herdr server, its sessions, panes and agents SHALL keep running.

#### Scenario: Brian quits IntelliJ while agents work
- **GIVEN** the Herdr panel shows a session where two agents are working
- **WHEN** Brian quits IntelliJ IDEA
- **THEN** both agents keep working
- **AND** Ghostty's Herdr window still shows them
