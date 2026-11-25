"""Record what the Python rule engine (worker/cv-eval/core.py) returns, so the Java port can be tested against it.

    python -I tools/parity_fixture.py <path to cv-eval>
    python -I tools/parity_fixture.py <path to cv-eval> --cv <your CV .pdf or .txt> --facts <truth.json>

The first form evaluates the sample CV and a deliberately broken CV against every posting in
src/test/resources/postings and writes src/test/resources/parity/expected.json (committed).
The second form does the same for your own CV and writes target/parity-local/ (git-ignored); then run
mvn test -Dtest=CvRuleEngineParityTest -Dparity.local=target/parity-local/expected.json
Fails if the rule files here differ from cv-eval's.
"""
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
TEST = ROOT / "src" / "test" / "resources"


def load_core(cv_eval: Path):
    for name in ("rules.json", "vocab.json"):
        ours = json.loads((RES / "rules" / name).read_text(encoding="utf-8"))
        theirs = json.loads((cv_eval / name).read_text(encoding="utf-8"))
        if ours != theirs:
            sys.exit(f"{name} differs from {cv_eval / name}: copy it over first")
    spec = importlib.util.spec_from_file_location("core", cv_eval / "core.py")
    core = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(core)
    return core


def main():
    args = sys.argv[1:]
    if len(args) not in (1, 5):
        sys.exit(__doc__)
    core = load_core(Path(args[0]))
    if len(args) == 5:
        opts = dict(zip(args[1::2], args[2::2]))
        local = ROOT / "target" / "parity-local"
        local.mkdir(parents=True, exist_ok=True)
        cv_in = Path(opts["--cv"])
        text = core.pdf_to_text(cv_in.read_bytes()) if cv_in.suffix.lower() == ".pdf" else cv_in.read_text(encoding="utf-8")
        (local / "cv.txt").write_text(text, encoding="utf-8", newline="")
        write(core, {"local": (local / "cv.txt", Path(opts["--facts"]))}, local / "expected.json")
        return
    cvs = {"sample": (RES / "samples" / "cv.sample.txt", RES / "samples" / "candidate.sample.json"),
           "broken": (TEST / "parity" / "cv-broken.txt", TEST / "parity" / "candidate-broken.json")}
    write(core, cvs, TEST / "parity" / "expected.json")


def rel(path):
    """Paths inside the project are stored relative to it, so the committed fixture holds no machine paths."""
    path = Path(path).resolve()
    return path.relative_to(ROOT).as_posix() if path.is_relative_to(ROOT) else str(path)


def write(core, cvs, out):
    cases = []
    for name, (cv_path, facts_path) in cvs.items():
        with open(cv_path, encoding="utf-8", newline="") as f:
            cv = f.read()
        core.TRUTH = json.loads(facts_path.read_text(encoding="utf-8"))
        for posting in sorted((TEST / "postings").glob("*.txt")):
            result = core.evaluate(cv, posting.read_text(encoding="utf-8"))
            cases.append({"cv": name, "posting": posting.name, "label": core.label(result["score"]),
                          "cv_path": rel(cv_path), "facts_path": rel(facts_path), **result})
    out.write_text(json.dumps(cases, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(cases)} cases -> {out.relative_to(ROOT)}")
    for c in cases:
        print(f"  {c['cv']:8} {c['posting']:45} {c['verdict']:16} {c['score']:3}")


if __name__ == "__main__":
    main()
