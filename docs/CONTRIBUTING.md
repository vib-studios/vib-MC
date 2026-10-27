## AI Agent Disclosure

If you use an AI coding agent to make changes to this repository, you must disclose which AI model or models were used.

### Commit disclosure format

At the end of the commit message, append the following:

```text
AI-Models: (COMPANY) (MODEL)
AI-Subagents: (COMPANY) (MODEL); (COMPANY) (MODEL)
```

**Format:**
- `COMPANY`: The organization or provider behind the model, written in uppercase where practical (e.g., `ANTHROPIC`, `OPENAI`, `GOOGLE`).
- `MODEL`: The actual model name and version used (e.g., `CLAUDE OPUS 5.5`, `GPT-6`, `GEMINI 3 PRO`).
- `AI-Subagents`: List every subagent model used to perform work on the contribution, separated by semicolons. If no subagents were used, write `AI-Subagents: None`.

### Example

```text
Fix player visibility regression

Correct entity visibility handling and add a regression test.

AI-Models: ANTHROPIC (CLAUDE OPUS 5.5)
AI-Subagents: OPENAI (GPT-6); GOOGLE (GEMINI 3 PRO)
```

### Requirements

- Disclose the actual model used, not just the agent application or IDE name.
- Include all models that materially contributed to implementing, debugging, reviewing, or testing the change.
- If subagents were used, list their models separately under `AI-Subagents`.
- Do not invent model names or versions. If the exact model is unknown, state `Unknown` rather than guessing.
- If no AI coding agent was used, this disclosure is not required.
- Keep the disclosure at the very end of the commit message, after the summary and body.
- Include the same disclosure in the pull request description when AI agents materially contributed to the changes.

The purpose of this disclosure is transparency, so we know whether it was made by an janky model, or an good one. Even if u use a not so good one, its okay. Just make sure it works.
