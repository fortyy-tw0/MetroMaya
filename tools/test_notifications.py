"""Notification integration against a local fake email API. Run: python3 tools/test_notifications.py"""

import json
import os
import socket
import subprocess
import tempfile
import threading
import time
from datetime import date, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from test_staff_access import request

ROOT = Path(__file__).resolve().parents[1]


class FakeEmail(BaseHTTPRequestHandler):
    messages = []

    def do_POST(self):
        content = self.rfile.read(int(self.headers["Content-Length"]))
        self.messages.append(json.loads(content))
        body = b'{"id":"local-test-email"}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_args):
        pass


def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def main():
    FakeEmail.messages = []
    mock = ThreadingHTTPServer(("127.0.0.1", port()), FakeEmail)
    threading.Thread(target=mock.serve_forever, daemon=True).start()
    sources = [str(path) for path in (ROOT / "src/main/java").rglob("*.java")]
    with tempfile.TemporaryDirectory() as classes:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", classes, *sources], check=True)
        app_port = port()
        env = os.environ.copy()
        for key in ("SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_SECRET_KEY",
                    "EGOV_BASE_URL", "EGOV_PARTNER_CODE", "EGOV_PARTNER_SECRET", "ASSEMBLYAI_API_KEY"):
            env.pop(key, None)
        env.update(PORT=str(app_port), STAFF_ADMIN_PASSWORD="strong-admin-test-password",
                   STAFF_SESSION_SECRET="staff-test-session-secret-with-more-than-32-characters",
                   MANAGEMENT_LINK_KEY="management-link-encryption-secret-for-test-12345",
                   RESEND_API_KEY="test-resend-key", RESEND_FROM_EMAIL="MetroMaya <test@example.com>",
                   PUBLIC_BASE_URL=f"http://127.0.0.1:{app_port}",
                   RESEND_API_URL=f"http://127.0.0.1:{mock.server_port}/emails")
        server = subprocess.Popen(["java", "-cp", classes, "appointment.Main"], env=env,
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        base = f"http://127.0.0.1:{app_port}"
        try:
            for _ in range(40):
                try:
                    status, config = request(base, "/api/config")
                    if status == 200:
                        break
                except Exception:
                    time.sleep(0.1)
            else:
                raise AssertionError("Test server did not start")
            today = date.fromisoformat(config["today"])
            available = []
            for offset in range(1, 10):
                day = (today + timedelta(days=offset)).isoformat()
                status, slots = request(base, "/api/slots?officeId=caloocan&date=" + day)
                if status == 200 and any(slot["available"] for slot in slots["slots"]):
                    available.append((day, next(slot["time"] for slot in slots["slots"] if slot["available"])))
                if len(available) == 2:
                    break
            assert len(available) == 2
            (first_day, first_time), (next_day, next_time) = available
            status, booked = request(base, "/api/appointments", {
                "serviceId": "new-permit", "officeId": "caloocan", "date": first_day, "time": first_time,
                "industryId": "retail", "employees": "2", "businessName": "Email Test Business",
                "businessAddress": "Caloocan", "ownerName": "Email Applicant",
                "email": "email-test@example.com", "phone": "09123456789",
            })
            assert status == 201, booked
            reference = booked["appointment"]["referenceNo"]
            assert len(FakeEmail.messages) == 1
            assert reference in FakeEmail.messages[0]["text"]
            assert "#access=" + booked["managementToken"] in FakeEmail.messages[0]["text"]
            assert "managementTokenCiphertext" not in booked["appointment"]

            status, admin_login = request(base, "/api/staff/login", {
                "username": "admin", "password": env["STAFF_ADMIN_PASSWORD"]})
            assert status == 200
            admin = admin_login["token"]
            if first_day == (today + timedelta(days=1)).isoformat():
                status, reminded = request(base, "/api/staff/send-reminders", {}, bearer=admin)
                assert status == 200 and reminded["emailsSent"] == 1, reminded
                assert request(base, "/api/staff/send-reminders", {}, bearer=admin)[1]["emailsSent"] == 0
            status, seeded = request(base, "/api/staff/seed-heads", {}, bearer=admin)
            assert status == 200
            head = next(item for item in seeded["issued"] if item["officeId"] == "caloocan")
            status, head_login = request(base, "/api/staff/login", {
                "username": head["username"], "password": head["temporaryPassword"]})
            assert status == 200
            status, moved = request(base, "/api/staff/bookings/" + reference + "/move", {
                "date": next_day, "time": next_time}, bearer=head_login["token"])
            assert status == 200 and moved["emailSent"] is True, moved
            status, closed = request(base, "/api/staff/policy/closure", {
                "officeId": "caloocan", "date": next_day, "kind": "EMERGENCY", "reason": "Flooding",
            }, bearer=head_login["token"])
            assert status == 200 and closed["emailsSent"] == 1, closed
            subjects = [message["subject"] for message in FakeEmail.messages]
            assert any(s.startswith("Your MetroMaya booking BPL-") for s in subjects)
            assert "MetroMaya appointment time changed" in subjects
            assert "MetroMaya office closure affects your booking" in subjects
            print("Notification checks passed")
        finally:
            server.terminate()
            try:
                server.wait(timeout=3)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()
            mock.shutdown()


if __name__ == "__main__":
    main()
