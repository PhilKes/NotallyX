import os
import xml.etree.ElementTree as ET
from pathlib import Path
import re

BASE_PATH = Path("app/src/main/res")
BASE_DIR = BASE_PATH / "values"
README_FILE = Path("README.md")

def extract_keys(xml_path: Path):
    """Extract unique translation keys from strings.xml including <string>, <plurals>, <string-array>."""
    if not xml_path.exists():
        return set()
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        keys = set()
        for child in root:
            if child.tag in {"string", "plurals", "string-array"}:
                name = child.attrib.get("name")
                if name:
                    keys.add(name)
        return keys
    except ET.ParseError:
        print(f"Warning: Failed to parse {xml_path}")
        return set()

def generate_coverage_table():
    base_keys = extract_keys(BASE_DIR / "strings.xml")
    total_keys = len(base_keys)
    if total_keys == 0:
        print("No base keys found in values/strings.xml")
        return ""

    coverage_rows = []

    # Include base language explicitly
    coverage_rows.append(("English", 100, len(base_keys), total_keys))

    # Find other language folders like values-de, values-fr etc.
    for lang_dir in sorted(BASE_PATH.glob("values-*")):
        lang = lang_dir.name.removeprefix("values-")
        xml_file = lang_dir / "strings.xml"
        keys = extract_keys(xml_file)
        translated_count = len(base_keys.intersection(keys))
        percent = int((translated_count / total_keys) * 100)
        coverage_rows.append((lang.capitalize(), percent, translated_count, total_keys))

    # Build markdown table
    table_lines = [
        "| Language | Coverage |",
        "|----------|----------|"
    ]
    for lang, percent, done, total in coverage_rows:
        table_lines.append(f"| {lang} | {percent}% ({done}/{total}) |")

    return "\n".join(table_lines)

def update_readme_section():
    if not README_FILE.exists():
        print("README.md not found.")
        return

    content = README_FILE.read_text(encoding="utf-8")

    start_tag = "<!-- translations:start -->"
    end_tag = "<!-- translations:end -->"

    new_section = start_tag + "\n" + generate_coverage_table() + "\n" + end_tag

    pattern = re.compile(f"{re.escape(start_tag)}.*?{re.escape(end_tag)}", re.DOTALL)

    if pattern.search(content):
        updated_content = pattern.sub(new_section, content)
    else:
        # Append at end if tags not found
        updated_content = content.strip() + "\n\n" + new_section

    README_FILE.write_text(updated_content, encoding="utf-8")
    print("README.md updated with translation coverage.")

if __name__ == "__main__":
    update_readme_section()
