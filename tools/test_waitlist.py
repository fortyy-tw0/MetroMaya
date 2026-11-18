"""Waitlist integration against a local fake email API. Run: python3 tools/test_waitlist.py"""

import json
import os
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta
from http.server import ThreadingHTTPServer
from pathlib import Path

from test_notifications import FakeEmail, port

ROOT = Path(__file__).resolve().parents[1]


def request(base, path, form=None, management=None):
    headers = {}
    if management:
        headers["X-Management-Token"] = management
    if form is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    body = urllib.parse.urlencode(form).encode() if form is not None else None
    req = urllib.request.Request(base + path, data=body, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=8) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def main():
    FakeEmail.messages = []
    mock = ThreadingHTTPServer(("127.0.0.1", port()), FakeEmail)
    threading.Thread(target=mock.serve_forever, daemon=True).start()
    sources = [str(path) for path in (ROOT / "src/main/java").rglob("*.java")]
    with tempfile.TemporaryDirectory() as classes:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", classes, *sources], check=True)
        app_port = port()
        env = os.environ.copy()
        for key in ("SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_SECRET_KEY", "MANAGEMENT_LINK_KEY",
                    "EGOV_BASE_URL", "EGOV_PARTNER_CODE", "EGOV_PARTNER_SECRET", "ASSEMBLYAI_API_KEY"):
            env.pop(key, None)
        env.update(PORT=str(app_port), RESEND_API_KEY="test-resend-key",
                   RESEND_FROM_EMAIL="MetroMaya <test@example.com>", PUBLIC_BASE_URL=f"http://127.0.0.1:{app_port}",
                   RESEND_API_URL=f"http://127.0.0.1:{mock.server_port}/emails")
        server = subprocess.Popen(["java", "-cp", classes, "appointment.Main"], env=env,
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        base = f"http://127.0.0.1:{app_port}"
        try:
            for _ in range(60):
                try:
                    status, config = request(base, "/api/config")
                    if status == 200:
                        break
                except Exception:
                    time.sleep(0.1)
            else:
                raise AssertionError("Test server did not start")
            assert config["waitlist"] is True

            today = date.fromisoformat(config["today"])
            for offset in range(2, 12):
                day = (today + timedelta(days=offset)).isoformat()
                status, slots = request(base, "/api/slots?officeId=pateros&date=" + day)
                if status == 200:
                    break
            else:
                raise AssertionError("No open test day")

            # an open day cannot be joined
            assert request(base, "/api/waitlist", {"officeId": "pateros", "date": day, "email": "wait@example.com"})[0] == 400

            bookings = []
            n = 0
            for slot in slots["slots"]:
                for _ in range(slot["remaining"]):
                    n += 1
                    status, booked = request(base, "/api/appointments", {
                        "serviceId": "renewal", "officeId": "pateros", "date": day, "time": slot["time"],
                        "industryId": "retail", "employees": "2", "businessName": f"Full Day {n}",
                        "businessAddress": "Pateros", "ownerName": "Full Day Applicant",
                        "email": f"full-{n}@example.com", "phone": "09123456789"})
                    assert status == 201, booked
                    bookings.append(booked)
            assert len(FakeEmail.messages) == n   # one confirmation each

            month = day[:7]
            status, avail = request(base, f"/api/availability?officeId=pateros&month={month}")
            assert next(d for d in avail["days"] if d["date"] == day)["remaining"] == 0

            assert request(base, "/api/waitlist", {"officeId": "pateros", "date": day, "email": "nope"})[0] == 400
            status, joined = request(base, "/api/waitlist", {"officeId": "pateros", "date": day, "email": "Wait@Example.com"})
            assert status == 201, joined
            assert request(base, "/api/waitlist", {"officeId": "pateros", "date": day, "email": "wait@example.com"})[0] == 201
            assert request(base, "/api/waitlist", {"officeId": "pateros", "date": day, "email": "second@example.com"})[0] == 201

            first = bookings[0]
            ref = first["appointment"]["referenceNo"]
            status, cancelled = request(base, f"/api/appointments/{ref}/cancel", {}, management=first["managementToken"])
            assert status == 200 and cancelled["appointment"]["status"] == "CANCELLED"
            time.sleep(0.5)
            opened = [m for m in FakeEmail.messages if m["subject"].startswith("A place opened")]
            assert sorted(m["to"][0] for m in opened) == ["second@example.com", "wait@example.com"], opened
            assert "/book.html" in opened[0]["text"]

            # a second opening on the same day does not email the same people again
            second = bookings[1]
            request(base, f"/api/appointments/{second['appointment']['referenceNo']}/cancel", {},
                    management=second["managementToken"])
            time.sleep(0.5)
            assert len([m for m in FakeEmail.messages if m["subject"].startswith("A place opened")]) == 2
            print("Waitlist checks passed")
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
