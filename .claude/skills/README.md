# Skills

Skills are reusable prompts that teach Claude specific patterns for Java development.

## Structure Convention

Each skill folder contains:

| File | Purpose | Audience |
|------|---------|----------|
| `SKILL.md` | Instructions for Claude | AI (loaded with `view`) |
| `README.md` | Documentation, examples, tips | Humans (onboarding) |

## Available Skills

Skills are tailored to this repository (a Java 8-compatible SDK library, Gradle multi-module,
release-please automation). Repo-specific hard rules live in `.claude/rules/` — skills apply them.

### Workflow
| Skill | Description |
|-------|-------------|
| [git-commit](git-commit/) | Conventional commit messages wired to release-please semantics |

### Code Quality
| Skill | Description |
|-------|-------------|
| [java-code-review](java-code-review/) | Systematic Java code review checklist |
| [concurrency-review](concurrency-review/) | Thread safety, race conditions, shared SDK state |
| [performance-smell-detection](performance-smell-detection/) | Code-level performance smells (streams, boxing, regex) |
| [test-quality](test-quality/) | JUnit 5 tests in the repo house style (Verify:, Given/When/Then) |
| [gradle-dependency-audit](gradle-dependency-audit/) | Audit Gradle dependencies for updates and vulnerabilities |
| [security-audit](security-audit/) | OWASP Top 10, input validation, injection prevention |

### Architecture & Design
| Skill | Description |
|-------|-------------|
| [architecture-review](architecture-review/) | Macro-level review: packages, modules, layers, boundaries |
| [solid-principles](solid-principles/) | S.O.L.I.D. principles with Java examples |
| [design-patterns](design-patterns/) | Factory, Builder, Strategy, Observer, Decorator, etc. |
| [clean-code](clean-code/) | DRY, KISS, YAGNI, naming, refactoring |
| [logging-patterns](logging-patterns/) | SLF4J in a library context: levels, parameterized messages, what not to log |

### Removed as Not Applicable Here

`spring-boot-patterns`, `jpa-patterns`, `api-contract-review`, `java-migration`,
`changelog-generator` (CHANGELOG is owned by release-please), `issue-triage`, and the Maven
variant of the dependency audit. Restore from git history if the stack changes
(e.g. a Spring Boot starter module would bring `spring-boot-patterns` back).

## Adding a New Skill

### Before You Start

Validate your skill idea against existing skills:

- [ ] **No significant overlap** - Check the table above for similar skills
- [ ] **Clear level** - Micro (functions) / Meso (classes) / Macro (packages) / Framework / Cross-cutting
- [ ] **Clear type** - Audit (review existing code) or Template (show how to write)
- [ ] **Unique value** - What does it add that doesn't exist?
- [ ] **Focused scope** - Can be applied in one session (<15 checklist items)
- [ ] **Fits this repo** - Applies to a Java 8 SDK library on Gradle (no Spring/JPA/REST endpoints here)

### Implementation Steps

1. Create folder: `.claude/skills/<skill-name>/`
2. Create `SKILL.md` with instructions for Claude
3. Create `README.md` with human documentation (use existing READMEs as template)
4. Update this table

## Usage

Skills are automatically loaded by Claude Code based on context. You can also invoke them directly:

```bash
# Automatic - Claude detects when to use skills
> "Commit these changes"        # Loads git-commit
> "Review this code for SOLID"  # Loads solid-principles

# Manual - invoke with slash command
> /git-commit
> /solid-principles
```

## Learn More

- [Claude Code Skills Documentation](https://code.claude.com/docs/en/skills) - Official guide on creating and using skills
