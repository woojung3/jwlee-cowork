# DSL-StructureAgent.md

## 0. Header
This DSL defines the `StructureAgent`, which analyzes class/module dependencies, identifies core hubs, and detects architecture violations. It follows the Embabel Agent DSL Guide (v1.0).

## 1. Metadata
```yaml
agent:
  name: StructureAgent
  description: "Analyzes code structure, module dependencies, and architecture integrity."
  timezone: "Asia/Seoul" # REQUIRED
  language: "Korean" # REQUIRED: Use Korean for all user-facing outputs and reports.
```

## 2. Dependencies
- `PromptProvider` (REQUIRED for Jinja)
- `BashTool` (To run venv python script)
- `FileReadTool`
- `CoworkLogger`
- `AgentPlatform` (For cross-agent priming)

## 3. Domain Objects (DTOs)

### StructureRequest
- `path`: String # Root path of the project to analyze
- `context`: String # Additional context

### HubClassInfo
- `className`: String
- `package`: String
- `incomingDependencies`: int # Number of classes depending on this
- `outgoingDependencies`: int # Number of classes this depends on
- `role`: String # (e.g., Domain Core, Engine, Utility, API Entry)

### DependencyViolation
- `source`: String
- `target`: String
- `type`: String # (e.g., Circular Dependency, Layer Violation)
- `description`: String

### StructureAnalysisResult
- `modules`: Map<String, String> # Module name -> Description or primary package path
- `coreHubs`: List<HubClassInfo>
- `violations`: List<DependencyViolation>
- `mermaidDiagrams`: Map<String, String> # (Module-level, Core-Class-level)

### FinalStructureReport
- `report`: String # Markdown formatted report in Korean
- `status`: String

## 4. Workflow States

### State: StructurePrimingState
- `request`: StructureRequest
- `domainContext`: String # Merged summary from ERD, API, and Architecture agents

### State: RawDataExtractionState
- `request`: StructureRequest
- `domainContext`: String
- `rawJson`: String # JSON output from scripts/structure_analyzer.py

### State: InterpretationState
- `request`: StructureRequest
- `analysis`: StructureAnalysisResult

## 5. Actions

### Action: prepareStructureContext (Stage 0)
- **Goal**: Prime context using other specialized agents.
- **Input**: `StructureRequest`
- **Output**: `StructurePrimingState`
- **Logic**: 
  1. Invoke `ArchitectureAgent` for macro-structure.
  2. Invoke `ERDAgent` to identify core data entities.
  3. Invoke `ApiAgent` to identify system entry points.
  4. Merge results into a `domainContext`.

### Action: extractRawDependencies (Stage 1)
- **Goal**: Run static analysis via Python script.
- **Input**: `StructurePrimingState`
- **Output**: `RawDataExtractionState`
- **Logic**: 
  1. Execute `.venv/bin/python scripts/structure_analyzer.py <path>`.
  2. Capture JSON output.
- **Tool**: `BashTool`

### Action: interpretStructure (Stage 2 - Map Phase)
- **Goal**: Use LLM to give meaning to raw dependency data.
- **Input**: `RawDataExtractionState`
- **Output**: `InterpretationState`
- **Logic**: 
  1. Pass the raw JSON and domainContext to LLM.
  2. LLM identifies "Core Hubs" based on domain importance.
  3. LLM detects architecture violations (e.g., lower layers referencing upper layers).
  4. LLM generates Mermaid diagrams.
- **LLM Configuration**:
  - `role`: performant
  - `template`: `agents/structure/interpret-graph.jinja`

### Action: synthesizeStructureReport (Stage 3 - Reduce Phase)
- **AchievesGoal**: Generate final comprehensive report.
- **Input**: `InterpretationState`
- **Output**: `FinalStructureReport`
- **Logic**: Format analysis into a professional Korean architecture report.
- **LLM Configuration**:
  - `role`: performant
  - `template`: `agents/structure/synthesize-report.jinja`

## 6. Implementation Guidelines

### 6.1 Domain Focus
- Prioritize classes identified as "Entities" (from ERDAgent) and "Controllers" (from ApiAgent).
- Focus on relationships involving these core components.

### 6.2 Visualization
- **Module Diagram**: Show dependencies between identified modules (high-level).
- **Core Class Diagram**: Show relationships between top 10-15 most "important" classes.

### 6.3 Performance
- Truncate JSON if it exceeds token limits, focusing on the most relevant packages.
- Use `.withoutThinking()` for interpretation if using Gemini 2.0+ models.
