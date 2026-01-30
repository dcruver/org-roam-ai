;;; $DOOMDIR/config.el -*- lexical-binding: t; -*-

;; Place your private configuration here! Remember, you do not need to run 'doom
;; sync' after modifying this file!


;; Some functionality uses this to identify you, e.g. GPG configuration, email
;; clients, file templates and snippets. It is optional.
(setq user-full-name "Donald Cruver"
      user-mail-address "donald@cruver.network")

(setq doom-theme 'doom-gruvbox)
;; This determines the style of line numbers in effect. If set to `nil', line
;; numbers are disabled. For relative line numbers, set this to `relative'.
(setq display-line-numbers-type nil)

;; change `org-directory'. It must be set before org loads!
(setq org-directory "~/org")
;;
;; Whenever you reconfigure a package, make sure to wrap your config in an
;; `after!' block, otherwise Doom's defaults may override your settings. E.g.
;;
;;   (after! PACKAGE
;;     (setq x y))
;;
;; The exceptions to this rule:
;;
;;   - Setting file/directory variables (like `org-directory')
;;   - Setting variables which explicitly tell you to set them before their
;;     package is loaded (see 'C-h v VARIABLE' to look up their documentation).
;;   - Setting doom variables (which start with 'doom-' or '+').
;;
;; Here are some additional functions/macros that will help you configure Doom.
;;
;; - `load!' for loading external *.el files relative to this one
;; - `use-package!' for configuring packages
;; - `after!' for running code after a package has loaded
;; - `add-load-path!' for adding directories to the `load-path', relative to
;;   this file. Emacs searches the `load-path' when you load packages with
;;   `require' or `use-package'.
;; - `map!' for binding new keys
;;
;; To get information about any of these functions/macros, move the cursor over
;; the highlighted symbol at press 'K' (non-evil users must press 'C-c c k').
;; This will open documentation for it, including demos of how they are used.
;; Alternatively, use `C-h o' to look up a symbol (functions, variables, faces,
;; etc).
;;
;; You can also try 'gd' (or 'C-c c d') to jump to their definition and see how
;; they are implemented.

(setq doom-font (font-spec :family "JetBrainsMono Nerd Font" :size 22))


;;; configure org-publish
(use-package! ox-gfm
  :after ox
  :config
  (add-to-list 'org-export-backends 'gfm))

(defun my/org-export-to-md-with-images ()
  "Export Org file to Markdown using #+EXPORT_FILE_NAME and #+EXPORT_PROJECT_DIR, copying linked images and rewriting image paths."
  (interactive)
  (let* ((org-file (buffer-file-name))
         (keywords (org-collect-keywords '("EXPORT_FILE_NAME" "EXPORT_PROJECT_DIR")))
         (file-name (car (cdr (assoc "EXPORT_FILE_NAME" keywords))))
         (project-dir (car (cdr (assoc "EXPORT_PROJECT_DIR" keywords)))))
    (unless file-name
      (user-error "Missing #+EXPORT_FILE_NAME"))
    (unless project-dir
      (user-error "Missing #+EXPORT_PROJECT_DIR"))

    (let* ((export-dir (expand-file-name project-dir))
           (export-path (expand-file-name file-name export-dir))
           (image-dir (expand-file-name "images/" export-dir))
           (links (org-element-map (org-element-parse-buffer) 'link
                    (lambda (link)
                      (when (string= (org-element-property :type link) "file")
                        (let ((path (org-element-property :path link)))
                          (when (string-match-p (image-file-name-regexp) path)
                            path))))))
           (image-map (mapcar (lambda (img)
                                (cons img (concat "images/" (file-name-nondirectory img))))
                              links)))

      ;; Ensure export and image directories exist
      (make-directory (file-name-directory export-path) :parents)
      (make-directory image-dir :parents)

      ;; Export to Markdown using ox-gfm to the full path
      (let ((org-export-show-temporary-export-buffer nil))
        (org-export-to-file 'gfm export-path))

      ;; Copy linked images to image-dir
      (dolist (img links)
        (let* ((src (expand-file-name img (file-name-directory org-file)))
               (dest (expand-file-name (file-name-nondirectory img) image-dir)))
          (when (and (file-exists-p src)
                     (not (file-equal-p src dest)))
            (copy-file src dest t))))

      ;; Rewrite image paths in exported Markdown
      (with-temp-buffer
        (insert-file-contents export-path)
        (goto-char (point-min))
        (dolist (pair image-map)
          (let ((orig (regexp-quote (car pair)))
                (new (cdr pair)))
            (while (re-search-forward (concat "!\\[[^]]*\\](\\.*/*" orig ")") nil t)
              (replace-match (format "![image](%s)" new) t))))
        (write-region (point-min) (point-max) export-path))

      (message "âœ… Exported to %s and copied %d image(s) to %s"
               export-path (length links) image-dir))))

;;; start the server
(setq server-use-tcp 't)
(setq server-auth-dir "~/emacs-server/")
(server-start)

;;; setup org-roam and load custom packages
(setq org-roam-directory (file-truename "~/org-roam"))
(load! "org-roam-api")

(add-to-list 'load-path "~/Projects/org-roam-ai/packages/org-roam-ai")
(require 'org-roam-vector-search)
(require 'org-roam-ai-assistant)
(require 'org-roam-api)

(require 'org-roam-vector-search)
(require 'org-roam-ai-assistant)

;;; configure org-roam-vector-search
(global-set-key (kbd "C-c v s") 'org-roam-semantic-search)
(global-set-key (kbd "C-c v i") 'org-roam-semantic-insert-similar)
(global-set-key (kbd "C-c a f") 'org-roam-ai-enhance-with-context)

;;; Configure org-roam dailies
(setq org-roam-dailies-directory "daily/")
(load! "org-roam-daily-vectors")
(setq org-roam-dailies-capture-templates
      '(("d" "default" entry
         "* %<%H:%M> %?"
         :target (file+head "%<%Y-%m-%d>.org"
                           "#+title: %<%Y-%m-%d>\n#+filetags: :daily:\n\n"))))

;;; Configure org-roam TODOs
;; Dynamically include org-roam files with TODOs in agenda
(setq org-agenda-files
      (append
       (list (expand-file-name "daily" org-roam-directory))
       (directory-files-recursively org-roam-directory "\\.org$")))

;;; set some performance optimization values
(setq read-process-output-max (* 2 1024 1024)
      gc-cons-threshold (* 128 1024 1024))

(after! lsp-mode
  (setq lsp-idle-delay 0.3
        lsp-use-plists t
        lsp-log-io nil))

(load! "project-ide")
