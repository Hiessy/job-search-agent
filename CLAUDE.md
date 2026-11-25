# job-search-agent

Portfolio project for github.com/Hiessy: a Spring Boot 3 / Spring AI job-search agent where the LLM picks its own
tools, behind guardrails and a human approval step. See README.md for what it does and how to run it.

## Build

- Local JDK is 23 (`C:\Program Files\java\jdk-23.0.1`); the project targets Java 21. Maven 3.9.9 defaults to JDK 17,
  so set `JAVA_HOME` to the JDK 23 folder before `mvn`. CI and the Docker image use Temurin 21.
- `mvn verify`: unit tests plus Testcontainers integration tests (`*IT`). Needs Docker Desktop running.
- `mvn -Peval verify`: the live-model eval (`AgentEvalIT`), needs Ollama with qwen2.5:7b. Slow (minutes).

## Rules

- Package and group id: `com.hiessy.jobagent`.
- The rule engine in `cv/` is a port of `worker/cv-eval/core.py`. Never change its behaviour on its own: change the
  Python first, copy `rules.json`/`vocab.json`, rerun `python -I tools/parity_fixture.py ../../worker/cv-eval`, then
  make `CvRuleEngineParityTest` pass.
- Tools must not invent facts. `draft_cover_note` is a template on purpose; numbers come from the user.
- Writes go through `ApprovalService.request*`; there is no tool that approves.
- The real CV and `truth.json` are personal data: they live outside this repo or in git-ignored `data/`.
  Only the fictional samples (Alex Rivera, Jordan Lee) are committed.
- API keys come from environment variables only.
- Don't create the GitHub repo or push without the user's OK.
