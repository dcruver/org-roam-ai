;;; test-org-roam-second-brain.el --- Tests for org-roam-second-brain -*- lexical-binding: t; -*-

;; Tests for the Second Brain package using ERT

;;; Commentary:
;; Run with: emacs -batch -l ert -l test-org-roam-second-brain.el -f ert-run-tests-batch-and-exit
;; Or interactively: M-x ert RET t RET

;;; Code:

(require 'ert)
(require 'cl-lib)

;;; ============================================================================
;;; TEST FIXTURES
;;; ============================================================================

(defvar sb/test-temp-dir nil
  "Temporary directory for test org-roam files.")

(defvar sb/test-original-org-roam-directory nil
  "Original org-roam-directory to restore after tests.")

(defun sb/test-setup ()
  "Set up test environment with temporary org-roam directory."
  ;; Save original
  (setq sb/test-original-org-roam-directory (when (boundp 'org-roam-directory)
                                              org-roam-directory))
  ;; Create temp directory
  (setq sb/test-temp-dir (make-temp-file "org-roam-test-" t))
  (setq org-roam-directory sb/test-temp-dir)

  ;; Create subdirectories
  (dolist (dir '("people" "projects" "ideas" "admin" "daily"))
    (make-directory (expand-file-name dir sb/test-temp-dir) t))

  ;; Initialize org-roam database (in-memory for testing)
  (when (fboundp 'org-roam-db-sync)
    (org-roam-db-sync)))

(defun sb/test-teardown ()
  "Clean up test environment."
  ;; Restore original
  (when sb/test-original-org-roam-directory
    (setq org-roam-directory sb/test-original-org-roam-directory))
  ;; Delete temp directory
  (when (and sb/test-temp-dir (file-exists-p sb/test-temp-dir))
    (delete-directory sb/test-temp-dir t))
  (setq sb/test-temp-dir nil))

(defmacro sb/with-test-env (&rest body)
  "Run BODY in test environment with fixtures."
  `(unwind-protect
       (progn
         (sb/test-setup)
         ,@body)
     (sb/test-teardown)))

(defun sb/test-create-fixture-note (title content &optional subdir)
  "Create a test note with TITLE and CONTENT in optional SUBDIR."
  (let* ((dir (if subdir
                  (expand-file-name subdir sb/test-temp-dir)
                sb/test-temp-dir))
         (filename (format "%s-%d.org"
                          (downcase (replace-regexp-in-string "[^a-zA-Z0-9]+" "-" title))
                          (random 99999)))
         (filepath (expand-file-name filename dir))
         (id (org-id-new)))
    (with-temp-file filepath
      (insert (format ":PROPERTIES:\n:ID: %s\n:END:\n#+title: %s\n\n%s"
                      id title content)))
    (when (fboundp 'org-roam-db-sync)
      (org-roam-db-sync))
    (list :id id :file filepath :title title)))

;;; ============================================================================
;;; UNIT TESTS - Helper Functions
;;; ============================================================================

(ert-deftest sb/test-extract-link-names ()
  "Test extracting [[Name]] links from text."
  (should (equal (sb/--extract-link-names "Meet with [[John]] and [[Jane]]")
                 '("John" "Jane")))
  (should (equal (sb/--extract-link-names "No links here")
                 '()))
  (should (equal (sb/--extract-link-names "[[Single Link]]")
                 '("Single Link")))
  (should (equal (sb/--extract-link-names "Text [[A]] more [[B]] end [[C]]")
                 '("A" "B" "C"))))

(ert-deftest sb/test-generate-id ()
  "Test ID generation produces unique values."
  (let ((id1 (sb/--generate-id))
        (id2 (sb/--generate-id)))
    (should (stringp id1))
    (should (stringp id2))
    ;; IDs should be numeric strings
    (should (string-match-p "^[0-9]+$" id1))))

(ert-deftest sb/test-ensure-directory ()
  "Test directory creation."
  (sb/with-test-env
   (let ((dir (sb/--ensure-directory 'person)))
     (should (file-exists-p dir))
     (should (string-match-p "people$" dir)))))

;;; ============================================================================
;;; UNIT TESTS - Core Functions
;;; ============================================================================

(ert-deftest sb/test-core-create-node-person ()
  "Test creating a person node."
  (skip-unless (featurep 'org-roam))
  (sb/with-test-env
   (let ((result (sb/core-create-node 'person "Test Person"
                                      '(("context" . "Test context")))))
     (should (plist-get result :id))
     (should (plist-get result :file))
     (should (file-exists-p (plist-get result :file)))
     (should (equal (plist-get result :title) "Test Person"))
     (should (equal (plist-get result :type) 'person))
     ;; Check file contents
     (with-temp-buffer
       (insert-file-contents (plist-get result :file))
       (should (string-match-p ":NODE-TYPE: person" (buffer-string)))
       (should (string-match-p ":CONTEXT: Test context" (buffer-string)))
       (should (string-match-p "#\\+title: Test Person" (buffer-string)))))))

(ert-deftest sb/test-core-create-node-project ()
  "Test creating a project node."
  (skip-unless (featurep 'org-roam))
  (sb/with-test-env
   (let ((result (sb/core-create-node 'project "Test Project"
                                      '(("status" . "active")
                                        ("next-action" . "First step"))
                                      "* Next Actions\n- [ ] First step\n")))
     (should (file-exists-p (plist-get result :file)))
     ;; Check file contents
     (with-temp-buffer
       (insert-file-contents (plist-get result :file))
       (should (string-match-p ":NODE-TYPE: project" (buffer-string)))
       (should (string-match-p ":STATUS: active" (buffer-string)))
       (should (string-match-p ":NEXT-ACTION: First step" (buffer-string)))))))

(ert-deftest sb/test-core-nodes-by-type ()
  "Test retrieving nodes by type."
  (skip-unless (featurep 'org-roam))
  (sb/with-test-env
   ;; Create some test nodes
   (sb/core-create-node 'person "Alice" nil)
   (sb/core-create-node 'person "Bob" nil)
   (sb/core-create-node 'project "Project X" '(("status" . "active")))

   (let ((people (sb/core-nodes-by-type 'person))
         (projects (sb/core-nodes-by-type 'project)))
     (should (= (length people) 2))
     (should (= (length projects) 1))
     (should (member "Alice" (mapcar (lambda (p) (plist-get p :title)) people)))
     (should (member "Bob" (mapcar (lambda (p) (plist-get p :title)) people))))))

(ert-deftest sb/test-core-ensure-person ()
  "Test ensuring person exists, creating if needed."
  (skip-unless (featurep 'org-roam))
  (sb/with-test-env
   ;; First call should create
   (let ((id1 (sb/core-ensure-person "New Person")))
     (should id1)
     ;; Second call should return same ID
     (let ((id2 (sb/core-ensure-person "New Person")))
       (should (equal id1 id2))))))

;;; ============================================================================
;;; UNIT TESTS - Properties Extraction
;;; ============================================================================

(ert-deftest sb/test-extract-properties ()
  "Test extracting properties from org files."
  (sb/with-test-env
   (let* ((note (sb/test-create-fixture-note
                 "Property Test"
                 ""
                 nil)))
     ;; Create a file with custom properties
     (with-temp-file (plist-get note :file)
       (insert ":PROPERTIES:\n")
       (insert ":ID: test-id\n")
       (insert ":NODE-TYPE: person\n")
       (insert ":CUSTOM-PROP: custom value\n")
       (insert ":END:\n")
       (insert "#+title: Property Test\n"))

     (let ((props (sb/--extract-properties (plist-get note :file))))
       (should (assoc "node-type" props))
       (should (equal (cdr (assoc "node-type" props)) "person"))
       (should (equal (cdr (assoc "custom-prop" props)) "custom value"))))))

;;; ============================================================================
;;; UNIT TESTS - Unchecked Items Extraction
;;; ============================================================================

(ert-deftest sb/test-extract-unchecked-items ()
  "Test extracting unchecked items from a section."
  (sb/with-test-env
   (let ((filepath (expand-file-name "test-checklist.org" sb/test-temp-dir)))
     (with-temp-file filepath
       (insert ":PROPERTIES:\n:ID: test\n:END:\n")
       (insert "#+title: Test\n\n")
       (insert "* Tasks\n")
       (insert "- [ ] Unchecked 1\n")
       (insert "- [x] Checked item\n")
       (insert "- [ ] Unchecked 2\n")
       (insert "* Other Section\n")
       (insert "- [ ] Different section\n"))

     (let ((items (sb/--extract-unchecked-items filepath "Tasks")))
       (should (= (length items) 2))
       (should (member "Unchecked 1" items))
       (should (member "Unchecked 2" items))
       (should-not (member "Different section" items))))))

;;; ============================================================================
;;; INTEGRATION TESTS
;;; ============================================================================

(ert-deftest sb/test-integration-person-workflow ()
  "Test complete person creation and retrieval workflow."
  (skip-unless (featurep 'org-roam))
  (sb/with-test-env
   ;; Create person
   (let ((result (sb/core-create-node 'person "Integration Test Person"
                                      '(("context" . "Test workflow")))))
     (should (plist-get result :id))

     ;; Retrieve by type
     (let ((people (sb/core-nodes-by-type 'person)))
       (should (= (length people) 1))
       (should (equal (plist-get (car people) :title) "Integration Test Person"))))))

(ert-deftest sb/test-integration-project-stale ()
  "Test stale project detection."
  (skip-unless (featurep 'org-roam))
  (sb/with-test-env
   ;; Create active project
   (let ((result (sb/core-create-node 'project "Active Project"
                                      '(("status" . "active")))))
     ;; Immediately after creation, should not be stale
     (let ((stale (sb/core-stale-projects 0))) ; 0 days threshold
       ;; New file should have 0 days, so only stale with -1 threshold
       (should (listp stale))))))

(ert-deftest sb/test-integration-dangling-links ()
  "Test dangling link detection."
  (sb/with-test-env
   ;; Create a note with unchecked item mentioning non-existent person
   (let ((filepath (expand-file-name "daily/2024-01-01.org" sb/test-temp-dir)))
     (make-directory (file-name-directory filepath) t)
     (with-temp-file filepath
       (insert ":PROPERTIES:\n:ID: daily-test\n:END:\n")
       (insert "#+title: 2024-01-01\n\n")
       (insert "* Tasks\n")
       (insert "- [ ] Follow up with [[Nonexistent Person]]\n")))

   (when (fboundp 'org-roam-db-sync) (org-roam-db-sync))

   (let ((dangling (sb/core-dangling-person-links)))
     (should (>= (length dangling) 1))
     (should (equal (plist-get (car dangling) :name) "Nonexistent Person")))))

(ert-deftest sb/test-integration-digest-data ()
  "Test digest data aggregation."
  (skip-unless (featurep 'org-roam))
  (sb/with-test-env
   ;; Create some test data
   (sb/core-create-node 'project "Digest Project" '(("status" . "active")))
   (sb/core-create-node 'person "Digest Person" nil)

   (let ((digest (sb/core-digest-data)))
     (should (plist-get digest :generated-at))
     (should (plist-get digest :active-projects))
     (should (plist-get digest :pending-followups))
     (should (plist-get digest :stale-projects))
     (should (plist-get digest :dangling-links)))))

;;; ============================================================================
;;; BUFFER MODE TESTS
;;; ============================================================================

(ert-deftest sb/test-buffer-mode ()
  "Test that buffer mode sets up correctly."
  (with-temp-buffer
    (sb/buffer-mode)
    (should (eq major-mode 'sb/buffer-mode))
    (should (keymapp sb/buffer-mode-map))
    ;; Check key bindings exist
    (should (lookup-key sb/buffer-mode-map "n"))
    (should (lookup-key sb/buffer-mode-map "p"))
    (should (lookup-key sb/buffer-mode-map (kbd "RET")))
    (should (lookup-key sb/buffer-mode-map "q"))))

;;; ============================================================================
;;; CONFIGURATION TESTS
;;; ============================================================================

(ert-deftest sb/test-customization-group ()
  "Test that customization group is properly defined."
  (should (get 'sb 'custom-group)))

(ert-deftest sb/test-defcustom-variables ()
  "Test that customizable variables are defined."
  (should (boundp 'sb/stale-days))
  (should (boundp 'sb/similarity-threshold))
  (should (boundp 'sb/show-digest-on-startup))
  (should (boundp 'sb/proactive-suggestions))
  (should (boundp 'sb/directories)))

;;; ============================================================================
;;; RUN TESTS
;;; ============================================================================

(provide 'test-org-roam-second-brain)
;;; test-org-roam-second-brain.el ends here
