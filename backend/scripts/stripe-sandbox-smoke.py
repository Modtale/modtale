#!/usr/bin/env python3
"""Explicit opt-in provider contract smoke test; never accepts live credentials or real customer data."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

API_VERSION = "2026-08-26.dahlia"
API = "https://api.stripe.com/v1/"


def validate_environment(env):
    if env.get("MODTALE_STRIPE_SANDBOX_OPT_IN") != "true":
        raise ValueError("Set MODTALE_STRIPE_SANDBOX_OPT_IN=true to explicitly allow fictional Stripe test objects.")
    key = env.get("STRIPE_SECRET_KEY", "")
    if not key.startswith(("sk_test_", "rk_test_", "rkcs_")):
        raise ValueError("Only recognized Stripe test credentials are accepted. Live or unknown credentials are refused.")
    account = env.get("STRIPE_PLATFORM_ACCOUNT_ID", "")
    if not re.fullmatch(r"acct_[A-Za-z0-9]+", account):
        raise ValueError("STRIPE_PLATFORM_ACCOUNT_ID must identify the expected sandbox account.")
    return key, account


def safe_code(value):
    return value if isinstance(value, str) and re.fullmatch(r"[a-z][a-z0-9_]{0,100}", value) else None


class SandboxRun:
    def __init__(self, key, account, directory):
        self.key, self.account, self.directory = key, account, directory
        self.report = {"run_id": "modtale-" + str(uuid.uuid4()), "expected_account_id": account,
                       "api_version": API_VERSION, "started_at": int(time.time()), "scope": "fictional Stripe provider contracts", "checks": []}
        self.counter = 0

    def save(self):
        temporary = self.directory / "report.tmp"
        with temporary.open("w", encoding="utf-8") as out:
            json.dump(self.report, out, indent=2)
        temporary.replace(self.directory / "report.json")

    def record(self, check, passed, **safe):
        self.report["checks"].append({"check": check, "passed": passed, **safe})
        self.save()

    def request(self, method, path, fields=None, expected=(200,)):
        # No configurable host or redirect: credentials are sent only to Stripe's API.
        if not re.fullmatch(r"[A-Za-z0-9_/?=&.%\[\]-]+", path) or path.startswith("/") or ".." in path:
            raise ValueError("Invalid provider path.")
        self.counter += 1
        data = urllib.parse.urlencode(fields or {}).encode() if method == "POST" else None
        headers = {"Authorization": "Basic " + base64.b64encode((self.key + ":").encode()).decode(), "Stripe-Version": API_VERSION}
        if method == "POST":
            headers.update({"Content-Type": "application/x-www-form-urlencoded", "Idempotency-Key": self.report["run_id"] + "-" + str(self.counter)})
        self.report["last_request"] = {"method": method, "path": path, "ordinal": self.counter, "status": "SUBMITTED"}
        self.save()
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs): return None
        opener = urllib.request.build_opener(NoRedirect)
        try:
            with opener.open(urllib.request.Request(API + path, data=data, headers=headers, method=method), timeout=30) as response:
                status, body = response.status, json.load(response)
        except urllib.error.HTTPError as failure:
            status = failure.code
            try: body = json.loads(failure.read())
            except (ValueError, UnicodeError): body = {}
        except (OSError, TimeoutError):
            self.report["last_request"]["status"] = "OUTCOME_UNKNOWN"
            self.save()
            raise ValueError("Provider response unavailable. Inspect this saved run before any retry; no automatic retry was made.") from None
        if not isinstance(body, dict): raise ValueError("Provider response is not the expected JSON object; stopping.")
        self.report["last_request"]["status"] = status
        self.save()
        if status not in expected:
            error = body.get("error", {})
            self.record("provider_request", False, http_status=status, error_type=safe_code(error.get("type")), error_code=safe_code(error.get("code")))
            raise ValueError("Stripe rejected a test request. See the sanitized report; no raw provider body or credentials were saved.")
        return status, body

    @staticmethod
    def test_object(value, prefix):
        if value.get("livemode") is not False or not re.fullmatch(re.escape(prefix) + r"[A-Za-z0-9_]+", str(value.get("id", ""))):
            raise ValueError("Provider object failed test-mode or identity validation; stopping.")
        return value["id"]

    def verify_account(self):
        status, account = self.request("GET", "account", expected=(200, 403))
        if status == 200:
            if account.get("id") != self.account or account.get("object") != "account":
                raise ValueError("Provider account does not match the configured sandbox; stopping.")
            self.record("platform_account_binding", True)
        elif self.key.startswith("rkcs_"):
            self.record("platform_account_binding", False, limitation="Anonymous sandbox lacks account-read permission. Connect and payout validation remain blocked.")
        else:
            raise ValueError("Could not verify platform account; stopping before test mutations.")
    def execute(self):
        self.verify_account()
        checkout = {"mode": "payment", "success_url": "https://example.invalid/modtale-test/success", "cancel_url": "https://example.invalid/modtale-test/cancel",
                    "line_items[0][price_data][currency]": "usd", "line_items[0][price_data][product_data][name]": "Modtale fictional support test",
                    "line_items[0][price_data][unit_amount]": "500", "line_items[0][quantity]": "1", "metadata[intentId]": self.report["run_id"],
                    "payment_intent_data[metadata][intentId]": self.report["run_id"]}
        _, session = self.request("POST", "checkout/sessions", checkout)
        session_id = self.test_object(session, "cs_")
        if session.get("payment_status") != "unpaid" or session.get("amount_total") != 500:
            raise ValueError("Checkout fixture did not preserve unpaid status and exact amount.")
        self.record("hosted_checkout_creation", True, object_id=session_id, limitation="Hosted payment completion and application callback are not exercised by this API test.")
        _, expired = self.request("POST", "checkout/sessions/" + session_id + "/expire")
        self.record("checkout_expiration", expired.get("status") == "expired", object_id=session_id)
        checkout.pop("payment_intent_data[metadata][intentId]")
        checkout.update({"mode": "subscription", "line_items[0][price_data][recurring][interval]": "month", "subscription_data[metadata][intentId]": self.report["run_id"]})
        _, monthly = self.request("POST", "checkout/sessions", checkout)
        monthly_id = self.test_object(monthly, "cs_")
        self.record("monthly_checkout_creation", monthly.get("mode") == "subscription" and monthly.get("payment_status") == "unpaid", object_id=monthly_id)
        self.request("POST", "checkout/sessions/" + monthly_id + "/expire")
        _, payment = self.request("POST", "payment_intents", {"amount": "500", "currency": "usd", "payment_method": "pm_card_visa",
                "payment_method_types[]": "card", "confirm": "true", "metadata[modtale_sandbox_run]": self.report["run_id"]})
        payment_id = self.test_object(payment, "pi_")
        if payment.get("status") != "succeeded" or payment.get("amount_received") != 500:
            raise ValueError("Fictional payment did not succeed with exact cents.")
        self.record("fictional_payment", True, object_id=payment_id)
        expanded_status, expanded = self.request("GET", "payment_intents/" + payment_id + "?expand%5B%5D=latest_charge.balance_transaction", expected=(200, 403))
        if expanded_status == 200: self.test_object(expanded, "pi_")
        charge = expanded.get("latest_charge")
        transaction = charge.get("balance_transaction") if isinstance(charge, dict) else None
        if isinstance(transaction, dict):
            conserved = isinstance(transaction.get("amount"), int) and isinstance(transaction.get("fee"), int) and transaction.get("net") == transaction["amount"] - transaction["fee"]
            self.record("actual_charge_fee", conserved, object_id=transaction.get("id"), currency=transaction.get("currency"), amount_cents=transaction.get("amount"), fee_cents=transaction.get("fee"), net_cents=transaction.get("net"), availability=transaction.get("status"))
        else:
            self.record("actual_charge_fee", False, limitation="Actual balance transaction is pending or unavailable. No estimated fee is substituted.")
        for amount, label in [(100, "partial_refund"), (400, "remaining_refund")]:
            _, refund = self.request("POST", "refunds", {"payment_intent": payment_id, "amount": str(amount), "metadata[modtale_sandbox_run]": self.report["run_id"]})
            self.record(label, refund.get("status") == "succeeded" and refund.get("amount") == amount and refund.get("payment_intent") == payment_id, object_id=refund.get("id"))
        status, decline = self.request("POST", "payment_intents", {"amount": "500", "currency": "usd", "payment_method": "pm_card_chargeDeclined", "payment_method_types[]": "card", "confirm": "true"}, expected=(402,))
        self.record("declined_fictional_payment", status == 402 and decline.get("error", {}).get("type") == "card_error")
        self.report["finished_at"] = int(time.time())
        self.report["remaining"] = ["Application authenticated Checkout and persisted webhook fulfillment", "Recurring renewal and portal cancellation", "Connect onboarding and transfers with full test permissions", "Authentic webhook delivery", "Live approvals and fee policies"]
        self.save()


    def advance_clock(self, clock_id, timestamp):
        self.request("POST", "test_helpers/test_clocks/" + clock_id + "/advance", {"frozen_time": str(timestamp)})
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            _, clock = self.request("GET", "test_helpers/test_clocks/" + clock_id)
            self.test_object(clock, "clock_")
            if clock.get("status") == "ready": return
            if clock.get("status") != "advancing": raise ValueError("Test clock entered an unexpected state.")
            time.sleep(2)
        raise ValueError("Test clock is still advancing. Review its saved ID before any further mutation.")

    def verify_invoice_cash(self, invoice_id, label):
        if not re.fullmatch(r"in_[A-Za-z0-9]+", str(invoice_id)): raise ValueError("Missing expected invoice identity.")
        _, invoice = self.request("GET", "invoices/" + invoice_id)
        self.test_object(invoice, "in_")
        _, payments = self.request("GET", "invoice_payments?invoice=" + invoice_id + "&status=paid&limit=100")
        rows = payments.get("data", [])
        if payments.get("has_more") is not False or len(rows) != 1: raise ValueError("Expected one complete cash invoice payment for this fixture.")
        payment = rows[0].get("payment", {})
        if payment.get("type") != "payment_intent": raise ValueError("Invoice was not paid by an actual test PaymentIntent.")
        pi = payment.get("payment_intent")
        if not re.fullmatch(r"pi_[A-Za-z0-9]+", str(pi)): raise ValueError("Invoice payment identity is invalid.")
        _, intent = self.request("GET", "payment_intents/" + pi)
        self.test_object(intent, "pi_")
        parent = invoice.get("parent", {})
        passed = (invoice.get("id") == invoice_id and invoice.get("status") == "paid" and invoice.get("currency") == "usd" and invoice.get("amount_paid") == 500
                  and parent.get("type") == "subscription_details" and parent.get("subscription_details", {}).get("metadata", {}).get("intentId") == self.report["run_id"]
                  and rows[0].get("invoice") == invoice_id and rows[0].get("amount_paid") == 500
                  and intent.get("id") == pi and intent.get("currency") == "usd" and intent.get("status") == "succeeded" and intent.get("amount_received") == 500)
        self.record(label, passed, invoice_id=invoice_id, payment_id=pi)
        if not passed: raise ValueError("Expected exact paid cash invoice was not verified.")

    def execute_billing(self, use_clock=True):
        self.verify_account()
        clock_id = None
        if use_clock:
            now = int(time.time())
            _, clock = self.request("POST", "test_helpers/test_clocks", {"frozen_time": str(now), "name": self.report["run_id"]})
            clock_id = self.test_object(clock, "clock_"); self.record("test_clock", True, object_id=clock_id)
        _, method = self.request("POST", "payment_methods", {"type": "card", "card[token]": "tok_visa"})
        method_id = self.test_object(method, "pm_")
        customer_fields = {"payment_method": method_id, "invoice_settings[default_payment_method]": method_id, "metadata[modtale_sandbox_run]": self.report["run_id"]}
        if clock_id: customer_fields["test_clock"] = clock_id
        _, customer = self.request("POST", "customers", customer_fields)
        customer_id = self.test_object(customer, "cus_"); self.record("fictional_customer", True, object_id=customer_id)
        _, product = self.request("POST", "products", {"name": "Modtale fictional recurring support", "metadata[modtale_sandbox_run]": self.report["run_id"]})
        product_id = self.test_object(product, "prod_")
        _, price = self.request("POST", "prices", {"product": product_id, "unit_amount": "500", "currency": "usd", "recurring[interval]": "month"})
        price_id = self.test_object(price, "price_")
        _, subscription = self.request("POST", "subscriptions", {"customer": customer_id, "items[0][price]": price_id, "metadata[intentId]": self.report["run_id"]})
        subscription_id = self.test_object(subscription, "sub_")
        self.record("recurring_subscription", subscription.get("status") == "active", object_id=subscription_id)
        first_invoice = subscription.get("latest_invoice")
        self.verify_invoice_cash(first_invoice, "initial_recurring_cash_payment")
        if use_clock:
            items = subscription.get("items", {}).get("data", [])
            if len(items) != 1 or not isinstance(items[0].get("current_period_end"), int): raise ValueError("Unexpected subscription item period shape.")
            self.advance_clock(clock_id, items[0]["current_period_end"] + 7200)
            _, renewed = self.request("GET", "subscriptions/" + subscription_id)
            self.test_object(renewed, "sub_")
            renewal_invoice = renewed.get("latest_invoice")
            if renewal_invoice == first_invoice: raise ValueError("Clock advance did not generate a new renewal invoice.")
            self.verify_invoice_cash(renewal_invoice, "renewal_cash_payment")
        _, configuration = self.request("POST", "billing_portal/configurations", {"features[subscription_cancel][enabled]": "true", "features[subscription_cancel][mode]": "at_period_end", "features[payment_method_update][enabled]": "true"})
        configuration_id = self.test_object(configuration, "bpc_")
        _, portal = self.request("POST", "billing_portal/sessions", {"customer": customer_id, "configuration": configuration_id, "return_url": "https://example.invalid/modtale-test/return"})
        self.test_object(portal, "bps_")
        self.record("billing_portal_session", portal.get("configuration") == configuration_id and portal.get("customer") == customer_id and isinstance(portal.get("url"), str), configuration_id=configuration_id,
                    limitation="Portal URL is intentionally not saved. This tests session creation; interactive cancellation remains separate.")
        if use_clock:
            _, canceled = self.request("POST", "subscriptions/" + subscription_id, {"cancel_at_period_end": "true"})
            self.test_object(canceled, "sub_")
            self.record("cancellation_scheduled", canceled.get("cancel_at_period_end") is True and canceled.get("status") == "active", object_id=subscription_id)
            self.advance_clock(clock_id, canceled["items"]["data"][0]["current_period_end"] + 7200)
            _, ended = self.request("GET", "subscriptions/" + subscription_id)
            self.test_object(ended, "sub_")
            self.record("cancellation_at_period_end", ended.get("status") == "canceled", object_id=subscription_id)
        else:
            _, ended = self.request("DELETE", "subscriptions/" + subscription_id + "?invoice_now=false&prorate=false")
            self.test_object(ended, "sub_")
            self.record("immediate_test_cancellation", ended.get("status") == "canceled", object_id=subscription_id)
            self.record("clock_based_renewal", False, limitation="Not attempted in the initial-only scenario. This does not substitute for test-clock renewal and period-end cancellation coverage.")
        self.report["finished_at"] = int(time.time())
        self.report["remaining"] = ["Interactive hosted Checkout and portal completion", "Authenticated application and persisted webhook fulfillment", "Connect and payout verification", "Live approvals and actual later fees"]
        if not use_clock: self.report["remaining"].append("Clock-based renewal and period-end cancellation")
        self.save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", required=True, type=Path, help="New private directory outside the repository; contains sanitized audit results, never API keys")
    parser.add_argument("--scenario", choices=("payments", "billing", "billing-initial"), default="payments")
    args = parser.parse_args()
    try:
        key, account = validate_environment(os.environ)
        directory = args.state_dir.resolve()
        repo = Path(__file__).resolve().parents[2]
        if directory == repo or repo in directory.parents: raise ValueError("Keep sandbox run state outside the repository.")
        if directory.exists(): raise ValueError("Run directory already exists. Review its outcome; this runner never blindly repeats an uncertain run.")
        os.umask(0o077); directory.mkdir(mode=0o700, parents=True)
        run = SandboxRun(key, account, directory); run.report["scenario"] = args.scenario; run.save()
        if args.scenario in ("billing", "billing-initial"): run.execute_billing(use_clock=args.scenario == "billing")
        else: run.execute()
        print(json.dumps(run.report, indent=2))
    except ValueError as error:
        print(str(error), file=sys.stderr); return 1
    return 0

if __name__ == "__main__": sys.exit(main())
