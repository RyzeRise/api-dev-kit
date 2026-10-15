import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

from jsonschema.exceptions import ValidationError

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "validate_design", ROOT / "scripts/validate_design.py")
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


class DesignTests(unittest.TestCase):
    def setUp(self):
        self.schema = json.loads(
            (ROOT / "schemas/api-contract.schema.json").read_text())
        self.single = validator.load_yaml(ROOT / "examples/single-handler.yaml")
        self.multi = validator.load_yaml(ROOT / "examples/multi-handler.yaml")

    def check(self, data):
        return validator.validate_contract(data, self.schema)

    def test_examples(self):
        self.check(self.single)
        self.check(self.multi)

    def test_rejects_unknown_properties(self):
        self.single["contractRegistry"] = []
        with self.assertRaises(ValidationError):
            self.check(self.single)

    def test_rejects_duplicate_step(self):
        self.multi["steps"][1]["id"] = self.multi["steps"][0]["id"]
        with self.assertRaisesRegex(ValueError, "Duplicate/invalid step"):
            self.check(self.multi)

    def test_rejects_forward_reference(self):
        self.multi["steps"][0]["input"] = "steps.resource"
        with self.assertRaisesRegex(ValueError, "forward"):
            self.check(self.multi)

    def test_rejects_unknown_request_field(self):
        self.single["steps"][0]["input"] = "request.unknown"
        with self.assertRaisesRegex(ValueError, "Unknown request field"):
            self.check(self.single)

    def test_rejects_input_type_mismatch(self):
        self.single["steps"][0]["inputType"] = "java.lang.String"
        with self.assertRaisesRegex(ValueError, "Input type mismatch"):
            self.check(self.single)

    def test_rejects_direct_multi_handler(self):
        self.multi["assembly"] = {"mode": "direct"}
        with self.assertRaisesRegex(ValueError, "exactly one"):
            self.check(self.multi)

    def test_rejects_direct_output_mismatch(self):
        self.single["steps"][0]["outputType"] = "java.lang.String"
        with self.assertRaisesRegex(ValueError, "direct output"):
            self.check(self.single)

    def test_rejects_expression_injection(self):
        for binding in ["request.getAddress()", "request.address;exit()", "steps.port[0]"]:
            with self.subTest(binding=binding):
                data = copy.deepcopy(self.single)
                data["steps"][0]["input"] = binding
                with self.assertRaises(ValidationError):
                    self.check(data)

    def test_rejects_unsafe_roots(self):
        for root in ["../outside", "a/../outside", ".", "/tmp/out", "C:/out"]:
            with self.subTest(root=root):
                data = copy.deepcopy(self.single)
                data["models"]["request"]["sourceRoot"] = root
                with self.assertRaises((ValueError, ValidationError)):
                    self.check(data)

    def test_rejects_duplicate_field(self):
        fields = self.single["models"]["request"]["fields"]
        fields.append(copy.deepcopy(fields[0]))
        with self.assertRaisesRegex(ValueError, "Duplicate/invalid field"):
            self.check(self.single)

    def test_rejects_response_binding(self):
        self.single["responseBindings"]["resourceName"] = "nonexistent"
        with self.assertRaisesRegex(ValueError, "response binding"):
            self.check(self.single)

    def test_rejects_java_keyword(self):
        self.single["operation"] = "class"
        with self.assertRaisesRegex(ValueError, "operation"):
            self.check(self.single)

    def test_rejects_generated_type_collision(self):
        self.single["steps"][0]["handler"] = self.single["models"]["request"]["class"]
        with self.assertRaisesRegex(ValueError, "collide"):
            self.check(self.single)

    def test_rejects_primitive_handler_generic(self):
        self.single["steps"][0]["inputType"] = "int"
        with self.assertRaisesRegex(ValueError, "reference types"):
            self.check(self.single)

    def test_external_step_field_is_deferred_to_ast(self):
        self.multi["steps"][1]["input"] = "steps.port.neResId"
        self.multi["steps"][1]["inputType"] = "java.lang.String"
        self.check(self.multi)

    def test_yaml_duplicate_keys_and_aliases(self):
        for content in ["version: 1\nversion: 2\n", "a: &a 1\nb: *a\n"]:
            with tempfile.TemporaryDirectory() as folder:
                path = Path(folder) / "example.yaml"
                path.write_text(content)
                with self.assertRaises(ValueError):
                    validator.load_yaml(path)

    def test_repository_validation(self):
        validator.validate_repository(ROOT)


if __name__ == "__main__":
    unittest.main()
