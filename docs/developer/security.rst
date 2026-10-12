.. _dev-security:

========
Security
========

.. note::

   **Stub page -- no content yet.** No phase document names this page individually, so it falls to Phase 16's *complete developer documentation tree* deliverable (``phases/PHASE-16-release.rst``). Its material comes from Phase 02 (``R-SEC-01``), Phase 05 (``R-SEC-02``, ``R-SEC-05``, ``R-SEC-06``) and Phase 12 (``R-SEC-03``, ``R-SEC-04``), and Phase 16 is listed as contributing to ``R-SEC-01``.

   Phase 01 created it so that the documentation tree builds strictly
   (``sphinx-build -n -W``) from the start and so that the page has a
   stable name to link to. Phase 12 added the section below on credentials
   and upload (``R-SEC-03``, ``R-SEC-04``) on 2026-10-12; the rest of the
   page is still to be written.

What this page will cover
=========================

The project's security rules in one place: no secret in a log, a provenance
record or an export; downloads verified before use; archives extracted without
path traversal; credentials kept out of every file CometGUI writes, and the one
outward-facing action -- upload -- started only by the scientist.

Credentials and upload (Phase 12)
=================================

The only credential the application holds is a Limelight submit-import key.
How it is handled, in brief (the detail, with the tests, is in
:ref:`dev-limelight-integration`):

* **One way in.** ``org.cometgui.domain.secrets.SessionSecrets.admit`` is the
  only way to make a ``Credential``; it registers the value with the session's
  redactor before it returns, and a ``Credential`` prints nothing of itself.
  The key is typed into a password field bound to nothing and emptied as soon
  as it is handed over.
* **Never on an argument array, in a URL, a log, the provenance record, the
  event log or an export.** The upload is an in-JVM client; the key travels only in
  the protocol's request bodies and file-parameters header, over ``https`` --
  plain ``http`` is accepted only to loopback. Redirects are never followed.
  Server messages are redacted with the key registered.
* **Kept between uses** only where it can be done without exposing it: the
  Linux Secret Service through ``secret-tool`` with the secret on standard
  input, where installed and answering (proved only against a scripted
  stand-in); otherwise, and on macOS and Windows, in memory for the session.
  Never in a file under a project.
* **Upload only on an explicit action** (``R-SEC-04``): no engine step plans
  one; ``LimelightUploads`` sends only on a single-use confirmation made from
  the destination the interface showed; architecture rules confine the client
  to that service and forbid product code from setting or choosing a proxy.
* **Tests cannot reach a real server**: every test JVM that can upload routes
  non-loopback HTTP(S) into a loopback sinkhole (``LoopbackGuard``), and a
  catch fails the test.
* **Gate 7 is proved by search**: after converting, uploading and exporting, a
  seeded key is searched for, in every form, in every file under the project
  and the tool cache, every file written during the test under the temporary
  and home directories, the window's texts and the console.
