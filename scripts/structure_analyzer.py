import os
import re
import json
import sys
import subprocess
from collections import defaultdict

class StructureAnalyzer:
    def __init__(self, root_path):
        self.root_path = root_path
        self.classes = {}
        self.modules = defaultdict(list)

    def check_ripgrep(self):
        try:
            subprocess.run(["rg", "--version"], capture_output=True, check=True)
            return True
        except (subprocess.CalledProcessError, FileNotFoundError):
            print("ERROR: 'ripgrep (rg)' is not installed. Please install it using: sudo apt install ripgrep")
            sys.exit(1)

    def analyze(self):
        self.check_ripgrep()
        
        # 1. Discover all Java/Kotlin files
        for root, _, files in os.walk(self.root_path):
            if any(p in root for p in ["/target/", "/build/", "/.git/", "/node_modules/"]):
                continue
            for file in files:
                if file.endswith(".java") or file.endswith(".kt"):
                    full_path = os.path.join(root, file)
                    self._parse_file(full_path)
        
        # 2. Build Dependency Graph data
        return {
            "classes": self.classes,
            "modules": self._infer_modules()
        }

    def _parse_file(self, file_path):
        try:
            with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()
        except Exception:
            return

        # Package & Class Name
        package_match = re.search(r'package\s+([\w\.]+);', content)
        package = package_match.group(1) if package_match else "default"

        class_match = re.search(r'(?:public\s+)?(?:class|interface|record|enum)\s+(\w+)', content)
        if not class_match:
            return
        
        class_name = class_match.group(1)
        full_class_name = f"{package}.{class_name}"

        # Imports
        imports = re.findall(r'import\s+(?:static\s+)?([\w\.]+);', content)
        
        # Simple Dependency Injection (Constructor/Field)
        # Regex to find final fields or @Autowired/@Inject (common patterns)
        injected_types = []
        # Pattern for: private final ServiceName service;
        fields = re.findall(r'(?:private\s+)?final\s+([\w<>]+)\s+\w+;', content)
        injected_types.extend(fields)
        # Pattern for: @Autowired private ServiceName service;
        annotated = re.findall(r'@(?:Autowired|Inject|Resource)\s+(?:private\s+)?([\w<>]+)\s+\w+;', content)
        injected_types.extend(annotated)

        # Inheritance / Implementation
        extends = re.search(r'extends\s+([\w\.]+)', content)
        implements = re.search(r'implements\s+([\w\.,\s]+)', content)
        impl_list = [i.strip() for i in implements.group(1).split(',')] if implements else []

        self.classes[full_class_name] = {
            "name": class_name,
            "package": package,
            "file": os.path.relpath(file_path, self.root_path),
            "imports": imports,
            "injected": list(set(injected_types)),
            "extends": extends.group(1) if extends else None,
            "implements": impl_list,
            "is_interface": "interface" in content[:content.find(class_name)]
        }

    def _infer_modules(self):
        # Assume top 4/5 segments of package represent a module in this workspace
        modules = defaultdict(list)
        for full_name, info in self.classes.items():
            parts = info["package"].split('.')
            if len(parts) >= 5:
                module_name = ".".join(parts[:5])
            else:
                module_name = ".".join(parts)
            modules[module_name].append(full_name)
        return dict(modules)

if __name__ == "__main__":
    target_path = sys.argv[1] if len(sys.argv) > 1 else "."
    analyzer = StructureAnalyzer(target_path)
    print(json.dumps(analyzer.analyze(), indent=2))
