.. _dev-limelight-upload-spike:

==================================================================
How an upload reaches Limelight, and what the fake must be: spike
==================================================================

:Phase: 12 -- Limelight Conversion and Upload
:Work unit: 1 of 9 -- the spike that decides the shape of units 5 and 7
:Date: 2026-10-11
:Script: ``scripts/spikes/limelight_upload_spike.py`` (Java beside it in
         ``scripts/spikes/limelight_upload_spike/``)
:Status: Evidence. Throwaway code; nothing here is product code. It decides
         nothing that belongs to a ``D-`` item, and it makes no
         specification amendment.

.. contents:: Contents
   :depth: 2
   :local:

Verdict
=======

**Analytics and third-party contact: none, in either JAR.** Neither
``limelightSubmitImport.jar`` v8.3.1 nor the pinned converter
``cometPercolator2LimelightXML.jar`` v2.8.1 contains an analytics client, an
update check or any third-party host it contacts. The importer opens exactly
one kind of connection -- to the Limelight URL it is given -- and a real
conversion opens no socket at all. See `Network and analytics`_.

**The protocol is small, documented by its own source, and reasonable to
reimplement.** An upload is five XML-over-HTTP ``POST`` calls (four for a
Limelight XML without a FASTA): auth test, initialise, upload the file as one
raw request body, final submit. The submit key travels inside the XML of every
call. The server returns an **import tracking id** (the protocol calls it
``uploadKey``) and an ``importerSubDir``; nothing else. See `The protocol`_.

**The fake is built and proven.** A loopback-only fake of the server side
(``FakeLimelight.java``) validates every request and response against the
wire schema JAXB derives from the importer's own classes, and validates the
received Limelight XML against ``limelight-xml.xsd`` from the converter JAR.
The **real** importer JAR uploaded the real Phase 00 output to it and was
accepted (exit 0, tracking id issued, bytes intact); the three Phase 00
corruptions were rejected in the protocol's own 400 form (importer exit 6);
and a 150-line ``java.net.http`` client did both and, unlike the importer,
came back holding the tracking id and the server's error message. See
`The fake, exercised`_.

**Findings that bind the design**, each with its evidence below:

#. **The importer takes its key only on the command line.** No file, no
   environment variable, no standard input, and the ``java`` launcher does
   not expand ``@argfile`` after ``-jar``. A key on argv was visible in
   ``/proc/<pid>/cmdline`` and was written in full into a JFR recording of
   that run. Only a launcher shim that reads the key from standard input and
   calls the importer's ``main`` in the child JVM keeps it off argv (shown).
#. **The importer does not print the tracking id** unless its logging is
   reconfigured to DEBUG, and **does not relay the server's error message**
   when a file is refused. It prints the first two characters of the key on
   every run. Its exit status ``1`` means "help", "version", "wrong key",
   "wrong project" and "uncaught exception" alike.
#. **No endpoint lets a submit key list projects.** Project listing needs a
   logged-in web session. "Project selection" can only be the scientist typing
   a project id (an integer), checked by the auth-test call before anything is
   sent.
#. **The server's import log is not reachable with a submit key.** The real
   server validates the XML later, asynchronously, in its importer; the status
   of that import is visible only in the web application. "Live upload and
   import logs" can be met for the upload; the import's outcome can only be
   pointed to (the project page URL). This is a specification question for
   tier 1.
#. **The importer sends the file's absolute and canonical local paths** to the
   server with every upload. An in-product client need not.

**Recommendation: option B**, an in-product client in
``org.cometgui.tools.limelight``, with the one fake shared by a
``cometgui-tools`` test-jar (a POM change for tier 1 or 2 to approve) and a
proxy-sinkhole test guard. Costed in `Recommendation`_.

What was run
============

.. list-table:: Inputs and their identity
   :header-rows: 1
   :widths: 30 70

   * - Input
     - Identity, as the script re-establishes it every run
   * - ``limelightSubmitImport.jar`` (the importer)
     - **v8.3.1**, the newest ``yeastrc/limelight-core`` release (published
       2026-09-28). Downloaded once, by release-asset URL
       ``https://github.com/yeastrc/limelight-core/releases/download/v8.3.1/limelightSubmitImport.jar``,
       to ``scratch/limelight-upload-spike/artefacts/v8.3.1__limelightSubmitImport.jar``.
       5,641,681 bytes; SHA-256
       ``281d882ab6a2b0e12a0138dc19bfbae680069d569f7e070cf37e7dee7b03217b``
       (equal to the digest GitHub publishes for the asset); MD5
       ``54763dcd99cfcacef689961b7fde28c8``. Licence **Apache-2.0**
       (``limelight-core`` ``LICENSE`` at the tag). Its manifest names
       ``GIT-Full-HASH: 61554282dc77ae48abdb3a382a8f0dce3a2f0be6`` and
       ``GIT-Tag-at-HEAD: v8.3.1``; submit program version **16**. Bundles
       log4j 2.26.1, slf4j 2.0.18, JAXB 3.0.2, commons-lang3 3.20.0,
       commons-io 2.22.0 and jargs.
   * - ``yeastrc/limelight-core`` source
     - Tag ``v8.3.1``, commit ``61554282dc77ae48abdb3a382a8f0dce3a2f0be6``
       -- the same commit the JAR's manifest names -- cloned read-only into
       ``scratch/limelight-core-src``. Every ``file:line`` below is checked
       by the script against that tree (45 citations).
   * - ``cometPercolator2LimelightXML.jar`` (the converter)
     - ``scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar``,
       SHA-256 ``843573396ce0...74ece1``, equal to all five
       ``limelight-converter`` rows of ``manifests/tools.json``.
   * - ``limelight-xml.xsd``
     - The converter's JAR-root entry: 65,905 bytes, SHA-256
       ``e8c1b92098db3b4364f06101b1def43675cd956ddf8758de25b692e3f698d242``
       -- **verified equal to Phase 00's record**.
   * - Phase 00 Limelight XML (K562, ``D-006``: on disk only, never committed)
     - ``scratch/scientific-path/limelight/limelight.xml``, 12,797,113 bytes,
       SHA-256 ``25c218a8...8c0a11``; and the three corrupt copies in
       ``scratch/scientific-path/corrupt/``.

Command::

   . tools/env.sh
   python3 scripts/spikes/limelight_upload_spike.py            # all 12 sections
   python3 scripts/spikes/limelight_upload_spike.py --only importer,reject

Run on 2026-10-11: **93 of 93 checks passed, exit 0, 57 seconds** wall time
(64-core host). It needs no network unless the importer JAR or the source
clone is missing from ``scratch/``, in which case it fetches each once from
GitHub and refuses a JAR whose SHA-256 differs from the pin. Everything it
writes goes to ``scratch/limelight-upload-spike/run/`` (``results.json``,
per-run stdout/stderr/exit, the fake's logs and state, JFR recordings).

**The script was shown to fail.** With ``--fake-source`` pointing at a copy of
the fake whose schema check was replaced by "accept everything", nine checks
went red and it exited 1 (the importer then exited **0** on the truncated
file); with a copy that logs the key unredacted, the "fake's log never holds
the key" check went red and it exited 1. Two further controls are built in:
the ``/proc`` sampler must *see* the key in the argv run before its silence in
the stdin runs counts, and JFR must *see* the importer's sockets before its
silence over the converter counts.

The protocol
============

Read from source at ``v8.3.1``, then confirmed on the wire against the real
importer. Paths below are relative to ``scratch/limelight-core-src``; ``CSIW``
is
``limelight_submit_import_client_connector/.../main/CallSubmitImportWebservice.java``,
``SUM`` is ``limelight_submit_import/.../main/SubmitUploadMain.java``, ``SIP``
is ``limelight_submit_import/.../program/SubmitImportProgram.java``.

Endpoints
---------

All are ``POST`` to ``<web-app-url>`` plus the path, where the web-app URL
includes the context (``https://host/limelight``). Request and response
bodies are XML, ``Content-Type`` and ``Accept`` ``application/xml``
(``CSIW:79``, ``CSIW:620-621``).

.. list-table::
   :header-rows: 1
   :widths: 30 26 26 18

   * - Path (``CSIW:101-105``)
     - Request root
     - Response root
     - What it does
   * - ``/d/rws/for-page/project-upload-data-upload-submit-pgm-auth-test``
     - ``submitImport_AuthTest_Request_PgmXML``
     - ``submitImport_AuthTest_Response_PgmXML``
     - Checks key and project; returns the server's program version
   * - ``/d/rws/for-page/project-upload-data-upload-get-fasta-file-upload-accepted-submit-program``
     - ``submitImport_Get_FASTAFileUploadAccepted_Request_PgmXML``
     - ``..._Response_PgmXML``
     - Only when ``--fasta-file`` is given (``SUM:238-297``)
   * - ``/d/rws/for-page/project-upload-data-upload-initialize-from-submit-pgm``
     - ``submitImport_Init_Request_PgmXML``
     - ``submitImport_Init_Response_PgmXML``
     - Creates the import; returns ``uploadKey``
   * - ``/d/rws/for-page/project-upload-data-upload-file-from-submit-pgm``
     - raw file bytes; parameters in a header
     - ``submitImport_UploadFile_Response_PgmXML``
     - One call per file
   * - ``/d/rws/for-page/project-upload-data-upload-submit-from-submit-pgm``
     - ``submitImport_FinalSubmit_Request_PgmXML``
     - ``submitImport_FinalSubmit_Response_PgmXML``
     - Queues the import; returns ``importerSubDir``

The importer called exactly the four non-FASTA endpoints, in that order, on
every successful run.

The wire format is JAXB's, and it is not the Java field names
--------------------------------------------------------------

Requests and responses are the JAXB (Jakarta XML Binding 3) serialisation of
the ``request_response_objects`` classes, almost everything an XML
**attribute**. JAXB drops underscores from attribute names: the Java field
``statusFail_ErrorMessage`` is the attribute ``statusFailErrorMessage``,
``submitProgramVersionNumber_Current_Per_Webapp`` is
``submitProgramVersionNumberCurrentPerWebapp``. A first fake written from the
field names was silently misread by the real importer (the server version came
back ``null``, so it fell back to the old raw-XML header). The script therefore
asks JAXB, with the importer's own classes, for the schema
(``WireSchema.java``), and requires it to equal the committed
``scripts/spikes/limelight_upload_spike/submit-import-wire-v16.xsd`` (15,841
bytes). The fake validates every request it receives and every response it
sends against it; the in-JVM client's requests passed it too.

The fields that matter:

* every request carries ``submitProgramVersionNumber`` (16),
  ``projectIdentifier`` (the project id, a string holding an integer; the CLI
  insists on an integer, ``SIP:408``) and ``userSubmitImportProgramKey``;
* every response carries ``statusSuccess`` and may carry
  ``statusFailErrorMessage`` plus boolean reason flags
  (``userSubmitImportProgramKeyNotFound``, ``projectIdNotFound``,
  ``projectLocked``, ``userNotAuthorizedForProject``,
  ``submitProgramVersionNumberNotAccepted``, ...);
* the initialise response carries ``uploadKey``; the final-submit response
  carries ``importerSubDir``.

How the file travels
--------------------

**One POST per file, the whole file as the raw request body**, streamed with
``setFixedLengthStreamingMode`` so files over 2 GB are not buffered
(``CSIW:606``, ``CSIW:640-657``). There is no chunking in this client. The
file's parameters -- project, ``uploadKey``, file index and type (1 = Limelight
XML, ``LimelightSubmit_FileImportFileType``), file name, the file's
**SHA-256**, the key, and the local absolute and canonical paths -- go in a
request **header**: as raw single-line XML in ``limelight_upload_file_params_xml``
for servers older than program version 16, or Base64-encoded in
``limelight_upload_file_params_xml_base_64`` when the auth-test response says
the server is at version 16 or later (``CSIW:302-334``). The fake advertises
16, and the real importer then sent the Base64 header (observed).

Limits and integrity: the server refuses a Limelight XML larger than its
configured maximum, **1 GB by default** (``FileUploadMaxFileSize_Config_WithConstantsDefaults.java:45``,
overridable by ``LIMELIGHT_LIMELIGHT_XML_FILE_MAX_SIZE_IN_GB``), answering
``fileSizeLimitExceeded`` with ``maxSizeFormatted``. It recomputes SHA-256 over
the body and answers ``uploadedFileSHA256HashNotMatchParamFileSHA256Hash`` on
a mismatch, which the importer retries (``SUM:519-536``). Timeouts: connect 8
minutes, read 6 hours (``CSIW:91-95``).

The server also has newer ``project-upload-data-v2-upload-file-*`` endpoints
(initialise, chunked upload, submit, cancel), several with ``-from-submit-pgm``
variants, but the v8.3.1 importer does not use them; the server keeps the v1
upload path "to support OLD Submit Import Program (and current Submit Import
Program since not yet updated)" (``AA_RestWSControllerPaths_Constants.java:207``).

How the key travels
-------------------

Inside the XML body of every call, as the attribute
``userSubmitImportProgramKey``, and inside the parameters header of the file
upload. Not in a URL, not as an HTTP authentication header. Over plain
``http`` it would therefore cross the network in clear text; a product client
should refuse a non-loopback ``http`` URL.

What comes back
---------------

.. list-table::
   :header-rows: 1
   :widths: 24 76

   * - Identifier
     - Meaning and evidence
   * - ``uploadKey``
     - Returned by initialise (``Project_UploadData_V1_UploadInitialize_RestWebserviceController.java:589``).
       **It is the server's import tracking id**: the final-submit handler
       reads it as ``int importTrackingId = uploadKey;``
       (``Project_UploadData_UploadSubmit_RestWebserviceController.java:523``)
       and queues that id (line 1063). This is the identifier to retain.
   * - ``importerSubDir``
     - Returned by final submit (same file, line 460):
       ``import_dir_for_id_`` plus the tracking id zero-padded to seven
       digits (``Limelight_XML_ImporterWrkDirAndSbDrsCmmn.java:100``). A
       server-side directory name; it adds nothing the tracking id does not.
   * - project page URL
     - Not returned. Constructible as ``<web-app-url>/d/pg/project/<id>``
       (``AA_PageControllerPaths_Constants.java:74``); that is where the
       scientist sees the import's progress and result.
   * - the search's own id
     - Not available to the client: it is assigned when the server's
       asynchronous importer runs, after the upload has finished.

How errors are reported
-----------------------

As a protocol answer, not as an HTTP failure: ``statusSuccess="false"`` with
reason flags and, for most refusals, ``statusFailErrorMessage``; mostly with
HTTP 200. The file-upload endpoint answers HTTP **400 with the XML body**
(``Project_UploadData_V1_UploadFile_OldFor_OldSubmitImportProgram_RestWebserviceController.java:308``),
and the client reads that body as the response (``CSIW:732-779``; the
condition on line 732 compares the full URL with a path, so in effect every
400 with a body is treated this way). A body-less 400, a 404 or a 5xx is a
transport error. The upload response's flags include
``limelightXMLFileFailsInitialParse`` and ``limelightXMLFilerootXMLNodeIncorrect``;
the real server does not set them in this version, because it does not parse
the XML at upload at all -- see the next section. The fake uses them.

Where the real server validates the XML
---------------------------------------

**Later, not at upload.** The v1 upload stores the bytes; the final submit
checks only the file list; validation against ``/limelight-xml.xsd`` happens
when the server's own importer process picks the job up
(``limelight_importer/.../ImporterCoreEntryPoint.java:421``). So against a
real server an invalid file is *accepted* by the upload and fails later, where
only the web application shows it. The fake deliberately rejects at upload,
because a test must be able to observe the rejection.

Projects: identified, not listed
--------------------------------

A project is identified by its integer id alone. The auth-test call answers
whether the key may submit to it (``projectIdNotFound``, ``projectLocked``,
``userNotAuthorizedForProject`` -- the key's owner must be a project owner,
``Validate_UserSubmitImportPgrogramKey_Access_ToWebservice_ForAccessLevelAnd_ProjectId.java:113``).
**No endpoint lets a submit key list projects.** The only controllers that
read a submit key are the nine upload controllers; the project list
(``d/rws/for-page/project-list``) requires a logged-in session
(``ProjectsList_RestWebserviceController.java:117``), as do the upload status
lists. "Project selection as required by the Limelight API" therefore means
an id the scientist types (and may have remembered), verified with the
auth-test call and shown before sending.

The importer, ``limelightSubmitImport.jar``
===========================================

Its help, in full
-----------------

``java -jar limelightSubmitImport.jar --help`` (exit status **1**)::

   Submit Import Program Version: 16
   Submit Import Help:

   --limelight-web-app-url=<URL to Limelight web app, including the "/limelight" >
            (may be different from "/limelight"
             if the deployed war file was changed from "limelight.war")

       **  See information at end of help about configuration file

       **     See below for Submit ONLY Scan Files with NO Limelight XML file

   -V  display version (Capitol V)

   -r (--retry-count-limit=) <retry count as integer>
      (defaults to 1)
      (Optional)
      (Only applies to errors connecting to server. Response HTTP status code not 200.)
      (Each retry adds 5 seconds of additional wait time.  So retry delays are 5 seconds, 10, 15, ...)
      (Example:  --retry-count-limit=3  will try immediately, in 5 seconds, in 10 seconds, in 15 seconds)

   --user-submit-import-key=

   -p (--project-id=) <project id>

   -i (--limelight-xml-file=) <limelightXML file>

   --fasta-file=<FASTA file>   : Optional: MUST be same filename as in limelightXML file

   --fasta-file-send-only-if-possible   :  Optional:  Only send fasta file if Limelight is configured to accept FASTA file.
                                              (If this is not set and --fasta-file=<FASTA file> is set and Limelight is NOT configured to accept FASTA file the submit will fail)

   -s (--scan-file=)  <scan file>
      (Optional.  If not have then MUST have '-n' or '--no-scan-files' (parameter listed next)
      (Can have more than one scan file.
       Repeat the:
       	-s (--scan-file=)  <scan file>
       for each scan file.)
      (If ANY scan files are listed, ALL scan filenames in limelightXML file must be listed)

   -n (--no-scan-files)  :  If no scan files


   --add-file=  <additional file to save to search>
      (Optional.  Save other additional files to be downloadable when view search
       Repeat the:
       	--add-file=  <additional file to save to search>
       for each file.)


   --search-description=

   --no-search-description

   --search-short-label=      : Short Label for Search.  Same as 'Short Label:' in the web app.  Value will be truncated to the max allowed length if necessary

   --path=					   : Displayed in "Path" on website.

   --send-search-path         : Send current directory as "Path"
   								(Not allowed if '--path=' is set)

   --search-tag=   :  tag string to put on the imported search.
   					(To specify the category, make the parameter value "<category>~<tag string>"  so "~" is the separator)
   					(There is a Max Length of tag string allowed.  Tag string will be truncated to the max allowed length if necessary)
   					(Can have multiple, repeat the --search-tag=)

     -------------

     Submit ONLY Scan Files with NO Limelight XML file - MUST have at least one scan file

     --no-limelight-xml-file : NO Limelight XML file

     -s (--scan-file=)  <scan file>
      (Can have more than one scan file.
       Repeat the:
       	-s (--scan-file=)  <scan file>
       for each scan file.)

   	*** When Scan File(s) submitted without a Limelight XML file, each Scan File will be submitted separately
   			-- Each scan file will be listed as a separate import
   			-- Some scan files may be successfully submitted even if this program fails.


   	Ignored: --search-description= --search-short-label=  --send-search-path  --search-tag=

     -------------

     To Test that user can submit to the project but not submit a file, use:
     (No files, search description, or send search path is allowed when this property is set)

   --auth-test

     -------------

     Test run only.  No connection to server is made.  Nothing submitted.  Useful for 'dry run' testing.

   --test-run-only-nothing-submitted-no-server-connection-made

     -------------
     For Debugging

   --list-command-line-params : List the command line parameters passed to the Java program, one per line

   -----------------------------------

   Configuration File:

       run the program with --help-configuration-file for more information

   A configuration file may be specified as a parameter
   (or included in a script used to run the executable)
   or embedded in the executable jar file.

       If a script is provided to run this program,
       it may pass '-c' (or '--config=') with a value.
       Try running the script without
       --limelight-web-app-url=
       first and see if it is successful.

Exit statuses observed: ``--help`` 1, ``-V`` 1 (prints ``Version Number:
16``), no arguments 1, unknown option 2, success 0, wrong key 1, wrong project
1, a refused file 6, an uncaught exception (connection failure) 1.

The key can only be on the command line
---------------------------------------

* The key's one source is ``--user-submit-import-key=`` (``SIP:200-201``,
  ``SIP:280``). The importer's source reads no environment variable, no
  standard input and no console (checked over every file). The configuration
  file (``-c``, or ``submit_upload.properties`` embedded in the JAR -- absent
  from v8.3.1) holds only ``limelight.web.app.url`` and
  ``limelight.upload.base.dir`` (``ConfigPropertiesConstants.java:7-9``).
* ``java -jar importer.jar @keyfile ...`` does **not** read the file: the
  launcher expands ``@argfiles`` only before the main class, and the importer
  rejected ``@...`` as an unexpected parameter (exit 2).
* Observed: in the argv run, the full key was in ``/proc/<pid>/cmdline`` --
  readable by any process of the same user, and by ``ps`` -- and **the JFR
  recording of that run contains the full key**, because JFR records the
  JVM's arguments. Any crash file (``hs_err_pid*.log``) or ``jcmd
  VM.command_line`` would carry it the same way.
* The importer prints ``First 2 characters of parameter
  '--user-submit-import-key=' are 'LL'`` on every run (``SIP:289``), repeats
  two characters in auth-failure messages, and with
  ``--list-command-line-params`` prints five (``SIP:127``). It never printed
  the whole key in any run.
* **A shim keeps it off argv.** ``StdinKeyLauncher.java`` is started as
  ``java -cp limelightSubmitImport.jar StdinKeyLauncher.java <args>``, reads
  the key from standard input and calls ``SubmitImportProgram.main`` with the
  key appended. The unmodified importer submitted successfully, and 80
  samples of ``/proc/<pid>/cmdline`` and ``/proc/<pid>/environ`` never held
  the key. In a product this shim would be CometGUI code, on the child's class
  path beside a third-party JAR whose entry point it calls by name.

What it does and does not tell the caller
-----------------------------------------

* **The tracking id is not printed.** It is written only under
  ``log.isDebugEnabled()`` (``SUM:339-343``), and the JAR's own
  ``log4j2.xml`` sets the root logger to ``warn``. With
  ``-Dlog4j2.configurationFile=importer-log4j2-debug.xml`` (raising only
  ``SubmitUploadMain`` to DEBUG) it printed ``submitImport_Init_Response:
  UploadKey: 4101``, equal to what the fake issued. Retrieving the identifier
  through the importer means scraping a debug line.
* **A refused file is reported without the server's reason**:
  ``FAILED sending Limelight XML file to server: <path>`` and ``If this error
  continues, contact the administrator of your Limelight Instance.``, exit 6.
  ``statusFailErrorMessage`` is printed only for auth, FASTA and initialise
  failures.
* It says ``Auth Test Successful.`` on standard output **and** standard error,
  and ``Submission Successful`` at the end; nothing between initialise and
  submit except ``Sent Limelight XML file to server: <path>``. There is no
  byte progress.
* It always sends ``submitterSameMachine="true"`` on initialise
  (``SUM:303``), and the file's absolute and canonical local paths on upload.

Network and analytics
=====================

.. list-table::
   :header-rows: 1
   :widths: 18 41 41

   * - Evidence
     - Importer v8.3.1
     - Converter v2.8.1
   * - Classes of the tool's own packages referencing ``java.net``
     - ``CallSubmitImportWebservice`` (the one connection site,
       ``new URL(webserviceURL).openConnection()``, ``CSIW:568-578``) and
       ``ConfigParams`` (a class-path ``URL`` for its properties file)
     - none (``org/yeastrc``, ``net/systemsbiology``, ``com/per_colator``)
   * - URL literals in the tool's own classes
     - none
     - ``https://github.com/yeastrc/limelight-import-comet-percolator`` and
       ``https://pubmed.ncbi.nlm.nih.gov/21876204/`` (printed help and
       citation text) and XML namespace URIs (``per-colator.com``,
       ``regis-web.systemsbiology.net``)
   * - Keyword scan (``analytics``, ``telemetry``, ``mixpanel``,
       ``segment.io``, ``sentry.io``, ``bugsnag``, ``amplitude``, ``matomo``,
       ``piwik``, ``update check``) over every file in the JAR
     - one hit, a method **name** in commons-lang3:
       ``EventCountCircuitBreaker.updateCheckIntervalData``
     - none
   * - Runtime, JFR ``jdk.SocketRead``/``jdk.SocketWrite`` at threshold 0
     - 85 socket events in a full upload, every one to ``127.0.0.1`` on the
       fake's port
     - **zero** socket events in a real conversion of the Phase 00 inputs
       (12,797,281 bytes written)

The converter's ``Error printing runtime information.`` line, noted by Phase
00, comes from ``MainProgram`` failing to read a class-path text resource it
fills with ``{{URL}}`` and ``{{VERSION}}``; it is local.

Limits of this evidence: JFR records reads and writes, not connection
attempts or DNS lookups, so a connection that never carried a byte would not
appear; the static scans cover that case for the tools' own classes, and the
libraries (log4j 2.26.1 with ``%m{nolookups}``, JAXB with external entities
disabled in the client, ``CSIW:884-891``) were not audited beyond the keyword
and ``java.net`` scans.

The fake, exercised
===================

``FakeLimelight.java`` binds ``InetAddress.getLoopbackAddress()`` on an
ephemeral port, refuses any non-loopback peer, reads the expected key from a
file (never argv), implements the five endpoints with the wire schema's names,
issues tracking ids from 4100, streams the upload to disk while hashing it,
checks the declared SHA-256, then judges the file: root element
``limelight_input``, then ``limelight-xml.xsd`` with external entities and
DTDs disabled. It logs one JSON line per request with the key replaced by
``<redacted len=N>``, and writes a state file the script asserts on.

(a) The real importer, the real file: accepted
-----------------------------------------------

::

   java -jar limelightSubmitImport.jar --retry-count-limit=0 \
       --limelight-web-app-url=http://127.0.0.1:<port>/limelight --project-id=7 \
       --limelight-xml-file=scratch/scientific-path/limelight/limelight.xml \
       --no-scan-files --search-description='CometGUI spike' --search-tag=CometGUI \
       --user-submit-import-key=<seeded>

Standard output (exit **0**)::

   Submit Import Program Version: 16
   First 2 characters of parameter '--user-submit-import-key=' are 'LL'
   Project id: 7
   Connecting to Limelight web app using URL: http://127.0.0.1:43355/limelight

   ********************************************************

   Submit Import: Auth Test Successful.

   Sent Limelight XML file to server: .../scratch/scientific-path/limelight/limelight.xml

   ******************************************

   Submission Successful

The fake's record: tracking id ``4100``, 12,797,113 bytes received with
SHA-256 ``25c218a8...8c0a11`` (equal to the source file), ``VALID``,
submitted, ``importerSubDir`` ``import_dir_for_id_0004100``. One of its log
lines, verbatim apart from the two local paths::

   {"method": "POST", "remote": "127.0.0.1",
    "path": "/limelight/d/rws/for-page/project-upload-data-upload-file-from-submit-pgm",
    "headerNames": ["Accept","Connection","Host","User-agent","Content-type",
                    "Content-length","Limelight_upload_file_params_xml_base_64"],
    "paramsHeader": "limelight_upload_file_params_xml_base_64",
    "requestRoot": "submitImport_UploadFile_Request_Common",
    "requestAttributes": {"absoluteFilenameWPathOnSubmitMachine": "...",
      "canonicalFilenameWPathOnSubmitMachine": "...", "fileIndex": "1",
      "fileSHA256Hash": "25c218a8f5658ea19273703517040c823530b1c5890681b0e231f001408c0a11",
      "fileType": "1", "filename": "limelight.xml", "newHeaderParamsFormatVersion": "1",
      "projectIdentifier": "7", "submitProgramVersion": "16", "uploadKey": "4100",
      "userSubmitImportProgramKey": "<redacted len=34>"},
    "contentLength": 12797113, "validation": "VALID",
    "outcome": "accepted, valid against limelight-xml.xsd", "status": 200}

(b) Corrupt files: rejected by the fake's schema check
-------------------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 22 48 30

   * - File
     - The fake: HTTP 400, ``limelightXMLFileFailsInitialParse="true"``,
       ``statusFailErrorMessage=``
     - The importer
   * - ``bogus-element.xml``
     - ``INVALID: ERROR line 3 col 25: cvc-complex-type.2.4.a: Invalid content
       was found starting with element 'not_in_the_schema'. ...``
     - exit **6**; ``FAILED sending Limelight XML file to server: <path>``;
       no final submit
   * - ``missing-attribute.xml``
     - ``INVALID: ERROR line 2 col 18: cvc-complex-type.4: Attribute
       'fasta_filename' must appear on element 'limelight_input'.``
     - exit 6, same lines
   * - ``truncated.xml``
     - ``INVALID: FATAL line 114132 col 1: XML document structures must start
       and end within the same entity.``
     - exit 6, same lines

These are the messages Phase 00's validator gave for the same files. In none
of the three did the importer print the reason. Two more refusals, from the
auth test: a wrong key gave exit 1 and ``--user-submit-import-key= value is not
found in Limelight. First 2 characters of value: WR``; a wrong project gave
exit 1 and ``Project is not found.  Project Id: 8   --user-submit-import-key=
value first 2 characters of value: LL``.

(c) A throwaway in-JVM client
-----------------------------

``InJvmClient.java`` (about 150 lines, JDK only: ``java.net.http`` and DOM)
does the four calls, reading the key from standard input. Against the same
fake::

   STEP auth-test: statusSuccess=true
   STEP initialize: statusSuccess=true
   STEP upload-file (HTTP 200, 12797113 bytes, sha256 25c218a8f565...): statusSuccess=true
   STEP final-submit: statusSuccess=true
   RESULT {"serverProgramVersion":16,"trackingId":"4100",
           "uploadedSha256":"25c218a8f5658ea19273703517040c823530b1c5890681b0e231f001408c0a11",
           "importerSubDir":"import_dir_for_id_0004100",
           "projectPage":"http://127.0.0.1:43939/limelight/d/pg/project/7","outcome":"SUBMITTED"}

and for each corrupt file, exit 3 with, for example::

   RESULT {..., "outcome":"REFUSED at upload-file",
           "serverSaid":{"limelightXMLFileFailsInitialParse":"true",
             "statusFailErrorMessage":"INVALID: ERROR line 2 col 18: cvc-complex-type.4:
               Attribute 'fasta_filename' must appear on element 'limelight_input'."}}

A wrong key: exit 3, ``"serverSaid":{"userSubmitImportProgramKeyNotFound":"true"}``.
Every request it sent passed the fake's wire-schema check; the key was in
neither its argv nor its environment.

Where the key ended up
----------------------

After all twelve sections the script searched every file the run wrote
(importer, client and fake stdout/stderr, fake logs and state, the received
uploads, JFR recordings, the sinkhole) for the seeded key. It was in exactly
two: its own ``0600`` key file, and ``importer-argv.jfr`` -- the recording of
the one run that put it on argv.

A test-side guard for P12-11
============================

The shape tried: point ``http.proxyHost``/``https.proxyHost`` at a **loopback
sinkhole** that records the request line and answers 403. The JDK's default
``http.nonProxyHosts`` (``localhost|127.*|[::1]``) keeps loopback direct, so
the fake still works, while any other destination is handed to the sinkhole
instead of leaving the machine. The test destination was ``192.0.2.1``
(RFC 5737 TEST-NET-1, reserved; not a Limelight host).

.. list-table::
   :header-rows: 1
   :widths: 30 46 24

   * - Under the guard
     - What the sinkhole recorded
     - Exit
   * - in-JVM client, ``http://192.0.2.1/limelight``
     - ``POST http://192.0.2.1/limelight/d/rws/for-page/project-upload-data-upload-submit-pgm-auth-test HTTP/1.1``
     - 3 (refused)
   * - in-JVM client, ``https://192.0.2.1/limelight``
     - ``CONNECT 192.0.2.1:443 HTTP/1.1``
     - 4 (``Tunnel failed, got: 403``)
   * - importer (stdin shim), ``http://``
     - the same ``POST`` line
     - 1
   * - importer (stdin shim), ``https://``
     - ``CONNECT 192.0.2.1:443 HTTP/1.1``
     - 1
   * - in-JVM client, the loopback fake
     - nothing
     - 0, submitted

JFR over both guarded importer runs showed socket I/O only with the sinkhole.
``java.net.http.HttpClient`` built without ``.proxy(...)`` and
``HttpURLConnection`` both honoured the properties. Two weaknesses: code that
sets ``Proxy.NO_PROXY`` or its own ``ProxySelector`` bypasses it, and over
plain ``http`` the sinkhole receives the request (the key included) -- harmless
on loopback, but the sinkhole must be the test's, never a real proxy.

Credential storage per platform
===============================

Only Linux can execute here, and on this host **nothing keychain-shaped
exists**: no ``secret-tool``, ``gdbus``, ``dbus-send``, ``busctl``,
``gnome-keyring-daemon``, ``kwallet-query`` or ``keepassxc-cli`` on the
``PATH``, no ``DBUS_SESSION_BUS_ADDRESS``, no ``libsecret``. The rest is from
documentation and is **unverified**.

.. list-table::
   :header-rows: 1
   :widths: 14 50 36

   * - Platform
     - Route without the secret on any argv
     - Practicality
   * - Linux
     - ``secret-tool store --label=... service cometgui-limelight server <url>``
       reads the secret from standard input; ``secret-tool lookup ...``
       writes it to standard output (libsecret's command-line tool, Secret
       Service API over D-Bus).
     - Works only where ``libsecret-tools`` is installed **and** a Secret
       Service provider (GNOME Keyring, KWallet, KeePassXC) is on a session
       bus. Often absent on minimal, headless and WSL systems; never
       installable by CometGUI. Needs a fallback.
   * - macOS
     - ``security add-generic-password -w <pw>`` puts it on argv. ``security
       -i`` (interactive mode) reads *commands* from standard input, so
       ``add-generic-password -U -a <acct> -s <svc> -w <pw>`` can be written
       to its stdin; ``security find-generic-password -a <acct> -s <svc> -w``
       prints the secret to stdout with nothing secret on argv.
     - ``security`` ships with macOS, so this is always available. The
       quoting of a secret inside ``-i`` mode is unverified and must be
       tested on a Mac.
   * - Windows
     - ``cmdkey /pass:`` puts it on argv and cannot read secrets back;
       PowerShell has no built-in Credential Manager cmdlets (Windows
       PowerShell 5.1 can reach WinRT ``PasswordVault``). **Without native
       code we ship**: JDK 25's Foreign Function and Memory API (final since
       JDK 22) can call ``advapi32`` ``CredWriteW``/``CredReadW``/``CredDeleteW``
       directly.
     - FFM is the clean route but needs ``--enable-native-access`` on the
       application's JVM and testing on Windows; nothing could be run here.

**Practical recommendation**: one ``CredentialStore`` port; a session-memory
store that always works and is the fallback everywhere; a macOS adapter over
``security -i`` and a Linux adapter over ``secret-tool``, both through the
process service with the secret on standard input, each used only when a probe
shows it works; Windows via FFM, or session-only until someone can test on
Windows. Whatever the store, the key is registered with ``SecretRegistry`` the
moment it is typed, and never written under a project.

Recommendation
==============

**Option B: an in-product Java client of the protocol in
``org.cometgui.tools.limelight``**, with the one fake shared by tests.

.. list-table::
   :header-rows: 1
   :widths: 20 40 40

   * - Concern
     - A: managed, SHA-256-pinned importer JAR via the process service
     - B: in-product client (``java.net.http``)
   * - Key exposure
     - On argv unless CometGUI also ships the stdin shim (product code calling
       a third-party ``main`` by name). On argv it reached ``/proc`` and JFR.
       The importer prints 2 characters of it every run.
     - Never leaves the JVM's heap except in the HTTPS body. Nothing to
       redact in an argument array.
   * - New managed tool
     - Five ``manifests/tools.json`` rows (one JAR, five platforms, as the
       converter); a fifth ``ToolName`` (whose documentation says "there is
       no fifth name") and its pinned-identifier test; installer, cache,
       verification and a version probe (``-V`` exits 1); ``docs/citations.rst``
       (Apache-2.0); and a **specification amendment** adding the importer to
       the managed tools and the Definition of Done -- tier 1 only.
     - None. No download, no amendment. ~300--500 lines of product code plus
       tests; the throwaway client is 150.
   * - Protocol stability
     - Upstream absorbs changes, but each upstream release is a new pin, new
       rows and a re-verification -- and the old pin keeps working only while
       the server accepts its version.
     - CometGUI owns the protocol knowledge. Mitigations: the server accepts
       every client version >= 1 (``SUBMIT_PROGRAM__MINUMUM__VERSION_NUMBER``,
       ``Limelight_SubmitImport_Version_Constants.java:10``, enforced at
       initialise, ``Project_UploadData_V1_UploadInitialize_RestWebserviceController.java:308``)
       and keeps the v1 path for old programs; a refusal arrives as
       ``submitProgramVersionNumberNotAccepted`` and can be reported plainly;
       the committed wire schema and this script detect drift on any new
       upstream release.
   * - Live upload log
     - The importer's stdout: four lines, no progress, no reason for a
       refusal.
     - Ours: each step, byte progress (the body publisher sees every
       chunk), the server's flags and message.
   * - Server-side identifiers
     - Only by raising its log level and scraping
       ``submitImport_Init_Response: UploadKey: N``.
     - The tracking id and ``importerSubDir`` as values; the project page
       URL built from them.
   * - Project selection
     - ``--auth-test`` checks a typed id (another process start).
     - The auth-test call checks a typed id before anything is sent. Neither
       can list projects.
   * - Exit/result semantics
     - Exit 1 is ambiguous (help, version, key, project, exception).
     - Typed outcome per step.
   * - What is sent
     - The local absolute and canonical paths of every file.
     - Only what the protocol needs (the path attributes are optional in the
       wire schema).
   * - Test guard (P12-11)
     - The guard's ``-D`` flags must be added to the child JVM's argument
       array by the process service under test.
     - In-process: install the sinkhole proxy before the client is built, and
       an architecture rule that production code never calls
       ``HttpClient.Builder.proxy`` (so it cannot be bypassed).

**Where the one fake should live** (it is needed by tests in
``cometgui-tools``, ``cometgui-workflow`` and ``cometgui-app``; the latter two
already depend on ``cometgui-tools``):

#. **A** ``cometgui-tools`` **test-jar** (recommended): one
   ``maven-jar-plugin`` ``test-jar`` execution in ``cometgui-tools/pom.xml``
   and a ``<type>test-jar</type><scope>test</scope>`` dependency in the two
   consumer POMs -- three POM edits, which this unit did not make. To verify
   when made: that ``mvn -pl <module> -am test`` resolves the test-jar from
   the reactor without a ``package`` phase, and that the gate harnesses'
   sandbox copies still build.
#. A new ``cometgui-testkit`` module: the cleanest boundary, the highest cost
   (root module list, a new POM, three dependency edits, census and
   architecture-test awareness).
#. Copies per module, as Phase 11 did with ``ProjectXvfb``: no POM change,
   but three copies of a protocol fake will drift, against P12-1's "one of
   everything", unless a test compares them byte for byte.
#. In ``cometgui-tools`` main code: no POM change, but ships a fake server in
   the product. Not recommended.

The fake needs two schemas at run time. The wire schema can be committed as a
test resource (it is generated from the importer's classes; this unit
committed it under ``scripts/spikes/``). The Limelight XSD must come from the
converter JAR, as Phase 00 and ``D-007`` require -- tests that read it from
outside their module must say so in the work log the day they land, because of
the ``--only tests`` sandbox.

What could not be established
=============================

* How a **real** Limelight server answers. By rule nothing contacted one; the
  fake is built from source and from the real client's behaviour, so a
  server-side behaviour the source does not show (a proxy, a WAF, a
  redirect -- the client special-cases 302 -- or a deployment override) is
  unknown.
* That the server's asynchronous import of the Phase 00 file would succeed:
  the fake validates the schema only, as the importer process would first.
* macOS ``security -i`` quoting and Windows FFM credential calls: not run.
* DNS behaviour under the proxy guard (JFR does not record lookups; the JDK
  passes the host name to an HTTP proxy, so none is expected).
* Whether Maven resolves a test-jar from the reactor in this build without
  ``package`` (see above).

Re-running
==========

::

   . tools/env.sh
   python3 scripts/spikes/limelight_upload_spike.py
   # sections: artefacts source cli scan wire importer reject client guard converter keychain leak
   python3 scripts/spikes/limelight_upload_spike.py --only guard
   # falsifiability: run an injured copy of the fake
   python3 scripts/spikes/limelight_upload_spike.py --only reject --fake-source /path/to/injured/FakeLimelight.java

About one minute; exit 0 only if every check passes. ``--only`` adds a
section's prerequisites. The script is throwaway: nothing in the product may
import it, and units 5 and 7 build their own client and fake from this
evidence.
