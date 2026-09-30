## ADDED Requirements

### Requirement: The shared session is the default
For a project without its own Herdr session, the panel SHALL attach to Herdr's default session, the same session a plain `herdr` in Ghostty attaches to.

#### Scenario: Brian opens the panel in a fresh project
- **GIVEN** Ghostty shows Herdr's default session with workspace `work`
- **AND** the project `herdr-idea` has no project session
- **WHEN** Brian opens the Herdr panel in `herdr-idea`
- **THEN** the panel shows workspace `work`

#### Scenario: Both clients show the same state
- **GIVEN** the Herdr panel and Ghostty both show the shared session
- **WHEN** Brian creates a tab in Ghostty
- **THEN** the new tab appears in the panel

### Requirement: Brian can create a project session
The panel header SHALL offer an action that creates a Herdr session that belongs to the open project, starts it in the project's root folder and switches the panel to it. The project session's name SHALL be derived from the project so the same project always maps to the same session.

#### Scenario: Brian creates a project session
- **GIVEN** the panel shows the shared session in project `herdr-idea`
- **WHEN** Brian chooses `New project session` in the panel header
- **THEN** the panel shows a new Herdr session whose first pane starts in the `herdr-idea` root folder
- **AND** the shared session is unchanged

#### Scenario: The project already has a session
- **GIVEN** project `herdr-idea` already has a project session
- **WHEN** Brian looks at the panel header
- **THEN** it offers to switch to the project session instead of creating another one

### Requirement: Brian can switch between shared and project session
The panel header SHALL show which session the panel displays and SHALL let Brian switch between the shared session and the project session without closing the panel.

#### Scenario: Brian switches to the shared session
- **GIVEN** the panel shows the project session of `herdr-idea`
- **WHEN** Brian picks the shared session in the panel header
- **THEN** the panel shows the shared session
- **AND** the project session keeps running

### Requirement: The panel remembers the session per project
The plugin SHALL remember per project which session the panel showed last and SHALL attach to that session when the project reopens.

#### Scenario: Brian reopens a project that used its project session
- **GIVEN** the panel showed the project session when Brian closed project `herdr-idea`
- **WHEN** Brian reopens `herdr-idea`
- **THEN** the panel shows the project session with the panes and agents it had

### Requirement: A project session outlives the IDE
A project session SHALL keep running, with its panes and agents, after the project closes or the IDE quits, and SHALL be reachable from Ghostty with `herdr --session <name>`.

#### Scenario: Agents continue after Brian quits IntelliJ
- **GIVEN** an agent is working in the project session of `herdr-idea`
- **WHEN** Brian quits IntelliJ IDEA
- **THEN** `herdr session list` still lists the project session
- **AND** the agent keeps working

#### Scenario: The project session vanished while IntelliJ was closed
- **GIVEN** the panel last showed the project session of `herdr-idea`
- **AND** Brian stopped that session from Ghostty while IntelliJ was closed
- **WHEN** Brian reopens `herdr-idea`
- **THEN** the panel says the project session no longer exists
- **AND** offers to create it again or switch to the shared session
