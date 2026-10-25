"""Required evidence and forbidden claims are defined independently by each case."""

def career_quality(expected, answer: str, tags: list[str]) -> float:
    if not isinstance(expected, dict) or not answer or len(answer.strip()) < 8:
        return 0.0
    normalized = answer.casefold()
    if any(not any(word.casefold() in normalized for word in group) for group in expected.get("anyOf", [])):
        return 0.0
    if any(word.casefold() in normalized for word in expected.get("forbidden", [])):
        return 0.0
    if expected.get("minLength") and len(answer) < expected["minLength"]:
        return 0.0
    return 1.0
