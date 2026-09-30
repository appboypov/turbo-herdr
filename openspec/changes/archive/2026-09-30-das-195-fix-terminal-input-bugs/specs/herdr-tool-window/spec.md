## ADDED Requirements

### Requirement: The panel shows the latest screen after output stops
After the Herdr client stops writing output, the panel SHALL show the screen as it is after the last byte written, without waiting for further output or a keypress.

#### Scenario: Brian switches Herdr tabs
- **GIVEN** the Herdr panel shows tab A, whose rows all differ from tab B's rows
- **WHEN** Brian switches Herdr to tab B and presses no key
- **THEN** within one second every row of the panel shows tab B's content
- **AND** no row shows tab A's content

#### Scenario: Output that arrives in bursts
- **GIVEN** the Herdr panel is open
- **WHEN** the program in the focused pane writes many output chunks in quick succession and then stops
- **THEN** the panel shows the screen that includes the last chunk

### Requirement: Text and caret share one cell grid
The panel SHALL draw every character, its background, the selection and the caret at the same cell position the terminal reports, at every column and for every font style.

#### Scenario: Brian types a long prompt
- **GIVEN** the Herdr panel has focus with the IDE's console font
- **WHEN** Brian types a line that wraps past the panel's width
- **THEN** the caret is drawn directly after the last typed character
- **AND** each character is drawn inside its own cell at every column

#### Scenario: Bold text keeps its cells
- **GIVEN** a row holds bold text followed by plain text
- **WHEN** the panel draws that row
- **THEN** each character starts at its cell's left edge
- **AND** the plain text after the bold text starts where its cells begin
