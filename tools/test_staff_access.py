"""Role and office boundary checks. Run: python3 tools/test_staff_access.py"""

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


def request(base, path, form=None, bearer=None):
    headers = {}
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    if form is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    body = urllib.parse.urlencode(form).encode() if form is not None else None
    req = urllib.request.Request(base + path, data=body, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=4) as response:
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
                   STAFF_SESSION_SECRET="staff-test-session-secret-with-more-than-32-characters",
                   ALLOW_DEMO_SEED="true")
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
            day = next((date.fromisoformat(config["today"]) + timedelta(days=i)).isoformat()
                       for i in range(1, 10)
                       if (date.fromisoformat(config["today"]) + timedelta(days=i)).weekday() < 5)
            status, admin_login = request(base, "/api/staff/login",
                                          {"username": "admin", "password": env["STAFF_ADMIN_PASSWORD"]})
            assert status == 200, admin_login
            admin = admin_login["token"]
            assert request(base, "/api/staff/users")[0] == 401
            assert request(base, "/api/staff/users", bearer="bad")[0] == 401
            assert request(base, "/api/staff/bookings?officeId=manila&from=" + day + "&to=" + day,
                           bearer=admin)[0] == 403
            assert request(base, "/api/staff/analytics?officeId=manila&month=" + day[:7],
                           bearer=admin)[0] == 403
            status, seeded = request(base, "/api/staff/seed-heads", {}, bearer=admin)
            assert status == 200 and len(seeded["issued"]) == 17, seeded
            assert request(base, "/api/staff/seed-heads", {}, bearer=admin)[1]["issued"] == []
            heads = {row["officeId"]: row for row in seeded["issued"]}
            status, head_login = request(base, "/api/staff/login", {
                "username": heads["caloocan"]["username"],
                "password": heads["caloocan"]["temporaryPassword"],
            })
            assert status == 200, head_login
            head = head_login["token"]
            assert request(base, "/api/staff/users", bearer=head)[0] == 403
            assert request(base, "/api/staff/bookings?officeId=manila&from=" + day + "&to=" + day,
                           bearer=head)[0] == 403
            assert request(base, "/api/staff/analytics?officeId=caloocan&month=" + day[:7],
                           bearer=head)[0] == 200

            booked = {}
            for office in ("caloocan", "manila"):
                status, slots = request(base, "/api/slots?officeId=" + office + "&date=" + day)
                assert status == 200, slots
                slot = next(row["time"] for row in slots["slots"] if row["available"])
                status, result = request(base, "/api/appointments", {
                    "serviceId": "new-permit", "officeId": office, "date": day, "time": slot,
                    "industryId": "retail", "employees": "2", "businessName": "Staff Test " + office,
                    "businessAddress": office, "ownerName": "Test Applicant",
                    "email": office + "@example.com", "phone": "09123456789",
                })
                assert status == 201, result
                booked[office] = result["appointment"]
            own = booked["caloocan"]
            other = booked["manila"]
            assert request(base, "/api/staff/checkin/" + own["checkInCode"], bearer=head)[0] == 200
            assert request(base, "/api/staff/checkin/" + other["checkInCode"], bearer=head)[0] == 404
            assert request(base, "/api/staff/bookings/" + other["referenceNo"] + "/accept", {}, bearer=head)[0] == 404
            status, accepted = request(base, "/api/staff/bookings/" + own["referenceNo"] + "/accept", {}, bearer=head)
            assert status == 200 and accepted["appointment"]["queueNumber"] == 1
            assert request(base, "/api/staff/bookings/" + own["referenceNo"] + "/no-show", {}, bearer=head)[0] == 400
            status, walkin = request(base, "/api/staff/walkins", {
                "officeId": "caloocan", "serviceId": "renewal", "industryId": "retail",
                "employees": "2", "businessName": "Walk-in Business", "businessAddress": "Caloocan",
                "ownerName": "Walk-in Applicant", "email": "walkin@example.com", "phone": "09123456789",
            }, bearer=head)
            assert status == 201 and walkin["appointment"]["walkIn"]
            assert walkin["appointment"]["queueNumber"] == 1, walkin
            status, next_walkin = request(base, "/api/staff/walkins", {
                "officeId": "caloocan", "serviceId": "renewal", "industryId": "retail",
                "employees": "2", "businessName": "Second Walk-in", "businessAddress": "Caloocan",
                "ownerName": "Second Applicant", "email": "second@example.com", "phone": "09123456789",
            }, bearer=head)
            assert status == 201 and next_walkin["appointment"]["queueNumber"] == 2
            assert request(base, "/api/staff/walkins", {
                "officeId": "manila", "serviceId": "renewal", "industryId": "retail",
                "employees": "2", "businessName": "Blocked Walk-in", "businessAddress": "Manila",
                "ownerName": "Blocked Applicant", "email": "blocked@example.com", "phone": "09123456789",
            }, bearer=head)[0] == 403

            status, created = request(base, "/api/staff/users", {"username": "clerk.caloocan",
                "password": "strong-clerk-test-password", "role": "STAFF", "officeId": "caloocan"}, bearer=admin)
            assert status == 201, created
            status, clerk_login = request(base, "/api/staff/login", {
                "username": "clerk.caloocan", "password": "strong-clerk-test-password"})
            assert status == 200, clerk_login
            clerk = clerk_login["token"]
            assert request(base, "/api/staff/analytics?month=" + day[:7], bearer=clerk)[0] == 403
            assert request(base, "/api/staff/bookings/" + other["referenceNo"], bearer=clerk)[0] == 404
            status, created = request(base, "/api/staff/users", {"username": "treasurer.caloocan",
                "password": "strong-treasurer-test-password", "role": "TREASURER", "officeId": "caloocan"}, bearer=admin)
            assert status == 201, created
            status, treasury_login = request(base, "/api/staff/login", {
                "username": "treasurer.caloocan", "password": "strong-treasurer-test-password"})
            assert status == 200, treasury_login
            treasurer = treasury_login["token"]
            assert request(base, "/api/staff/reconciliation?from=" + day + "&to=" + day,
                           bearer=treasurer)[0] == 200
            assert request(base, "/api/staff/reconciliation?from=" + day + "&to=" + day,
                           bearer=clerk)[0] == 403
            assert request(base, "/api/staff/bookings/" + other["referenceNo"] + "/counter-payment", {
                "amount": "1000", "officialReceiptNo": "OR-1001"}, bearer=treasurer)[0] == 404
            status, paid_at_hall = request(base, "/api/staff/bookings/" + own["referenceNo"] + "/counter-payment", {
                "amount": "1000", "officialReceiptNo": "OR-1001"}, bearer=treasurer)
            assert status == 200 and paid_at_hall["appointment"]["counterPayment"]["verified"]
            assert paid_at_hall["appointment"]["counterPayment"]["source"] == "CITY_HALL"
            assert request(base, "/api/staff/bookings/" + own["referenceNo"] + "/counter-payment", {
                "amount": "1000", "officialReceiptNo": "OR-1001"}, bearer=treasurer)[0] == 400
            assert request(base, "/api/staff/bookings/" + own["referenceNo"] + "/counter-payment", {
                "amount": "1000", "officialReceiptNo": "OR-1002"}, bearer=clerk)[0] == 403
            assert request(base, "/api/staff/policy/schedule", {
                "officeId": "caloocan", "times": "09:00,10:00", "capacity": "1"}, bearer=clerk)[0] == 403

            status, policy = request(base, "/api/staff/policy/schedule", {
                "officeId": "caloocan", "times": "09:00,10:00", "capacity": "1"}, bearer=head)
            assert status == 200 and policy["policy"]["times"] == ["09:00", "10:00"]
            status, slots = request(base, "/api/slots?officeId=caloocan&date=" + day)
            assert status == 200 and len(slots["slots"]) == 2
            assert all(slot["capacity"] == 1 for slot in slots["slots"])
            status, checklist = request(base, "/api/staff/policy/checklist", {
                "officeId": "caloocan", "serviceId": "new-permit",
                "documents": "Local clearance\nSite sketch", "instructions": "Use counter 4.",
            }, bearer=head)
            assert status == 200, checklist
            status, public_checklist = request(base, "/api/checklist?officeId=caloocan&serviceId=new-permit")
            assert status == 200 and public_checklist["checklist"]["documents"] == ["Local clearance", "Site sketch"]
            status, extra = request(base, "/api/appointments", {
                "serviceId": "new-permit", "officeId": "caloocan", "date": day, "time": "09:00",
                "industryId": "retail", "employees": "2", "businessName": "Checklist Test",
                "businessAddress": "Caloocan", "ownerName": "Checklist Applicant",
                "email": "checklist@example.com", "phone": "09123456789",
            })
            assert status == 201 and "Local clearance" in extra["appointment"]["quote"]["requirements"]
            status, closed = request(base, "/api/staff/policy/closure", {
                "officeId": "caloocan", "date": day, "kind": "EMERGENCY", "reason": "Flooding",
            }, bearer=head)
            assert status == 200 and closed["affectedBookings"], closed
            assert request(base, "/api/slots?officeId=caloocan&date=" + day)[0] == 400
            assert request(base, "/api/staff/simulate-full-day", {
                "officeId": "manila", "date": day}, bearer=head)[0] == 403
            status, seeded_day = request(base, "/api/staff/simulate-full-day", {
                "officeId": "manila", "date": day}, bearer=admin)
            assert status == 200 and seeded_day["created"] == 31, seeded_day
            status, full_slots = request(base, "/api/slots?officeId=manila&date=" + day)
            assert status == 200 and all(slot["remaining"] == 0 for slot in full_slots["slots"])
            assert request(base, "/api/staff/system", bearer=admin)[0] == 200
            assert request(base, "/api/staff/system", bearer=clerk)[0] == 403
            status, reset = request(base, "/api/staff/users/clerk.caloocan/reset-password", {
                "password": "new-strong-clerk-password"}, bearer=admin)
            assert status == 200, reset
            assert request(base, "/api/staff/me", bearer=clerk)[0] == 401
            status, fresh_login = request(base, "/api/staff/login", {
                "username": "clerk.caloocan", "password": "new-strong-clerk-password"})
            assert status == 200, fresh_login
            status, disabled = request(base, "/api/staff/users/clerk.caloocan/disable", {}, bearer=admin)
            assert status == 200 and not disabled["user"]["active"]
            assert request(base, "/api/staff/me", bearer=fresh_login["token"])[0] == 401
            print("Staff access checks passed")
        finally:
            server.terminate()
            try:
                server.wait(timeout=3)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()


if __name__ == "__main__":
    main()
