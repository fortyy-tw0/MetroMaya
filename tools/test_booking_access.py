"""End-to-end checks for the booking access boundary. Run: python3 tools/test_booking_access.py"""

import json
import os
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def request(base, path, form=None, management=None, bearer=None):
    headers = {}
    if management is not None:
        headers["X-Management-Token"] = management
    if bearer is not None:
        headers["Authorization"] = "Bearer " + bearer
    if form is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    body = urllib.parse.urlencode(form).encode() if form is not None else None
    req = urllib.request.Request(base + path, data=body, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=3) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def main():
    sources = [str(path) for path in (ROOT / "src/main/java").rglob("*.java")]
    with tempfile.TemporaryDirectory() as classes:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", classes, *sources], check=True)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        env = os.environ.copy()
        for key in ("SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_SECRET_KEY",
                    "EGOV_BASE_URL", "EGOV_PARTNER_CODE", "EGOV_PARTNER_SECRET", "ASSEMBLYAI_API_KEY"):
            env.pop(key, None)
        env.update(PORT=str(port), STAFF_ADMIN_PASSWORD="strong-admin-test-password",
                   STAFF_SESSION_SECRET="staff-test-session-secret-with-more-than-32-characters")
        server = subprocess.Popen(["java", "-cp", classes, "appointment.Main"], env=env,
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        base = f"http://127.0.0.1:{port}"
        try:
            for _ in range(40):
                try:
                    status, config = request(base, "/api/config")
                    if status == 200:
                        break
                except urllib.error.URLError:
                    time.sleep(0.1)
            else:
                raise AssertionError("Test server did not start")

            today = date.fromisoformat(config["today"])
            for offset in range(1, 8):
                day = (today + timedelta(days=offset)).isoformat()
                status, slots = request(base, "/api/slots?officeId=caloocan&date=" + day)
                if status == 200 and any(slot["available"] for slot in slots["slots"]):
                    time_slot = next(slot["time"] for slot in slots["slots"] if slot["available"])
                    break
            else:
                raise AssertionError("No bookable test day")

            status, booked = request(base, "/api/appointments", {
                "serviceId": "new-permit", "officeId": "caloocan", "date": day, "time": time_slot,
                "industryId": "retail", "employees": "2", "businessName": "Access Test Business",
                "businessAddress": "Caloocan, Metro Manila", "ownerName": "Test Applicant",
                "email": "access-test@example.com", "phone": "09123456789",
            })
            assert status == 201, booked
            ref = booked["appointment"]["referenceNo"]
            token = booked["managementToken"]
            code = booked["appointment"]["checkInCode"]
            assert len(token) == 43 and len(code) == 32 and ref not in code
            assert "managementTokenHash" not in booked["appointment"]

            path = "/api/appointments/" + ref
            status, denied = request(base, path)
            assert status == 404 and "Test Applicant" not in json.dumps(denied)
            assert request(base, path, management="A" * 43)[0] == 404
            assert request(base, path, management=code)[0] == 404
            assert request(base, path, management=token)[0] == 200
            assert request(base, path + "/cancel", {})[0] == 404
            assert request(base, path + "/reschedule", {"date": day, "time": time_slot})[0] == 404
            assert request(base, path + "/pay", {"channel": "qrph"})[0] == 404

            status, admin_login = request(base, "/api/staff/login",
                                          {"username": "admin", "password": env["STAFF_ADMIN_PASSWORD"]})
            assert status == 200
            status, created = request(base, "/api/staff/users", {
                "username": "clerk.caloocan", "password": "strong-clerk-test-password",
                "role": "STAFF", "officeId": "caloocan"}, bearer=admin_login["token"])
            assert status == 201, created
            status, clerk_login = request(base, "/api/staff/login",
                                          {"username": "clerk.caloocan", "password": "strong-clerk-test-password"})
            assert status == 200
            clerk = clerk_login["token"]
            checkin = "/api/staff/checkin/" + code
            assert request(base, checkin)[0] == 401
            assert request(base, checkin, bearer=clerk)[0] == 200
            assert request(base, "/api/office/checkin/" + code)[0] == 404
            status, reset = request(base, "/api/staff/bookings/" + ref + "/reset-management", {}, bearer=clerk)
            assert status == 200, reset
            new_token = reset["managementToken"]
            assert new_token != token
            assert request(base, path, management=token)[0] == 404
            assert request(base, path, management=new_token)[0] == 200
            assert request(base, path + "/pay", {"channel": "qrph"}, management=new_token)[0] == 200
            other_time = next(slot["time"] for slot in slots["slots"]
                              if slot["available"] and slot["time"] != time_slot)
            status, moved = request(base, path + "/reschedule",
                                    {"date": day, "time": other_time}, management=new_token)
            assert status == 200 and moved["appointment"]["time"] == other_time
            status, cancelled = request(base, path + "/cancel", {}, management=new_token)
            assert status == 200 and cancelled["appointment"]["status"] == "CANCELLED"
            print("Booking access checks passed")
        finally:
            server.terminate()
            try:
                server.wait(timeout=3)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()


if __name__ == "__main__":
    main()
