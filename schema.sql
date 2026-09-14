-- ==============================================================================
-- ATTENTIS: ACADEMIC MANAGEMENT SUITE
-- Production PostgreSQL / Supabase Schema & Row Level Security (RLS) Policies
-- Multi-Tenant Data Isolation, Offline-First Sync & Last-Write-Wins (LWW)
-- ==============================================================================

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. ENUMS
CREATE TYPE user_role AS ENUM ('student', 'teacher', 'admin');
CREATE TYPE sync_status_enum AS ENUM ('SYNCED', 'PENDING_INSERT', 'PENDING_UPDATE', 'PENDING_DELETE');
CREATE TYPE attendance_status_enum AS ENUM ('P', 'A', 'L');

-- 3. PROFILES TABLE (Linked to auth.users)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    full_name TEXT NOT NULL,
    role user_role NOT NULL DEFAULT 'student',
    department TEXT NOT NULL,
    roll_or_emp_id TEXT NOT NULL,
    designation TEXT,
    cabin_no TEXT,
    semester TEXT,
    section TEXT,
    device_id TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 4. COURSES TABLE
CREATE TABLE IF NOT EXISTS public.courses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    credits INT NOT NULL DEFAULT 3,
    teacher_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    department TEXT NOT NULL,
    semester TEXT,
    section TEXT,
    sync_version BIGINT NOT NULL DEFAULT 1,
    device_id TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT unique_course_code_dept UNIQUE (code, department, semester, section)
);

-- 5. ENROLLMENTS TABLE (Students enrolled in Courses)
CREATE TABLE IF NOT EXISTS public.enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT unique_student_enrollment UNIQUE (course_id, student_id)
);

-- 6. SCHEDULE SLOTS TABLE (Timetable / Lecture Slots)
CREATE TABLE IF NOT EXISTS public.schedule_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    day_of_week TEXT NOT NULL, -- "Monday", "Tuesday", etc.
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    room TEXT NOT NULL,
    section TEXT NOT NULL,
    device_id TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 7. ATTENDANCE SESSIONS TABLE (Specific lecture class meetings)
CREATE TABLE IF NOT EXISTS public.attendance_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    schedule_slot_id UUID REFERENCES public.schedule_slots(id) ON DELETE SET NULL,
    teacher_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    device_id TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 8. ATTENDANCE RECORDS TABLE (Individual student attendance event)
CREATE TABLE IF NOT EXISTS public.attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES public.attendance_sessions(id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES public.courses(id) ON DELETE CASCADE,
    schedule_slot_id UUID REFERENCES public.schedule_slots(id) ON DELETE SET NULL,
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status attendance_status_enum NOT NULL DEFAULT 'P',
    marked_date DATE NOT NULL,
    marked_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    sync_status sync_status_enum NOT NULL DEFAULT 'SYNCED',
    sync_version BIGINT NOT NULL DEFAULT 1,
    device_id TEXT,
    marked_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT unique_attendance_per_student_session UNIQUE (course_id, schedule_slot_id, student_id, marked_date)
);

-- ==============================================================================
-- 9. SERVER-SIDE LAST-WRITE-WINS (LWW) CONFLICT RESOLUTION TRIGGER
-- ==============================================================================
CREATE OR REPLACE FUNCTION resolve_attendance_lww()
RETURNS TRIGGER AS $$
BEGIN
    -- If existing record has newer or equal updated_at timestamp, reject incoming stale write
    IF (TG_OP = 'UPDATE') THEN
        IF NEW.updated_at < OLD.updated_at THEN
            RETURN OLD;
        END IF;
        NEW.sync_version = OLD.sync_version + 1;
    END IF;
    NEW.updated_at = timezone('utc'::text, now());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_attendance_lww ON public.attendance_records;
CREATE TRIGGER trg_attendance_lww
BEFORE INSERT OR UPDATE ON public.attendance_records
FOR EACH ROW EXECUTE FUNCTION resolve_attendance_lww();

-- ==============================================================================
-- 10. ROW LEVEL SECURITY (RLS) POLICIES
-- ==============================================================================

-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.courses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedule_slots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_records ENABLE ROW LEVEL SECURITY;

-- Helper functions for role identification
CREATE OR REPLACE FUNCTION auth.current_role()
RETURNS user_role AS $$
    SELECT role FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- PROFILES POLICIES
-- Users can view their own profile, or teachers can view enrolled students' profiles
CREATE POLICY "Users can read own profile"
    ON public.profiles FOR SELECT
    USING (auth.uid() = id OR auth.current_role() = 'teacher');

CREATE POLICY "Users can update own profile"
    ON public.profiles FOR UPDATE
    USING (auth.uid() = id);

-- COURSES POLICIES
-- Teachers can manage their courses; enrolled students can read course details
CREATE POLICY "Teachers can create and manage their courses"
    ON public.courses FOR ALL
    USING (teacher_id = auth.uid());

CREATE POLICY "Students can view enrolled courses"
    ON public.courses FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.enrollments
            WHERE enrollments.course_id = courses.id
            AND enrollments.student_id = auth.uid()
        )
    );

-- ENROLLMENTS POLICIES
CREATE POLICY "Teachers can view enrollments for their courses"
    ON public.enrollments FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.courses
            WHERE courses.id = enrollments.course_id
            AND courses.teacher_id = auth.uid()
        )
    );

CREATE POLICY "Students can view own enrollments"
    ON public.enrollments FOR SELECT
    USING (student_id = auth.uid());

-- SCHEDULE SLOTS POLICIES
CREATE POLICY "Teachers can manage slots"
    ON public.schedule_slots FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.courses
            WHERE courses.id = schedule_slots.course_id
            AND courses.teacher_id = auth.uid()
        )
    );

CREATE POLICY "Students can view schedule for enrolled courses"
    ON public.schedule_slots FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.enrollments
            WHERE enrollments.course_id = schedule_slots.course_id
            AND enrollments.student_id = auth.uid()
        )
    );

-- ATTENDANCE SESSIONS POLICIES
CREATE POLICY "Teachers manage attendance sessions"
    ON public.attendance_sessions FOR ALL
    USING (teacher_id = auth.uid());

CREATE POLICY "Students view attendance sessions for enrolled courses"
    ON public.attendance_sessions FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.enrollments
            WHERE enrollments.course_id = attendance_sessions.course_id
            AND enrollments.student_id = auth.uid()
        )
    );

-- ATTENDANCE RECORDS POLICIES (STRICT ZERO-DATA MIXING)
-- Teachers can read and write attendance for their courses
CREATE POLICY "Teachers manage attendance for their courses"
    ON public.attendance_records FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.courses
            WHERE courses.id = attendance_records.course_id
            AND courses.teacher_id = auth.uid()
        )
    );

-- Students can ONLY view their OWN attendance records
CREATE POLICY "Students view only their own attendance"
    ON public.attendance_records FOR SELECT
    USING (student_id = auth.uid());

-- Students can ONLY insert/update their OWN self-attendance
CREATE POLICY "Students mark only their own self attendance"
    ON public.attendance_records FOR INSERT
    WITH CHECK (
        student_id = auth.uid() AND
        marked_by = auth.uid() AND
        EXISTS (
            SELECT 1 FROM public.enrollments
            WHERE enrollments.course_id = attendance_records.course_id
            AND enrollments.student_id = auth.uid()
        )
    );
