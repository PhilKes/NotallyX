#!/usr/bin/python3

import os
import sys
import pandas as pd
import xml.etree.ElementTree as ET
import xml.dom.minidom

def print_help():
    help_message = """
Usage:
    python import.py [INPUT_XLSX_FILE] [OUTPUT_APP_SRC_MAIN_RES_FOLDER]

Description:
    This script converts an Excel file containing string translations into
    Android-compatible strings.xml files.

    - INPUT_XLSX_FILE: Path to the Excel file to process (default: 'translations.xlsx').
    - OUTPUT_APP_SRC_MAIN_RES_FOLDER: Path to the output directory where the
      strings.xml files will be generated into ('values-{LANGUAGE}' subfolders) (default: './app/src/main/res/').

Notes:
    - The Excel file should have the following columns:
        * "Key": The name of the string resource.
        * "Translatable": A boolean indicating whether the string is translatable.
        * Language columns ("values-{LANGUAGE}"): Columns representing different language translations.
    - Plural keys should have the format `key_PLURALS_{quantity}` (e.g., `app_name_PLURALS_one`).
    - Non-translatable strings will include the attribute translatable="false" in the generated XML files.

Example:
    python import.py translations.xlsx ./app/src/main/res/
"""
    print(help_message)

def read_excel_file(input_file):
    print(f"Loading Excel file: {input_file}")
    try:
        df = pd.read_excel(input_file, keep_default_na=False, na_values=[''])
        print(f"Excel file loaded successfully. Found {len(df)} rows and {len(df.columns)} columns.")
        return df
    except Exception as e:
        print(f"Error loading Excel file: {e}")
        sys.exit(1)

def pretty_print_xml(element):
    xml_str = ET.tostring(element, 'utf-8')
    dom = xml.dom.minidom.parseString(xml_str)
    return dom.toprettyxml(indent="  ")

def process_strings(df, output_dir):
    for column in df.columns:
        if column not in ["Key", "Translatable"]:
            print(f"Processing language: {column}")
            root = ET.Element("resources")

            for _, row in df.iterrows():
                string_name = row["Key"]
                string_value = row[column]
                is_not_translatable = row["Translatable"] == False

                if should_skip_row(column, string_value, is_not_translatable, df):
                    continue

                if "_PLURALS_" in string_name:
                    handle_plurals(root, string_name, string_value)
                else:
                    handle_regular_string(root, string_name, string_value, is_not_translatable)

            write_xml_to_file(root, column, output_dir)
    return len(df.columns)

def should_skip_row(column, string_value, is_not_translatable, df):
    if is_not_translatable and column != df.columns[2]:  # Default language check
        return True
    if pd.isna(string_value) or (string_value.startswith("Key:") and "_PLURALS_" in string_value):
        return True
    return False

def handle_plurals(root, string_name, string_value):
    plural_base_name = string_name.split("_PLURALS_")[0]
    plural_quantity = string_name.split("_PLURALS_")[1]

    plural_group_elem = root.find(f".//plurals[@name='{plural_base_name}']")
    if plural_group_elem is None:
        plural_group_elem = ET.Element("plurals", name=plural_base_name)
        root.append(plural_group_elem)

    item_elem = ET.SubElement(plural_group_elem, "item", quantity=plural_quantity)
    item_elem.text = string_value

def handle_regular_string(root, string_name, string_value, is_not_translatable):
    string_attributes = {"name": string_name}
    if is_not_translatable:
        string_attributes["translatable"] = "false"

    string_element = ET.Element("string", string_attributes)
    string_element.text = string_value
    root.append(string_element)

def write_xml_to_file(root, column, output_dir):
    print(f"Writing strings.xml for language: {column}")
    pretty_xml = pretty_print_xml(root)
    directory = f"{output_dir}/{column}"
    os.makedirs(directory, exist_ok=True)

    with open(f"{directory}/strings.xml", "w", encoding="utf-8") as file:
        file.write(pretty_xml)

def main():
    if len(sys.argv) > 1 and sys.argv[1] in ["-h", "--help"]:
        print_help()
        sys.exit(0)

    default_file = "translations.xlsx"
    input_file = sys.argv[1] if len(sys.argv) > 1 else default_file
    output_dir = sys.argv[2] if len(sys.argv) > 2 else "./app/src/main/res/"

    print("Starting the string conversion process...")
    df = read_excel_file(input_file)
    amt_files = process_strings(df, output_dir)

    print(f"Import completed. Generated {amt_files} strings.xml files. Check '{output_dir}' for the output.")

if __name__ == "__main__":
    main()
