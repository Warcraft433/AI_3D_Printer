import py_compile
import os
import sys

files_to_check = [
    'backend/core/settings.py',
    'backend/core/urls.py',
    'backend/core/asgi.py',
    'backend/dashboard/views.py',
    'backend/dashboard/urls.py',
    'backend/error_logs/views.py',
    'backend/error_logs/urls.py',
]

print("🔍 Checking syntax for project files...")
error_found = False
for f in files_to_check:
    try:
        if os.path.exists(f):
            py_compile.compile(f, doraise=True)
            print(f"✅ {f}: Syntax OK")
        else:
            print(f"⚠️ {f}: File not found")
    except py_compile.PyCompileError as e:
        print(f"❌ {f}: SYNTAX ERROR!")
        print(e)
        error_found = True
    except Exception as e:
        print(f"❌ {f}: ERROR - {e}")
        error_found = True

if error_found:
    print("\n🚨 SYNTAX ERRORS DETECTED. Stopping.")
    sys.exit(1)
else:
    print("\n✨ All checked files have valid syntax.")
