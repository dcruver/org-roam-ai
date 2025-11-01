;;; org-roam-api.el --- Complete API for org-roam knowledge management -*- lexical-binding: t; -*-

;; Author: David Cruver <dcruver@users.noreply.github.com>
;; URL: https://github.com/dcruver/org-roam-ai
;; Version: 0.1.0
;; Package-Requires: ((emacs "27.1") (org-roam "2.2"))

;;; Commentary:
;; Complete API for org-roam integration including:
;; - Basic note creation and search
;; - Enhanced contextual search for RAG
;; - Simplified draft management (one draft per room)
;;
;; This package provides the API functions used by org-roam-mcp.

;;; Code:

(require 'org-roam)
(require 'json)

;;; ============================================================================
;;; HELPER FUNCTIONS
;;; ============================================================================

(defun my/api--ensure-org-roam-db ()
  "Ensure org-roam database is available."
  t) ; No automatic sync - handle selectively per function

(defun my/api--json-response (data)
  "Convert DATA to JSON string for API response."
  (json-encode data))

(defun my/api--generate-id ()
  "Generate a unique ID for new notes."
  (format "%s" (time-convert nil 'integer)))

(defun my/api--create-org-file (title id)
  "Create org file path for TITLE with ID."
  (let ((filename (format "%s-%s.org"
                         (downcase (replace-regexp-in-string "[^a-zA-Z0-9]+" "-" title))
                         id)))
    (expand-file-name filename org-roam-directory)))

(defun my/api--node-to-json (node)
  "Convert org-roam NODE to JSON-serializable format."
  (when node
    (let* ((file (org-roam-node-file node))
           (properties (my/api--extract-properties file)))
      `((id . ,(org-roam-node-id node))
        (title . ,(org-roam-node-title node))
        (file . ,file)
        (status . ,(cdr (assoc "status" properties)))
        (priority . ,(cdr (assoc "priority" properties)))
        (created . ,(format-time-string "%Y-%m-%d %H:%M:%S"
                                       (org-roam-node-file-mtime node)))))))

(defun my/api--extract-properties (file)
  "Extract org properties from FILE."
  (when file
    (condition-case nil
        (with-temp-buffer
          (insert-file-contents file)
          (org-mode)
          (goto-char (point-min))
          (let ((props '()))
            (when (re-search-forward "^:PROPERTIES:" nil t)
              (while (and (forward-line 1)
                         (not (looking-at "^:END:"))
                         (not (eobp)))
                (when (looking-at "^:\\([^:]+\\):\\s-*\\(.*\\)\\s-*$")
                  (push (cons (downcase (match-string 1))
                             (match-string 2)) props))))
            props))
      (error '()))))

;;; ============================================================================
;;; BASIC NOTE CREATION AND SEARCH
;;; ============================================================================

(defun my/api-create-note (title content &optional type confidence)
  "Create new org-roam note with properties and generate embeddings if org-roam-semantic is available."
  (interactive)
  (my/api--ensure-org-roam-db)

  (let* ((id (my/api--generate-id))
         (file-path (my/api--create-org-file title id)))

    ;; Create file using buffer approach to trigger hooks
    (with-current-buffer (find-file-noselect file-path)
      (org-mode)
      (erase-buffer)
      (insert (format ":PROPERTIES:\n")
              (format ":ID: %s\n" id)
              (format ":END:\n")
              (format "#+title: %s\n\n" title)
              (format "%s\n" (or content "")))
      (save-buffer)  ; This will trigger the before-save-hook
      (kill-buffer (current-buffer)))

    (org-roam-db-sync)

    ;; Generate embeddings if org-roam-semantic is available
    (condition-case err
        (when (fboundp 'org-roam-semantic-generate-embedding)
          (message "Generating embedding for new note: %s" (file-name-nondirectory file-path))
          (org-roam-semantic-generate-embedding file-path))
      (error
       (message "Warning: Failed to generate embedding for %s: %s"
                (file-name-nondirectory file-path)
                (error-message-string err))))

    (let ((node (org-roam-node-from-id id)))
      (json-encode
        `((success . t)
          (message . "Note created successfully")
          (embedding_generated . ,(and (fboundp 'org-roam-semantic-generate-embedding) t))
          (note . ,(my/api--node-to-json node)))))))

(defun my/api-search-notes (query)
  "Search org-roam notes by QUERY."
  (interactive)
  (my/api--ensure-org-roam-db)

  (let* ((query-words (split-string (downcase query) "\\s-+" t))
         (all-nodes (org-roam-node-list))
         (matching-nodes
          (seq-filter
           (lambda (node)
             (let ((title (downcase (org-roam-node-title node))))
               (seq-some
                (lambda (word)
                  (string-match-p (regexp-quote word) title))
                query-words)))
           all-nodes))
         (node-data (mapcar #'my/api--node-to-json matching-nodes)))

    (my/api--json-response
     `((success . t)
       (query . ,query)
       (total_found . ,(length matching-nodes))
       (notes . ,node-data)))))

;;; ============================================================================
;;; SEMANTIC VECTOR SEARCH (for RAG)
;;; ============================================================================

(defun my/api-semantic-search (query &optional limit cutoff)
  "Semantic vector search using org-roam-semantic.
Returns semantically similar notes with full content for RAG applications."
  (interactive)
  (my/api--ensure-org-roam-db)

  (condition-case err
      (if (fboundp 'org-roam-semantic-get-similar-data)
          (let* ((limit (or limit 10))
                 (cutoff (or cutoff 0.55))
                 (similarities (org-roam-semantic-get-similar-data query limit nil cutoff))
                 (enriched-results
                  (mapcar (lambda (result)
                            (let* ((file (car result))
                                   (similarity (cadr result))
                                   (node (let ((id (caar (org-roam-db-query [:select [id] :from nodes :where (= file $s1)] file))))
                                          (when id (org-roam-node-from-id id))))
                                   (full-content (when file
                                                  (condition-case nil
                                                      (with-temp-buffer
                                                        (insert-file-contents file)
                                                        (buffer-string))
                                                    (error ""))))
                                   (properties (my/api--extract-properties file))
                                   (backlinks (when node (org-roam-backlinks-get node)))
                                   (forward-links (when node
                                                   (org-roam-db-query
                                                    [:select [dest] :from links :where (= source $s1)]
                                                    (org-roam-node-id node)))))

                              `((id . ,(when node (org-roam-node-id node)))
                                (title . ,(when node (org-roam-node-title node)))
                                (file . ,file)
                                (similarity_score . ,similarity)
                                (full_content . ,full-content)
                                (properties . ,properties)
                                (backlinks . ,(mapcar (lambda (backlink)
                                                       `((id . ,(org-roam-node-id (org-roam-backlink-source-node backlink)))
                                                         (title . ,(org-roam-node-title (org-roam-backlink-source-node backlink)))))
                                                     backlinks))
                                (forward_links . ,(mapcar (lambda (link-dest)
                                                           (when-let ((dest-node (org-roam-node-from-id link-dest)))
                                                             `((id . ,link-dest)
                                                               (title . ,(org-roam-node-title dest-node)))))
                                                         forward-links))
                                (created . ,(when node (format-time-string "%Y-%m-%d %H:%M:%S"
                                                                           (org-roam-node-file-mtime node)))))))
                          similarities)))

            (my/api--json-response
             `((success . t)
               (query . ,query)
               (search_type . "semantic_vector")
               (total_found . ,(length similarities))
               (similarity_cutoff . ,cutoff)
               (notes . ,enriched-results)
               (knowledge_context . ,(my/api--generate-semantic-context enriched-results query)))))

        ;; Fallback to keyword search if semantic search not available
        (my/api-contextual-search query limit))

    (error
     (my/api--json-response
      `((success . nil)
        (error . ,(format "Semantic search failed: %s. Ensure org-roam-semantic is loaded and embeddings are generated." (error-message-string err)))
        (fallback_available . t))))))

(defun my/api--generate-semantic-context (enriched-results query)
  "Generate contextual summary for semantic search results."
  (let ((total-notes (length enriched-results))
        (avg-similarity (if enriched-results
                           (/ (apply #'+ (mapcar (lambda (note)
                                                  (cdr (assoc 'similarity_score note)))
                                                enriched-results))
                              (float (length enriched-results)))
                         0))
        (connection-count (apply #'+ (mapcar (lambda (note)
                                             (+ (length (cdr (assoc 'backlinks note)))
                                                (length (cdr (assoc 'forward_links note)))))
                                           enriched-results))))
    `((query_intent . ,query)
      (search_method . "vector_embeddings")
      (total_semantic_matches . ,total-notes)
      (average_similarity . ,avg-similarity)
      (total_connections . ,connection-count)
      (embedding_model . "nomic-embed-text")
      (knowledge_density . ,(if (> total-notes 1)
                               (/ connection-count (* total-notes (1- total-notes)))
                             0)))))

;;; ============================================================================
;;; ENHANCED CONTEXTUAL SEARCH (for RAG)
;;; ============================================================================

(defun my/api-contextual-search (query &optional limit)
  "Enhanced search that returns rich context for LLM consumption."
  (interactive)
  (my/api--ensure-org-roam-db)

  (let* ((limit (or limit 10))
         (query-words (split-string (downcase query) "\\s-+" t))
         (all-nodes (org-roam-node-list))
         (matching-nodes
          (seq-filter
           (lambda (node)
             (let ((title (downcase (org-roam-node-title node)))
                   (content (when-let ((file (org-roam-node-file node)))
                             (condition-case nil
                                 (with-temp-buffer
                                   (insert-file-contents file)
                                   (downcase (buffer-string)))
                               (error "")))))
               (seq-some
                (lambda (word)
                  (or (string-match-p (regexp-quote word) title)
                      (and content (string-match-p (regexp-quote word) content))))
                query-words)))
           all-nodes))
         (limited-results (seq-take matching-nodes limit))
         (enriched-results
          (mapcar (lambda (node)
                    (my/api--enrich-node-context node query-words))
                  limited-results)))

    (my/api--json-response
     `((success . t)
       (query . ,query)
       (context_type . "enhanced_search")
       (total_found . ,(length matching-nodes))
       (returned . ,(length limited-results))
       (notes . ,enriched-results)
       (knowledge_context . ,(my/api--generate-knowledge-context enriched-results query))))))

(defun my/api--enrich-node-context (node query-words)
  "Enrich NODE with full content, connections, and relevance scoring."
  (let* ((file (org-roam-node-file node))
         (full-content (when file
                        (condition-case nil
                            (with-temp-buffer
                              (insert-file-contents file)
                              (buffer-string))
                          (error ""))))
         (backlinks (org-roam-backlinks-get node))
         (forward-links (when file
                         (org-roam-db-query
                          [:select [dest] :from links :where (= source $s1)]
                          (org-roam-node-id node))))
         (properties (my/api--extract-properties file)))

    `((id . ,(org-roam-node-id node))
      (title . ,(org-roam-node-title node))
      (file . ,file)
      (full_content . ,full-content)
      (properties . ,properties)
      (backlinks . ,(mapcar (lambda (backlink)
                             `((id . ,(org-roam-node-id (org-roam-backlink-source-node backlink)))
                               (title . ,(org-roam-node-title (org-roam-backlink-source-node backlink)))))
                           backlinks))
      (forward_links . ,(mapcar (lambda (link-dest)
                                 (when-let ((dest-node (org-roam-node-from-id link-dest)))
                                   `((id . ,link-dest)
                                     (title . ,(org-roam-node-title dest-node)))))
                               forward-links))
      (relevance_score . ,(my/api--calculate-relevance node query-words))
      (created . ,(format-time-string "%Y-%m-%d %H:%M:%S"
                                     (org-roam-node-file-mtime node))))))

(defun my/api--generate-knowledge-context (enriched-results query)
  "Generate contextual summary for LLM about the knowledge graph state."
  (let ((total-notes (length enriched-results))
        (connection-count (apply #'+ (mapcar (lambda (note)
                                              (+ (length (cdr (assoc 'backlinks note)))
                                                 (length (cdr (assoc 'forward_links note)))))
                                            enriched-results))))
    `((query_intent . ,query)
      (total_relevant_notes . ,total-notes)
      (total_connections . ,connection-count)
      (avg_connections_per_note . ,(if (> total-notes 0)
                                      (/ connection-count total-notes)
                                    0))
      (knowledge_density . ,(if (> total-notes 1)
                               (/ connection-count (* total-notes (1- total-notes)))
                             0)))))

(defun my/api--calculate-relevance (node query-words)
  "Calculate relevance score for NODE given QUERY-WORDS."
  (let* ((title (downcase (org-roam-node-title node)))
         (content (when-let ((file (org-roam-node-file node)))
                   (condition-case nil
                       (with-temp-buffer
                         (insert-file-contents file)
                         (downcase (buffer-string)))
                     (error ""))))
         (title-matches (seq-count (lambda (word)
                                    (string-match-p (regexp-quote word) title))
                                  query-words))
         (content-matches (seq-count (lambda (word)
                                      (and content (string-match-p (regexp-quote word) content)))
                                    query-words))
         (total-words (length query-words)))

    (if (> total-words 0)
        (+ (* 0.7 (/ title-matches (float total-words)))
           (* 0.3 (/ content-matches (float total-words))))
      0)))

;;; ============================================================================
;;; SIMPLIFIED DRAFT MANAGEMENT (One Draft Per Room)
;;; ============================================================================

(defun my/api--draft-file-path (room-id)
  "Get draft file path for ROOM-ID."
  (let ((clean-room-id (replace-regexp-in-string "[^a-zA-Z0-9]" "" room-id)))
    (expand-file-name (format "draft-%s.org" clean-room-id) org-roam-directory)))

(defun my/api-get-draft (room-id)
  "Get current draft for ROOM-ID, or nil if none exists."
  (my/api--ensure-org-roam-db)

  (let ((draft-file (my/api--draft-file-path room-id)))
    (if (file-exists-p draft-file)
        (condition-case nil
            (with-temp-buffer
              (insert-file-contents draft-file)
              (let (original-request current-draft created)
                (goto-char (point-min))
                (when (re-search-forward ":ORIGINAL-REQUEST: \\(.+\\)" nil t)
                  (setq original-request (match-string 1)))
                (goto-char (point-min))
                (when (re-search-forward ":CREATED: \\(.+\\)" nil t)
                  (setq created (match-string 1)))

                ;; Content extraction using working method
                (goto-char (point-min))
                (when (search-forward "* Current Draft" nil t)
                  (when (search-forward "#+begin_example" nil t)
                    (forward-line 1)
                    (let ((start (point)))
                      (when (search-forward "#+end_example" nil t)
                        (beginning-of-line)
                        (setq current-draft (buffer-substring-no-properties start (point)))
                        (setq current-draft (string-trim current-draft))))))

                (my/api--json-response
                 `((success . t)
                   (has_draft . t)
                   (room_id . ,room-id)
                   (original_request . ,original-request)
                   (current_draft . ,(or current-draft ""))
                   (created . ,created)
                   (has_content . ,(not (string-empty-p (or current-draft ""))))))))
          (error
           (my/api--json-response
            `((success . nil)
              (error . "Could not read draft file")
              (room_id . ,room-id)))))
      (my/api--json-response
       `((success . t)
         (has_draft . nil)
         (room_id . ,room-id)
         (message . "No draft exists"))))))

(defun my/api-update-draft (room-id content &optional revision-note)
  "Create or update draft for ROOM-ID with CONTENT."
  (my/api--ensure-org-roam-db)

  (let ((draft-file (my/api--draft-file-path room-id))
        (revision-note (or revision-note "Draft updated"))
        (is-new-draft (not (file-exists-p (my/api--draft-file-path room-id)))))

    (if is-new-draft
        ;; Create new draft
        (with-temp-file draft-file
          (insert ":PROPERTIES:\n")
          (insert (format ":ROOM-ID: %s\n" room-id))
          (insert (format ":ORIGINAL-REQUEST: %s\n" revision-note))
          (insert (format ":CREATED: %s\n" (format-time-string "%Y-%m-%dT%H:%M:%SZ")))
          (insert (format ":LAST-UPDATED: %s\n" (format-time-string "%Y-%m-%dT%H:%M:%SZ")))
          (insert ":END:\n")
          (insert "#+title: Draft\n\n")
          (insert "* Current Draft\n#+begin_example\n")
          (insert content "\n")
          (insert "#+end_example\n\n")
          (insert "* Revision History\n")
          (insert (format "** %s - Draft Created\n" (format-time-string "%H:%M:%S")))
          (insert (format "%s\n" revision-note)))

      ;; Update existing draft
      (with-temp-buffer
        (insert-file-contents draft-file)
        (goto-char (point-min))

        ;; Update LAST-UPDATED property
        (when (re-search-forward ":LAST-UPDATED: .*" nil t)
          (replace-match (format ":LAST-UPDATED: %s"
                                (format-time-string "%Y-%m-%dT%H:%M:%SZ"))))

        ;; Update current draft content
        (goto-char (point-min))
        (when (search-forward "* Current Draft" nil t)
          (when (search-forward "#+begin_example" nil t)
            (forward-line 1)
            (let ((start (point)))
              (when (search-forward "#+end_example" nil t)
                (beginning-of-line)
                (delete-region start (point))
                (insert content "\n")))))

        ;; Add revision history entry
        (goto-char (point-max))
        (insert (format "\n** %s - Revision\n" (format-time-string "%H:%M:%S")))
        (insert (format "%s\n" revision-note))

        (write-file draft-file)))

    (my/api--json-response
     `((success . t)
       (action . ,(if is-new-draft "draft_created" "draft_updated"))
       (room_id . ,room-id)
       (is_new . ,is-new-draft)
       (message . ,(if is-new-draft "Draft created successfully" "Draft updated successfully"))))))

(defun my/api-finalize-draft (room-id final-title)
  "Convert draft to permanent note and remove draft file."
  (my/api--ensure-org-roam-db)

  (let ((draft-file (my/api--draft-file-path room-id)))
    (if (file-exists-p draft-file)
        (condition-case nil
            (let (current-draft)
              ;; Extract draft content using working method
              (with-temp-buffer
                (insert-file-contents draft-file)
                (goto-char (point-min))
                (when (search-forward "* Current Draft" nil t)
                  (when (search-forward "#+begin_example" nil t)
                    (forward-line 1)
                    (let ((start (point)))
                      (when (search-forward "#+end_example" nil t)
                        (beginning-of-line)
                        (setq current-draft (buffer-substring-no-properties start (point)))
                        (setq current-draft (string-trim current-draft)))))))

              (if (string-empty-p (or current-draft ""))
                  (my/api--json-response
                   `((success . nil)
                     (error . "No draft content to save")
                     (room_id . ,room-id)))

                ;; Create final note
                (let* ((final-id (my/api--generate-id))
                       (file-path (my/api--create-org-file final-title final-id)))

                  (with-temp-file file-path
                    (insert current-draft))

                  ;; Remove draft file
                  (delete-file draft-file)

                  ;; Sync database so final note is immediately available
                  (org-roam-db-sync)

                  (my/api--json-response
                   `((success . t)
                     (action . "draft_finalized")
                     (room_id . ,room-id)
                     (final_note_id . ,final-id)
                     (final_note_title . ,final-title)
                     (message . ,(format "Draft saved as \"%s\"" final-title)))))))
          (error
           (my/api--json-response
            `((success . nil)
              (error . "Failed to finalize draft")
              (room_id . ,room-id)))))
      (my/api--json-response
       `((success . nil)
         (error . "No draft exists to finalize")
         (room_id . ,room-id))))))

(defun my/api-cancel-draft (room-id)
  "Delete draft file for ROOM-ID."
  (my/api--ensure-org-roam-db)

  (let ((draft-file (my/api--draft-file-path room-id)))
    (if (file-exists-p draft-file)
        (progn
          (delete-file draft-file)
          (my/api--json-response
           `((success . t)
             (action . "draft_cancelled")
             (room_id . ,room-id)
             (message . "Draft cancelled"))))
      (my/api--json-response
       `((success . t)
         (action . "no_draft_to_cancel")
         (room_id . ,room-id)
         (message . "No draft to cancel"))))))

;; Configure org-roam dailies
(setq org-roam-dailies-directory "daily/")
(setq org-roam-dailies-capture-templates
      '(("d" "default" entry
         "* %<%H:%M> %?"
         :target (file+head "%<%Y-%m-%d>.org"
                           "#+title: %<%Y-%m-%d>\n#+filetags: :daily:\n\n"))))

(defun my/add-daily-entry-structured (timestamp title points next-steps tags &optional type priority)
  "Add structured entry to today's daily note. Handles both journal and TODO entries.
TYPE: 'journal' or 'todo'"
  (let* ((today (format-time-string "%Y-%m-%d"))
         (daily-file (expand-file-name (concat today ".org")
                                      (expand-file-name "daily" org-roam-directory)))
         (entry-type (or type "journal"))
         (is-todo (string= entry-type "todo")))

    (unless (file-exists-p (file-name-directory daily-file))
      (make-directory (file-name-directory daily-file) t))

    (unless (file-exists-p daily-file)
      (with-temp-file daily-file
        (insert (format "#+title: %s\n#+filetags: :daily:\n\n" today))))

    (with-current-buffer (find-file-noselect daily-file)
      (goto-char (point-max))
      (unless (bolp) (insert "\n"))

      ;; Insert entry header with appropriate formatting
      (if is-todo
          (if (and tags (> (length tags) 0))
              (insert (format "* TODO %s %s    %s\n"
                             timestamp title
                             (concat ":" (string-join tags ":") ":")))
            (insert (format "* TODO %s %s\n" timestamp title)))
        ;; Regular journal entry
        (if (and tags (> (length tags) 0))
            (insert (format "* %s %s    %s\n"
                           timestamp title
                           (concat ":" (string-join tags ":") ":")))
          (insert (format "* %s %s\n" timestamp title))))

      ;; Insert main points
      (when points
        (dolist (point points)
          (insert (format "- %s\n" point))))

      ;; Insert next steps
      (when next-steps
        (if is-todo
            (progn
              (insert "\n** Subtasks\n")
              (dolist (step next-steps)
                (insert (format "- [ ] %s\n" step))))
          (progn
            (insert "\n** Next Steps\n")
            (dolist (step next-steps)
              (insert (format "- [ ] %s\n" step))))))

      (insert "\n")
      (save-buffer))

    (message "Added %s entry to daily note: %s" entry-type daily-file)))

(defun my/get-daily-note-content (&optional date)
  "Get content of daily note for DATE (defaults to today)."
  (let* ((target-date (or date (format-time-string "%Y-%m-%d")))
         (daily-file (expand-file-name (concat target-date ".org")
                                      (expand-file-name org-roam-dailies-directory org-roam-directory))))
    (if (file-exists-p daily-file)
        (with-temp-buffer
          (insert-file-contents daily-file)
          (buffer-string))
      "")))

(provide 'org-roam-api)
;;; org-roam-api.el ends here
