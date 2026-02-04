# OpenCode Agent Guide for crypto-scout-analyst

This guide explains how to use the configured OpenCode agents and skills for developing, reviewing, and documenting the crypto-scout-analyst microservice.

## Quick Start

```bash
# Install OpenCode (if not already installed)
# See: https://opencode.ai/docs/installation

# Navigate to project
cd /path/to/crypto-scout-analyst

# Start OpenCode
opencode
```

## Available Agents

### 1. Developer Agent (`developer`)

**Purpose**: Primary agent for implementing features, fixing bugs, and maintaining the analyst microservice.

**Mode**: Primary (use Tab to switch to this agent)

**Capabilities**:
- Full read/write access to files
- Can execute bash commands (build, test)
- Access to all project skills

**When to Use**:
- Implementing new stream processors
- Adding analysis indicators
- Fixing data transformation issues
- Refactoring code
- Running builds and tests

**Example Prompts**:
```
Add a new technical indicator processor using AnalystEngine

Implement a risk assessment transformer for Bybit data

Fix the stream offset tracking in CryptoScoutService

Add support for real-time alert generation
```

### 2. Code Reviewer Agent (`reviewer`)

**Purpose**: Reviews code for quality, security, and adherence to project conventions.

**Mode**: Subagent (invoke with `@reviewer`)

**Capabilities**:
- Read-only access to files
- Can search and grep codebase
- Access to code style skills

**When to Use**:
- Before merging changes
- Checking stream processing logic
- Verifying style compliance
- Security audits

**Example Prompts**:
```
@reviewer Review the AnalystTransformer implementation

@reviewer Check if DataService follows async patterns

@reviewer Audit the stream consumer error handling

@reviewer Review the offset management logic
```

### 3. Technical Writer Agent (`writer`)

**Purpose**: Creates and maintains documentation for the analyst service.

**Mode**: Subagent (invoke with `@writer`)

**Capabilities**:
- Read/write access to documentation files
- Can read source code for reference
- Access to documentation skills

**When to Use**:
- Updating README.md
- Writing architecture documentation
- Creating analysis guides
- Maintaining AGENTS.md

**Example Prompts**:
```
@writer Update README.md with the new analysis features

@writer Document the stream processing pipeline

@writer Create a guide for adding new indicators

@writer Document the alert generation system
```

## Available Skills

Skills are loaded on-demand by agents. View available skills in your session.

### java-microservice
Microservice development patterns including ActiveJ services, configuration, and health endpoints.

### java-code-style
Java 25 code style conventions for naming, imports, error handling, and testing patterns.

### stream-processing
Stream processing patterns including transformers, data services, and offset management.

## Usage Patterns

### Feature Development Workflow

1. **Start with the developer agent** (Tab to switch if needed)
2. Describe the feature you want to implement
3. The agent will use skills to understand project conventions
4. Review generated code
5. **Invoke reviewer**: `@reviewer Check the new implementation`
6. Address feedback
7. **Invoke writer**: `@writer Update documentation for the new feature`

### Code Review Workflow

```
@reviewer Review src/main/java/com/github/akarazhev/cryptoscout/analyst/stream/AnalystTransformer.java

@reviewer Check all files changed in the last commit for style violations

@reviewer Audit the error handling across the analyst service
```

### Documentation Workflow

```
@writer Update the README with current analysis capabilities

@writer Add examples for using the AnalystEngine

@writer Create a troubleshooting section for stream issues
```

## Agent Navigation

| Action | Keybind |
|--------|---------|
| Switch primary agent | `Tab` |
| Invoke subagent | `@agentname message` |
| Cycle child sessions | `<Leader>+Right` |
| Cycle back | `<Leader>+Left` |

## Configuration

Agents are configured in `.opencode/agents/`. To customize:

### Modify Agent Behavior
Edit the agent's markdown file (e.g., `.opencode/agents/developer.md`):
- Adjust `temperature` for creativity vs. determinism
- Enable/disable specific `tools`
- Modify the system prompt

### Add New Skills
Create a new skill in `.opencode/skills/<skill-name>/SKILL.md`:
```yaml
---
name: skill-name
description: Brief description for agent discovery
license: MIT
compatibility: opencode
---

## What I Do
...

## When to Use Me
...
```

### Override Model
Add `model` to agent frontmatter:
```yaml
model: zai-coding-plan/glm-4.7
```

## Best Practices

### For Development
1. Always run tests after changes: `mvn test`
2. Use `@reviewer` before finalizing changes
3. Keep changes focused and incremental
4. Follow the code style skill guidelines
5. Test stream processing logic thoroughly

### For Reviews
1. Be specific about what to review
2. Ask for specific aspects (async patterns, error handling, style)
3. Request actionable feedback

### For Documentation
1. Keep examples working and tested
2. Update docs alongside code changes
3. Use consistent formatting
4. Document stream processing pipelines with diagrams

## Troubleshooting

### Agent Not Found
Verify agent files exist in `.opencode/agents/`:
```bash
ls -la .opencode/agents/
```

### Skill Not Loading
1. Check SKILL.md is uppercase
2. Verify frontmatter has `name` and `description`
3. Ensure skill name matches directory name

### Permission Issues
Check agent's `tools` configuration in frontmatter:
```yaml
tools:
  write: true
  edit: true
  bash: true
```

## File Structure

```
.opencode/
├── agents/
│   ├── developer.md    # Primary development agent
│   ├── reviewer.md     # Code review subagent
│   └── writer.md       # Documentation subagent
├── skills/
│   ├── java-microservice/
│   │   └── SKILL.md    # Microservice patterns
│   ├── java-code-style/
│   │   └── SKILL.md    # Code style conventions
│   └── stream-processing/
│       └── SKILL.md    # Stream processing patterns
└── OPENCODE_GUIDE.md   # This guide
```

## Additional Resources

- [OpenCode Documentation](https://opencode.ai/docs/)
- [Agent Configuration](https://opencode.ai/docs/agents/)
- [Skills Reference](https://opencode.ai/docs/skills)
- [Project README](../README.md)
- [Project AGENTS.md](../AGENTS.md)
