## ADDED Requirements

### Requirement: Supported IDE builds
The plugin SHALL install and run on IntelliJ Platform IDEs from build 261 (2026.1) onward, and SHALL NOT depend on the IDE's own terminal plugin or on an IDE-bundled Ghostty engine.

#### Scenario: Brian installs on IntelliJ IDEA 2026.1.2
- **GIVEN** IntelliJ IDEA Ultimate 2026.1.2 (build `IU-261.24374.151`)
- **WHEN** Brian installs the plugin from JetBrains Marketplace
- **THEN** the Herdr panel works without enabling any registry key or other plugin

### Requirement: Supported operating systems and architectures
The plugin SHALL ship its own `libghostty-vt` build for macOS arm64, macOS x64, Linux x64 and Linux arm64, and SHALL load the one matching the running machine.

#### Scenario Outline: The panel works on each supported platform
- **GIVEN** an IntelliJ Platform 2026.1 IDE on `<platform>`
- **AND** `herdr` is installed
- **WHEN** the user opens the Herdr panel
- **THEN** the panel shows the live Herdr screen

| platform |
| --- |
| macOS arm64 |
| macOS x64 |
| Linux x64 |
| Linux arm64 |

#### Scenario: Unsupported platform
- **GIVEN** an IntelliJ Platform IDE on Windows
- **WHEN** the user opens the Herdr panel
- **THEN** the panel says the platform is not supported and names the supported ones

### Requirement: Marketplace distribution
The plugin SHALL be published on JetBrains Marketplace as one plugin archive that contains every supported platform's native library, and SHALL pass the IntelliJ Plugin Verifier for its supported build range.

#### Scenario: A release reaches Marketplace
- **GIVEN** a tagged plugin release
- **WHEN** the release is published
- **THEN** the plugin appears on JetBrains Marketplace for builds 261 and later
- **AND** the Plugin Verifier reports no compatibility problems for that range
