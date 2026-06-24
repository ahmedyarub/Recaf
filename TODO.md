# Recaf Improvement TODO

## Priority 1: Assembly ↔ Code View Mapping

### Layer 1: Bidirectional Navigation at Same Line
- [x] Create `BytecodeSourceMapper` core service (maps between bytecode and source lines via `LineNumberTable`)
- [x] Add `openAssemblerAtLine(PathNode, int)` to `Actions.java` (opens assembler at mapped line)
- [x] Add "View in Assembler at this line" to decompiler right-click context menu
- [x] Add "View in Decompiler at this line" to assembler right-click context menu
- [x] Add translation keys for new menu items

### Layer 2: Inline Comments / Gutter Tooltips
- [x] Create `ViewMappingConfig` — config for comment style (inline comments vs gutter tooltips)
- [x] Create `BytecodeAnnotationService` — builds source line to bytecode instruction summaries
- [x] Create `BytecodeAnnotationGutterFactory` — gutter tooltips for bytecode annotations
- [x] Integrate gutter factory into decompiler pane
- [x] Add configuration UI translation keys
- [ ] Implement inline bytecode comments in decompiled source view (future)
- [ ] Implement inline source comments in assembler view (future)

## Priority 2: Change Viewer
- [ ] Reimplement the disabled "View Changes" in File menu (`FileMenu.java:238`)
- [ ] Use `diffutils` dependency to compute class diffs
- [ ] Show before/after view of modified classes

## Priority 3: Deobfuscation Presets
- [ ] Load/save transformer preset configurations (`DeobfuscationWindow.java:274`)
- [ ] Bundle profiles for common obfuscators (Zelix, ProGuard, Allatori, Stringer)
- [ ] Separate transformers into categorized sub-sections (`DeobfuscationWindow.java:215`)
- [ ] Show deobfuscation preference for display order (`DeobfuscationWindow.java:579`)

## Priority 4: Evaluator Exception Control Flow
- [ ] Implement exception handler control flow in `Evaluator.java` (5 TODOs)
- [ ] Handle exception throwing in method invocations
- [ ] Find handler and set instruction pointer for exceptions

## Priority 5: Binary Viewer Completion
- [ ] Display content of selected PE model items (`PePane.java:81`)
- [ ] Display content of selected ELF model items (`ElfPane.java:74`)
- [ ] Better display name derivation for ELF headers (`ElfPane.java:84`)

## Priority 6: Fuzzy Tab Completion
- [ ] Implement CamelCase/fuzzy matching in assembler autocomplete (`AssemblerTabCompleter.java:449`)
- [ ] Mirror IntelliJ-style matching (e.g., `gNS` → `getNameSpace`)

## Priority 7: Low-Level View Search
- [ ] Add search bar to `JvmLowLevelPane` (`JvmLowLevelPane.java:175`)
- [ ] Support filtering by prefix and text content

## Priority 8: Configurable Deobfuscation Limits
- [ ] Make `MAX_STEPS` configurable in `CallResultInliningTransformer.java:47`
- [ ] Add config options for `StaticValueCollectionTransformer.java:113`

## Priority 9: Squiggle Error Indicators
- [ ] Reimplement squiggle drawing with `PixelCanvas` (`ProblemSquiggleGraphicFactory.java:87`)

## Priority 10: Workspace Diff Mode
- [ ] Structural diff between two JARs
- [ ] Show new/removed/changed classes
- [ ] Field/method signature diffs and bytecode diffs

## Priority 11: Navigation and Workspace State
- [x] Double-clicking on a search result should navigate to the decompile view and the found string
- [ ] Save and restore the workspace status including scroll position (e.g. which node was collapsed)
- [ ] Add a drop-down list with most recent searches that is also used as auto-complete in the search box

## Priority 12: Keyboard Shortcuts
- [x] Add keyboard shortcuts like F3 for search and Ctrl+O for open file

## Priority 13: UI and Editor Improvements
- [x] The search box should always be on top
- [ ] Add an option to wrap text in the editor
- [ ] Fix in-file search when there is a very long line (caret should move to the current result)
