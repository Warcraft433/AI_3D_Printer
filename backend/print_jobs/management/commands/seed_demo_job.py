"""
Management command: seed_demo_job

Creates a dummy GeneratedModel and PrintJob in 'ready_to_print' status
linked to the first superuser (or the first user in the DB), so the Print
Queue always has a job ready to demonstrate the Start Print → inference flow.

Usage:
    python manage.py seed_demo_job
"""
import os
from django.core.management.base import BaseCommand
from django.contrib.auth import get_user_model
from django.conf import settings

from generated_models.models import GeneratedModel
from print_jobs.models import PrintJob

User = get_user_model()


class Command(BaseCommand):
    help = 'Seeds a demo print job (ready_to_print) for the presentation.'

    def handle(self, *args, **options):
        # ------------------------------------------------------------------ #
        # 1.  Resolve a user to own the demo job (prefer superuser)
        # ------------------------------------------------------------------ #
        user = (
            User.objects.filter(is_superuser=True).first()
            or User.objects.first()
        )
        if not user:
            self.stderr.write(
                '❌ No users in the database. Register an account on the '
                'dashboard first, then re-run this command.'
            )
            return

        self.stdout.write(f'👤 Using user: {user.username}')

        # ------------------------------------------------------------------ #
        # 2.  Create (or reuse) the demo GeneratedModel
        # ------------------------------------------------------------------ #
        model_prompt = '3D Printer Simulation — Spaghetti Error Demo'

        existing_model = GeneratedModel.objects.filter(
            user=user, prompt=model_prompt
        ).first()

        if existing_model:
            gen_model = existing_model
            self.stdout.write(f'♻️  Reusing existing demo model (id={gen_model.pk})')
        else:
            gen_model = GeneratedModel.objects.create(
                user=user,
                prompt=model_prompt,
                status='ready',
                file_path='',          # No actual .obj needed for demo
                generation_time=0,
            )
            self.stdout.write(f'✅ Created demo model (id={gen_model.pk})')

        # ------------------------------------------------------------------ #
        # 3.  Cancel any previous incomplete demo jobs to keep the queue clean
        # ------------------------------------------------------------------ #
        stale = PrintJob.objects.filter(
            model=gen_model,
            status__in=['queued', 'slicing', 'ready_to_print']
        )
        stale_count = stale.count()
        stale.update(status='cancelled')
        if stale_count:
            self.stdout.write(f'🗑️  Cancelled {stale_count} stale demo job(s)')

        # ------------------------------------------------------------------ #
        # 4.  Create the fresh demo print job
        # ------------------------------------------------------------------ #
        # Plant a dummy gcode file so the start-print button is enabled.
        # We create a minimal valid-looking gcode placeholder.
        gcode_rel = 'demo/demo_print.gcode'
        gcode_abs = os.path.join(settings.MEDIA_ROOT, gcode_rel)
        os.makedirs(os.path.dirname(gcode_abs), exist_ok=True)
        if not os.path.exists(gcode_abs):
            with open(gcode_abs, 'w') as f:
                f.write('; PrintGuard Demo GCode\n')
                f.write('G28 ; Home all axes\n')
                f.write('G1 Z10 F3000 ; Raise nozzle\n')
                f.write('M109 S220 ; Wait for nozzle\n')
                f.write('; === SIMULATION ONLY — no real hardware ===\n')
            self.stdout.write(f'📄 Created placeholder gcode: {gcode_rel}')

        job = PrintJob.objects.create(
            model=gen_model,
            status='ready_to_print',
            gcode_path=gcode_rel,
            estimated_time=600,     # 10 minute demo
            progress=0,
            auto_pause_enabled=True,
        )
        self.stdout.write(
            f'🖨️  Demo PrintJob created (id={job.pk}, status=ready_to_print)'
        )

        self.stdout.write('')
        self.stdout.write('====================================================')
        self.stdout.write('  ✅ Demo job is ready! Open the Print Queue on your')
        self.stdout.write('     dashboard and click "Start Print" to begin the')
        self.stdout.write('     YOLOv8 error detection simulation.')
        self.stdout.write('====================================================')
