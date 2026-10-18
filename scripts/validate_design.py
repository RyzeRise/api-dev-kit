#!/usr/bin/env python3
"""Validate design artifacts only; this is not the Java AST checker."""

import json
import re
import sys
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator
from jsonschema.exceptions import SchemaError, ValidationError


ROOT = Path(__file__).resolve().parents[1]
PRIMITIVES = set("boolean byte short int long char float double".split())
KEYWORDS = set((
    "abstract assert boolean break byte case catch char class const continue "
    "default do double else enum extends final finally float for goto if "
    "implements import instanceof int interface long native new package "
    "private protected public return short static strictfp super switch "
    "synchronized this throw throws transient try void volatile while "
    "true false null _"
).split())


class UniqueLoader(yaml.SafeLoader):
    pass


def unique_mapping(loader, node):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=True)
        if not isinstance(key, (str, int, float, bool, type(None))):
            raise ValueError("YAML mapping key must be scalar")
        if key in result:
            raise ValueError("Duplicate YAML key: {}".format(key))
        result[key] = loader.construct_object(value_node, deep=True)
    return result


UniqueLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping
)


def load_yaml(path):
    data = Path(path).read_bytes()
    if len(data) > 100_000:
        raise ValueError("YAML exceeds 100 KB")
    text = data.decode("utf-8-sig")
    if any(isinstance(event, yaml.events.AliasEvent)
           for event in yaml.parse(text)):
        raise ValueError("YAML aliases are not allowed")
    return yaml.load(text, Loader=UniqueLoader)


def valid_name(name):
    return re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name) and name not in KEYWORDS


def check_type_name(name):
    scalar = name
    while scalar.endswith("[]"):
        scalar = scalar[:-2]
    if scalar in PRIMITIVES:
        return
    if not all(valid_name(part) for part in scalar.split(".")):
        raise ValueError("Reserved/invalid Java type: " + name)


def validate_contract(contract, schema):
    Draft202012Validator(schema).validate(contract)
    if not valid_name(contract["operation"]):
        raise ValueError("Invalid Java operation")
    targets = list(contract["target"].values()) + [contract["externalResponse"]]
    if len(set(targets)) != len(targets):
        raise ValueError("Target types must differ")
    for value in targets:
        check_type_name(value)

    models = contract["models"]
    if contract["version"] == 2:
        if not valid_name(contract["business"]):
            raise ValueError("Invalid business identifier")
        if not models["request"]["class"].endswith("ReqDto") or not models["response"]["class"].endswith("RspDto"):
            raise ValueError("Version 2 models require ReqDto/RspDto")
        if contract["target"]["converter"].rsplit(".", 1)[0] != contract["target"]["responseConverter"].rsplit(".", 1)[0]:
            raise ValueError("Request/response converters must share a package")
    if models["request"]["class"] == models["response"]["class"]:
        raise ValueError("Request and response types must differ")
    if contract["externalResponse"] == "java.lang.String":
        if contract["version"] != 2 or contract.get("errorMode") not in ("exception", "message"):
            raise ValueError("String response requires v2 and explicit errorMode")
        if set(contract["responseBindings"]) != {"message"}:
            raise ValueError("String responseBindings requires only message")
        source = contract["responseBindings"]["message"]
        if not any(f["name"] == source and f["type"] == "java.lang.String" for f in models["response"]["fields"]):
            raise ValueError("String message binding must refer to String field")
    elif contract.get("errorMode") == "message":
        raise ValueError("message errorMode requires String response")
    request_fields = {}
    for role, model in models.items():
        check_type_name(model["class"])
        names = set()
        for field in model["fields"]:
            name = field["name"]
            if not valid_name(name) or name in names:
                raise ValueError("Duplicate/invalid field: " + name)
            names.add(name)
            check_type_name(field["type"])
            if field.get("required") and field["type"] in PRIMITIVES:
                raise ValueError("required applies to reference fields only")
        if role == "request":
            request_fields = {f["name"]: f["type"] for f in model["fields"]}

    roots = [model["sourceRoot"] for model in models.values()]
    prior = {}
    handlers = set()
    for step in contract["steps"]:
        step_id = step["id"]
        if not valid_name(step_id) or step_id in prior:
            raise ValueError("Duplicate/invalid step: " + step_id)
        if step["handler"] in handlers:
            raise ValueError("Duplicate generated Handler type")
        handlers.add(step["handler"])
        check_type_name(step["handler"])
        check_type_name(step["inputType"])
        check_type_name(step["outputType"])
        if step["inputType"] in PRIMITIVES or step["outputType"] in PRIMITIVES:
            raise ValueError("Handler generic types must be reference types")
        roots.append(step["sourceRoot"])
        parts = step["input"].split(".")
        expected = None
        if parts[0] == "request":
            if len(parts) == 1:
                expected = models["request"]["class"]
            elif parts[1] not in request_fields:
                raise ValueError("Unknown request field: " + parts[1])
            else:
                expected = request_fields[parts[1]]
        else:
            if parts[1] not in prior:
                raise ValueError("Unknown/forward step reference: " + parts[1])
            if len(parts) == 2:
                expected = prior[parts[1]]
            # External output fields require Java AST resolution later.
        if expected and expected != step["inputType"]:
            raise ValueError("Input type mismatch for step: " + step_id)
        prior[step_id] = step["outputType"]

    assembly = contract["assembly"]
    if assembly["mode"] == "direct":
        if len(contract["steps"]) != 1:
            raise ValueError("direct assembly requires exactly one Handler")
        if contract["steps"][0]["outputType"] != models["response"]["class"]:
            raise ValueError("direct output must equal the business response type")
    else:
        roots.append(assembly["sourceRoot"])
        check_type_name(assembly["builder"])

    generated = [model["class"] for model in models.values()] + list(handlers)
    if assembly["mode"] == "builder":
        generated.append(assembly["builder"])
    if len(set(generated)) != len(generated):
        raise ValueError("Generated type names collide")
    if set(generated) & (set(contract["target"].values())
                         | {contract["externalResponse"]}):
        raise ValueError("Generated type collides with an existing target")

    for root in roots:
        if any(part in (".", "..") for part in root.split("/")):
            raise ValueError("Unsafe sourceRoot: " + root)

    response_fields = {field["name"] for field in models["response"]["fields"]}
    for destination, source in contract["responseBindings"].items():
        if not valid_name(destination) or source not in response_fields:
            raise ValueError("Invalid response binding: " + destination)
    return contract


def validate_repository(root=ROOT):
    root = Path(root)
    schema = json.loads((root / "schemas/api-contract.schema.json").read_text())
    Draft202012Validator.check_schema(schema)
    examples = sorted((root / "examples").glob("*.yaml"))
    if not examples:
        raise ValueError("No example contracts")
    for example in examples:
        validate_contract(load_yaml(example), schema)
        print("PASS contract: " + example.name)

    skills = sorted((root / "skills").glob("*/SKILL.md"))
    if not skills:
        raise ValueError("No skills")
    for skill in skills:
        text = skill.read_text(encoding="utf-8")
        frontmatter = text.split("---", 2)
        if len(frontmatter) != 3 or frontmatter[0]:
            raise ValueError("Invalid skill frontmatter: " + str(skill))
        meta = yaml.safe_load(frontmatter[1])
        if set(meta) != {"name", "description"} or meta["name"] != skill.parent.name:
            raise ValueError("Invalid skill metadata: " + str(skill))
        if not isinstance(meta["description"], str) or not meta["description"].strip():
            raise ValueError("Missing skill description")
        print("PASS skill: " + meta["name"])
    actual = {p.relative_to(root / "skills").as_posix()
              for p in (root / "skills").rglob("*") if p.is_file() and p.name != "manifest.txt"}
    manifest = (root / "skills/manifest.txt").read_text().splitlines()
    if len(manifest) != len(set(manifest)) or set(manifest) != actual:
        raise ValueError("Skill package manifest does not match skill assets")

    for document in root.rglob("*.md"):
        if ".git" in document.parts:
            continue
        for target in re.findall(r"\]\(([^)]+)\)", document.read_text(encoding="utf-8")):
            if target.startswith(("https:", "http:", "#", "mailto:")):
                continue
            destination = target.split("#", 1)[0]
            if not (document.parent / destination).is_file():
                raise ValueError("Broken link in {}: {}".format(document, target))
    print("PASS Markdown local links")


if __name__ == "__main__":
    try:
        validate_repository()
    except (ValueError, OSError, yaml.YAMLError, SchemaError, ValidationError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
