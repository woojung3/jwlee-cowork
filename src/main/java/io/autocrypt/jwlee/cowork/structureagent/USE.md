# StructureAgent Usage Guide

The `StructureAgent` provides a deep analysis of a project's internal code structure, focusing on class/module dependencies, architectural hubs, and design violations.

## Prerequisites

To use this agent, ensure the following are installed on your system:

1.  **Python 3.10+**: Required for static analysis.
2.  **ripgrep (rg)**: Required for high-speed code scanning.
    ```bash
    sudo apt install ripgrep
    ```
3.  **Python dependencies**: Installed in the project's `.venv`.
    ```bash
    ./.venv/bin/pip install networkx
    ```

## CLI Command

You can use the `structure-analyze` command in the Spring Shell.

### Parameters
- `path`: (Optional) The root path of the project to analyze. Defaults to the current directory (`.`).
- `context`: (Optional) Additional context or specific instructions for the analysis.
- `--show-prompts`: (Optional) Set to `true` to see the prompts sent to the LLM.
- `--show-responses`: (Optional) Set to `true` to see the raw responses from the LLM.

### Example
```bash
shell:> structure-analyze --path ./pnc-project --context "Check for circular dependencies in the CA module."
```

## How it Works
1.  **Stage 0 (Context Priming)**: Invokes `ArchitectureAgent`, `ERDAgent`, and `ApiAgent` to understand the domain and entry points.
2.  **Stage 1 (Extraction)**: Runs `scripts/structure_analyzer.py` via the project's `venv` to extract raw class and module dependency data.
3.  **Stage 2 (Interpretation)**: The LLM analyzes the raw data to identify core hubs, logical modules, and architectural violations.
4.  **Stage 3 (Synthesis)**: Generates a comprehensive Markdown report in Korean, including:
    - **Module-level Mermaid Diagram**
    - **Class-level Mermaid Diagram**
    - **Violation Report** (Circular dependencies, layer violations)
    - **Refactoring Recommendations**

## Output
The report provides a professional architectural audit, highlighting structural risks and suggesting improvements for maintainability.
