"""
Importer for Campus++.

Logic:
1. Wait until DB is reachable
2. Wait until Flyway has created schema
3. If DB already has data -> exit 0
4. If DB empty:
   - JSON must exist
   - Import data
"""

import json
import os
import sys
import time
import uuid
import re

import psycopg2
from psycopg2.extras import Json


# =====================================================
# DB utils
# =====================================================

def require_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"{name} is not set")
    return value


def build_db_config():
    return {
        "host": require_env("DB_HOST"),
        "port": int(require_env("DB_PORT")),
        "database": require_env("DB_NAME"),
        "user": require_env("DB_USERNAME"),
        "password": require_env("DB_PASSWORD"),
    }


def connect_db():
    return psycopg2.connect(**build_db_config())


def wait_for_database(timeout=60, interval=2):
    print("⏳ Waiting for database connection...")
    start = time.time()
    last_error = None

    while time.time() - start < timeout:
        try:
            conn = connect_db()
            print("✅ Database connection is ready")
            return conn
        except Exception as e:
            last_error = e
            print(f"   ... DB not ready yet: {e}")
            time.sleep(interval)

    raise RuntimeError(f"Database not reachable after waiting: {last_error}")


def wait_for_schema(cur, timeout=60, interval=2):
    print("⏳ Waiting for DB schema (Flyway)...")
    start = time.time()

    while time.time() - start < timeout:
        cur.execute(
            """
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = 'app'
              AND table_name = 'study_programs'
            """
        )
        if cur.fetchone():
            print("✅ Schema is ready")
            return

        time.sleep(interval)

    raise RuntimeError("DB schema not ready after waiting")


def database_has_data(cur) -> bool:
    cur.execute("SELECT COUNT(*) FROM app.study_programs;")
    return cur.fetchone()[0] > 0


# =====================================================
# Helpers
# =====================================================

def extract_ects(value) -> int:
    if value is None:
        return 0

    m = re.search(r"[\d,\.]+", str(value))
    if not m:
        return 0

    try:
        return int(float(m.group(0).replace(",", ".")))
    except ValueError:
        return 0


def normalize_lang(v):
    if not v:
        return "de"
    s = str(v).lower()
    return "en" if "engl" in s or "english" in s else "de"


def j(v):
    return Json(v) if v is not None else None


def file_exists(path: str) -> bool:
    return os.path.exists(path) and os.path.getsize(path) > 0


# =====================================================
# Import logic
# =====================================================

def import_data(json_path: str):
    print(f"📖 Loading data from {json_path}")

    with open(json_path, encoding="utf-8") as f:
        programs = json.load(f)

    print(f"✅ Programs loaded: {len(programs)}")

    conn = connect_db()
    cur = conn.cursor()

    programs_i = modules_i = courses_i = links_i = 0

    try:
        for i, p in enumerate(programs, 1):
            curriculum = p.get("curriculum", [])
            if not curriculum:
                continue

            title = p.get("program_title", "Unknown")
            mode = p.get("mode")
            url = p.get("program_url", title)

            print(f"\n📚 [{i}] {title}")

            program_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"{url}:{mode}"))

            # ---------- Study program ----------
            cur.execute(
                """
                INSERT INTO app.study_programs
                    (id, name, description, degree, semesters, mode,
                     total_ects, language, application_period,
                     start_dates, source_url, created_at, updated_at)
                VALUES
                    (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,now(),now())
                ON CONFLICT (name, mode)
                DO UPDATE SET updated_at = now()
                RETURNING id
                """,
                (
                    program_id,
                    title,
                    None,
                    p.get("degree"),
                    p.get("semesters"),
                    mode,
                    p.get("total_ects"),
                    p.get("language"),
                    p.get("application_period"),
                    p.get("start_dates"),
                    url,
                ),
            )

            program_id = cur.fetchone()[0]
            programs_i += 1

            # ---------- Modules ----------
            module_map = {}

            for item in curriculum:
                if (item.get("kind") or "").lower() != "module":
                    continue

                m_title = item.get("title", "Unknown Module")
                semester = item.get("semester")

                module_id = str(
                    uuid.uuid5(uuid.NAMESPACE_URL, f"{program_id}:{m_title}:{semester}")
                )

                cur.execute(
                    """
                    INSERT INTO app.modules
                        (id, study_program_id, title, semester,
                         details_html, content, learning_outcomes,
                         teaching_method, exam_method, literature,
                         teaching_language, created_at, updated_at)
                    VALUES
                        (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,now(),now())
                    ON CONFLICT (study_program_id, title)
                    DO UPDATE SET updated_at = now()
                    RETURNING id
                    """,
                    (
                        module_id,
                        program_id,
                        m_title,
                        semester,
                        item.get("details_html"),
                        j(item.get("content")),
                        j(item.get("learning_outcomes")),
                        j(item.get("teaching_method")),
                        j(item.get("exam_method")),
                        j(item.get("literature")),
                        j(item.get("teaching_language")),
                    ),
                )

                module_id = cur.fetchone()[0]
                module_map[(m_title, semester)] = module_id
                modules_i += 1

            # ---------- Courses ----------
            for item in curriculum:
                if (item.get("kind") or "").lower() == "module":
                    continue

                c_title = item.get("title", "Unknown Course")
                semester = item.get("semester")
                ects = extract_ects(item.get("ects"))

                module_id = None
                parent = item.get("parent_module")
                if parent:
                    module_id = module_map.get((parent, semester))
                    if not module_id:
                        for (t, _), mid in module_map.items():
                            if t == parent:
                                module_id = mid
                                break

                if not module_id:
                    continue

                course_id = str(
                    uuid.uuid5(
                        uuid.NAMESPACE_URL,
                        f"{module_id}:{c_title}:{semester}:{ects}"
                    )
                )

                cur.execute(
                    """
                    INSERT INTO app.courses (
                        id, module_id, title, description, ects,
                        language, sws, semester, kind,
                        details_html, content, learning_outcomes,
                        teaching_method, exam_method, literature,
                        teaching_language, source_url, study_program_id,
                        created_at, updated_at
                    )
                    VALUES
                        (%s,%s,%s,%s,%s,%s,%s,%s,%s,
                         %s,%s,%s,%s,%s,%s,%s,%s,%s,
                         now(),now())
                    ON CONFLICT ON CONSTRAINT uq_courses_module_title
                    DO NOTHING
                    RETURNING id;
                    """,
                    (
                        course_id,
                        module_id,
                        c_title,
                        None,
                        ects,
                        normalize_lang(p.get("language")),
                        item.get("sws"),
                        semester,
                        item.get("kind"),
                        item.get("details_html"),
                        j(item.get("content")),
                        j(item.get("learning_outcomes")),
                        j(item.get("teaching_method")),
                        j(item.get("exam_method")),
                        j(item.get("literature")),
                        j(item.get("teaching_language")),
                        url,
                        program_id,
                    ),
                )

                row = cur.fetchone()
                if not row:
                    continue

                course_id = row[0]
                courses_i += 1

                cur.execute(
                    """
                    INSERT INTO app.study_program_courses
                        (study_program_id, course_id, created_at)
                    VALUES (%s,%s,now())
                    ON CONFLICT DO NOTHING
                    """,
                    (program_id, course_id),
                )
                links_i += 1

            print(f"   ✅ modules={modules_i}, courses={courses_i}, links={links_i}")

        conn.commit()

        print("\n================ IMPORT FINISHED ================")
        print(f"Programs: {programs_i}")
        print(f"Modules : {modules_i}")
        print(f"Courses : {courses_i}")
        print(f"Links   : {links_i}")
        print("================================================")

    except Exception as e:
        conn.rollback()
        print("❌ Import failed:", e)
        raise
    finally:
        cur.close()
        conn.close()


# =====================================================
# Entrypoint
# =====================================================

if __name__ == "__main__":
    print("🚀 Importer started")

    DATA_FILE = os.getenv("DATA_FILE", "/data/hcw_courses.json")

    if not file_exists(DATA_FILE):
        print(f"❌ JSON file not found: {DATA_FILE}")
        sys.exit(1)

    conn = None
    cur = None

    try:
        conn = wait_for_database(timeout=60, interval=2)
        cur = conn.cursor()

        wait_for_schema(cur, timeout=60, interval=2)

        if database_has_data(cur):
            print("✅ Database already populated — skipping import")
            sys.exit(0)

        print("🟡 Database is empty — starting import")
        import_data(DATA_FILE)

        print("✅ Import completed successfully")
        sys.exit(0)

    except Exception as e:
        print("💥 Importer crashed:", e)
        sys.exit(1)

    finally:
        if cur is not None:
            cur.close()
        if conn is not None:
            conn.close()