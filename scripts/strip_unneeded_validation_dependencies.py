from pathlib import Path


def remove_dependency(source: str, group_id: str, artifact_id: str) -> str:
    marker = f"""        <dependency>
            <groupId>{group_id}</groupId>
            <artifactId>{artifact_id}</artifactId>
"""
    start = source.find(marker)
    if start < 0:
        raise SystemExit(f"missing dependency {group_id}:{artifact_id}")
    end = source.find("        </dependency>\n", start)
    if end < 0:
        raise SystemExit(f"unterminated dependency {group_id}:{artifact_id}")
    end += len("        </dependency>\n")
    return source[:start] + source[end:]


path = Path("pom.validation.xml")
source = path.read_text()
for coordinates in (
    ("org.enthusia", "enthusia-teleport"),
    ("org.enthusia", "EnthusiaTags"),
    ("com.github.sirblobman.api", "core"),
):
    source = remove_dependency(source, *coordinates)
path.write_text(source)
